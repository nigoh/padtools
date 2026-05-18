package padtools.core.formats.uml;

import padtools.core.formats.java.JavaLexer;
import padtools.core.formats.java.JavaToken;
import padtools.util.ErrorListener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Java ソースから {@link JavaClassInfo} のリストを抽出するパーサ。
 *
 * <p>{@link JavaLexer} で得たトークン列を走査し、クラス/インタフェース/enum/@interface/
 * その内部のフィールド・メソッド・コンストラクタを構造化データとして返す。
 * メソッド本体内の呼び出しも {@link JavaMethodInfo#getCalls()} に記録するため、
 * シーケンス図生成に利用できる。</p>
 */
public final class JavaStructureExtractor {

    private static final Set<String> MODIFIERS = new HashSet<>(Arrays.asList(
            "public", "private", "protected", "static", "final", "abstract",
            "synchronized", "native", "strictfp", "default", "transient",
            "volatile"));

    /** Java ソースから ClassInfo のリストを返す。 */
    public static List<JavaClassInfo> extract(String source) {
        return extract(source, null);
    }

    /** エラーリスナー付き。 */
    public static List<JavaClassInfo> extract(String source, ErrorListener listener) {
        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }
        List<JavaToken> tokens = new JavaLexer(source).tokenize();
        List<JavaCommentScanner.Comment> comments = JavaCommentScanner.scan(source);
        Extractor e = new Extractor(tokens, source, comments,
                listener != null ? listener : ErrorListener.silent());
        e.parseFile();
        return Collections.unmodifiableList(e.results);
    }

    private JavaStructureExtractor() {
    }

    /** 状態を持つ実装本体。 */
    private static final class Extractor {
        private final List<JavaToken> tokens;
        private final String src;
        private final List<JavaCommentScanner.Comment> comments;
        private final ErrorListener listener;
        private final List<JavaClassInfo> results = new ArrayList<>();
        private final List<JavaClassInfo> classStack = new ArrayList<>();
        private String packageName = "";
        private int idx;

        Extractor(List<JavaToken> tokens, String src,
                  List<JavaCommentScanner.Comment> comments,
                  ErrorListener listener) {
            this.tokens = tokens;
            this.src = src;
            this.comments = comments;
            this.listener = listener;
        }

        private String findCommentBefore(int pos) {
            return JavaCommentScanner.findCommentBefore(src, comments, pos);
        }

        private void warn(int line, String msg) {
            listener.onError(null, line, msg);
        }

        private JavaToken peek() {
            return tokens.get(idx);
        }

        private JavaToken peek(int n) {
            int i = idx + n;
            if (i >= tokens.size()) {
                i = tokens.size() - 1;
            }
            return tokens.get(i);
        }

        private JavaToken next() {
            JavaToken t = tokens.get(idx);
            if (t.type != JavaToken.Type.EOF) {
                idx++;
            }
            return t;
        }

        private boolean atEnd() {
            return peek().type == JavaToken.Type.EOF;
        }

        // --- ファイルレベル ---

        void parseFile() {
            while (!atEnd()) {
                if (peek().isKw("package")) {
                    next();
                    packageName = readDottedName();
                    consume(";");
                    continue;
                }
                if (peek().isKw("import")) {
                    skipUntilSemicolon();
                    continue;
                }
                int declStart = atEnd() ? -1 : peek().start;
                List<String> ann = readAnnotations();
                List<String> mods = readModifiers();
                if (atEnd()) {
                    break;
                }
                String comment = findCommentBefore(declStart);
                if (peek().is("@") && peek(1).isKw("interface")) {
                    next(); // @
                    parseClassDecl(JavaClassInfo.Kind.ANNOTATION, mods, ann, comment);
                    continue;
                }
                if (peek().isKw("class")) {
                    parseClassDecl(JavaClassInfo.Kind.CLASS, mods, ann, comment);
                    continue;
                }
                if (peek().isKw("interface")) {
                    parseClassDecl(JavaClassInfo.Kind.INTERFACE, mods, ann, comment);
                    continue;
                }
                if (peek().isKw("enum")) {
                    parseClassDecl(JavaClassInfo.Kind.ENUM, mods, ann, comment);
                    continue;
                }
                next();
            }
        }

        private void parseClassDecl(JavaClassInfo.Kind kind, List<String> mods,
                                     List<String> annotations, String comment) {
            next(); // class/interface/enum
            String name = "Anonymous";
            if (peek().type == JavaToken.Type.IDENT) {
                name = next().text;
            }
            JavaClassInfo info = new JavaClassInfo();
            info.setKind(kind);
            info.setSimpleName(name);
            info.setPackageName(packageName);
            info.getModifiers().addAll(mods);
            info.getAnnotations().addAll(annotations);
            info.setComment(comment);
            if (!classStack.isEmpty()) {
                String outerName = buildEnclosingPath();
                info.setEnclosingClass(outerName);
            }

            // ジェネリック型パラメータ <T extends ...>
            if (peek().is("<")) {
                skipBalanced("<", ">");
            }
            // extends ... / implements ...
            while (!atEnd() && !peek().is("{") && !peek().is(";")) {
                if (peek().isKw("extends")) {
                    next();
                    if (kind == JavaClassInfo.Kind.INTERFACE) {
                        // interface extends は複数の親
                        info.getInterfaces().addAll(readTypeList());
                    } else {
                        info.setSuperClass(readTypeName());
                    }
                } else if (peek().isKw("implements") || peek().isKw("permits")) {
                    next();
                    info.getInterfaces().addAll(readTypeList());
                } else {
                    next();
                }
            }
            if (peek().is(";")) {
                next();
                results.add(info);
                return;
            }
            if (!peek().is("{")) {
                results.add(info);
                return;
            }
            next(); // {
            classStack.add(info);
            try {
                if (kind == JavaClassInfo.Kind.ENUM) {
                    parseEnumConstants(info);
                }
                parseClassBody(info);
            } finally {
                classStack.remove(classStack.size() - 1);
            }
            results.add(info);
        }

        /**
         * enum 定数列を ; or } まで読み取り、定数名を {@link JavaClassInfo#getEnumConstants()}
         * に追加する。{@code A(args)} の引数や {@code A { body }} の無名サブクラスは内容を
         * 解析せずスキップする。
         */
        private void parseEnumConstants(JavaClassInfo cls) {
            while (!atEnd()) {
                if (peek().is("}")) {
                    return;
                }
                if (peek().is(";")) {
                    next();
                    return;
                }
                if (peek().is(",")) {
                    next();
                    continue;
                }
                readAnnotations();
                if (atEnd()) {
                    return;
                }
                if (peek().type != JavaToken.Type.IDENT) {
                    next();
                    continue;
                }
                String constName = next().text;
                cls.getEnumConstants().add(constName);
                if (peek().is("(")) {
                    skipBalanced("(", ")");
                }
                if (peek().is("{")) {
                    skipBalanced("{", "}");
                }
            }
        }

        private String buildEnclosingPath() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < classStack.size(); i++) {
                if (i > 0) {
                    sb.append('.');
                }
                sb.append(classStack.get(i).getSimpleName());
            }
            return sb.toString();
        }

        private void parseClassBody(JavaClassInfo cls) {
            while (!atEnd()) {
                if (peek().is("}")) {
                    next();
                    return;
                }
                if (peek().is(";")) {
                    next();
                    continue;
                }
                int declStart = peek().start;
                List<String> annotations = readAnnotations();
                List<String> mods = readModifiers();
                if (atEnd() || peek().is("}")) {
                    continue;
                }
                String comment = findCommentBefore(declStart);

                if (peek().isKw("class")) {
                    parseClassDecl(JavaClassInfo.Kind.CLASS, mods, annotations, comment);
                    continue;
                }
                if (peek().isKw("interface")) {
                    parseClassDecl(JavaClassInfo.Kind.INTERFACE, mods, annotations, comment);
                    continue;
                }
                if (peek().isKw("enum")) {
                    parseClassDecl(JavaClassInfo.Kind.ENUM, mods, annotations, comment);
                    continue;
                }
                if (peek().is("@") && peek(1).isKw("interface")) {
                    next();
                    parseClassDecl(JavaClassInfo.Kind.ANNOTATION, mods, annotations, comment);
                    continue;
                }
                if (peek().is("{")) {
                    skipBalanced("{", "}");
                    continue;
                }
                if (!parseMember(cls, mods, annotations, comment)) {
                    next();
                }
            }
        }

        private boolean parseMember(JavaClassInfo cls, List<String> mods,
                                     List<String> annotations, String comment) {
            int save = idx;
            if (peek().is("<")) {
                skipBalanced("<", ">");
            }
            // 型→名前→( なら method、型→名前→;|= なら field
            int depth = 0;
            while (!atEnd()) {
                JavaToken t = peek();
                if (depth == 0) {
                    if (t.is(";")) {
                        idx = save;
                        parseFieldDecl(cls, mods, annotations, comment);
                        return true;
                    }
                    if (t.is("=")) {
                        idx = save;
                        parseFieldDecl(cls, mods, annotations, comment);
                        return true;
                    }
                    if (t.is("(")) {
                        idx = save;
                        parseMethodDecl(cls, mods, annotations, comment);
                        return true;
                    }
                    if (t.is("{") || t.is("}")) {
                        idx = save;
                        return false;
                    }
                }
                if (t.is("(") || t.is("[") || t.is("<")) {
                    depth++;
                } else if (t.is(")") || t.is("]")) {
                    if (depth > 0) {
                        depth--;
                    }
                } else if (t.is(">") || t.is(">>") || t.is(">>>")) {
                    depth = Math.max(0, depth - t.text.length());
                }
                next();
            }
            idx = save;
            return false;
        }

        private void parseFieldDecl(JavaClassInfo cls, List<String> mods,
                                     List<String> annotations, String comment) {
            int startPos = peek().start;
            int lastIdentEnd = startPos;
            String fieldName = "";
            // 型 + 名前を読み込み (; or = に到達するまで)
            int depth = 0;
            while (!atEnd()) {
                JavaToken t = peek();
                if (depth == 0 && (t.is(";") || t.is("="))) {
                    break;
                }
                if (t.is("(") || t.is("[") || t.is("<")) {
                    depth++;
                } else if (t.is(")") || t.is("]")) {
                    if (depth > 0) {
                        depth--;
                    }
                } else if (t.is(">") || t.is(">>") || t.is(">>>")) {
                    depth = Math.max(0, depth - t.text.length());
                }
                if (depth == 0 && t.type == JavaToken.Type.IDENT) {
                    fieldName = t.text;
                    lastIdentEnd = t.start;
                }
                next();
            }
            String type = src.substring(startPos, lastIdentEnd).trim();
            // 末尾のカンマ区切り変数 (int a = 1, b = 2) は最初の宣言のみ取得して残りはスキップ
            JavaFieldInfo f = new JavaFieldInfo();
            f.setName(fieldName);
            f.setType(normalizeType(stripAnnotations(type)));
            f.setVisibility(Visibility.fromModifiers(mods));
            f.setStatic(mods.contains("static"));
            f.setFinal(mods.contains("final"));
            f.getAnnotations().addAll(annotations);
            f.setComment(comment);
            cls.getFields().add(f);
            // ; までスキップ
            skipUntilSemicolonRespectingBlocks();
        }

        private void parseMethodDecl(JavaClassInfo cls, List<String> mods,
                                      List<String> annotations, String comment) {
            if (peek().is("<")) {
                skipBalanced("<", ">");
            }
            int startPos = peek().start;
            int lastIdentEnd = startPos;
            String methodName = "";
            while (!atEnd() && !peek().is("(")) {
                JavaToken t = peek();
                if (t.type == JavaToken.Type.IDENT) {
                    methodName = t.text;
                    lastIdentEnd = t.start;
                }
                next();
            }
            if (atEnd()) {
                return;
            }
            String returnType = stripAnnotations(src.substring(startPos, lastIdentEnd).trim());
            boolean isConstructor = returnType.isEmpty()
                    || returnType.equals(methodName)
                    || cls.getSimpleName().equals(methodName);

            JavaMethodInfo m = new JavaMethodInfo();
            m.setName(methodName);
            m.setReturnType(isConstructor ? "" : normalizeType(returnType));
            m.setVisibility(Visibility.fromModifiers(mods));
            m.setStatic(mods.contains("static"));
            m.setAbstract(mods.contains("abstract")
                    || cls.getKind() == JavaClassInfo.Kind.INTERFACE
                    || cls.getKind() == JavaClassInfo.Kind.AIDL_INTERFACE);
            m.setConstructor(isConstructor);
            m.getAnnotations().addAll(annotations);
            m.setComment(comment);
            // パラメータ
            parseParameters(m);
            // throws ...
            while (!atEnd() && !peek().is("{") && !peek().is(";")) {
                next();
            }
            if (peek().is(";")) {
                next();
                cls.getMethods().add(m);
                return;
            }
            if (!peek().is("{")) {
                cls.getMethods().add(m);
                return;
            }
            next();
            extractCallsInBody(m);
            cls.getMethods().add(m);
        }

        private void parseParameters(JavaMethodInfo m) {
            if (!peek().is("(")) {
                return;
            }
            next();
            int parenDepth = 1;
            int angleDepth = 0;
            int paramStart = peek().start;
            int paramEnd = paramStart;
            List<int[]> ranges = new ArrayList<>();
            while (!atEnd() && parenDepth > 0) {
                JavaToken t = peek();
                if (t.is("(") || t.is("[")) {
                    parenDepth++;
                } else if (t.is(")") || t.is("]")) {
                    parenDepth--;
                    if (parenDepth == 0) {
                        if (paramEnd > paramStart) {
                            ranges.add(new int[]{paramStart, paramEnd});
                        }
                        next();
                        break;
                    }
                } else if (t.is("<")) {
                    angleDepth++;
                } else if (t.is(">")) {
                    angleDepth = Math.max(0, angleDepth - 1);
                } else if (t.is(">>") || t.is(">>>")) {
                    angleDepth = Math.max(0, angleDepth - t.text.length());
                } else if (t.is(",") && parenDepth == 1 && angleDepth == 0) {
                    ranges.add(new int[]{paramStart, paramEnd});
                    next();
                    paramStart = peek().start;
                    paramEnd = paramStart;
                    continue;
                }
                paramEnd = t.end;
                next();
            }
            for (int[] r : ranges) {
                String raw = src.substring(r[0], r[1]).trim();
                // アノテーション・修飾子を取り除き、最後の識別子をパラメータ名、それ以前を型とする
                String[] sp = stripAnnotations(raw).trim().split("\\s+");
                if (sp.length == 0 || sp[0].isEmpty()) {
                    continue;
                }
                String paramName;
                String type;
                if (sp.length == 1) {
                    type = sp[0];
                    paramName = "";
                } else {
                    paramName = sp[sp.length - 1];
                    StringBuilder tb = new StringBuilder();
                    for (int i = 0; i < sp.length - 1; i++) {
                        if (tb.length() > 0) {
                            tb.append(' ');
                        }
                        tb.append(sp[i]);
                    }
                    type = tb.toString();
                }
                // final 等の修飾子を型から除外
                type = type.replaceAll("(^|\\s)(final|in|out|inout)(\\s|$)", " ").trim();
                m.getParameterTypes().add(normalizeType(type));
                m.getParameterNames().add(paramName);
            }
        }

        private void extractCallsInBody(JavaMethodInfo m) {
            int depth = 1;
            while (!atEnd() && depth > 0) {
                JavaToken t = peek();
                if (t.is("{")) {
                    depth++;
                    next();
                    continue;
                }
                if (t.is("}")) {
                    depth--;
                    next();
                    continue;
                }
                // 識別子 ( パターン: 直前が IDENT で次が "("
                if (t.type == JavaToken.Type.IDENT && peek(1).is("(")) {
                    // 制御構文キーワードは呼び出しとしない
                    String name = t.text;
                    boolean afterNew = idx > 0
                            && tokens.get(idx - 1).isKw("new");
                    if (!isControlKeyword(name) && !afterNew) {
                        String receiver = findReceiver();
                        m.getCalls().add(new JavaMethodInfo.Call(receiver, name));
                    }
                }
                next();
            }
        }

        /** 直前のトークンを見て {@code receiver.method(...)} の receiver を組み立てる。 */
        private String findReceiver() {
            // 直前が "." なら、ドット前の識別子(連鎖)を集める
            // 例: a.b.c.method(...) では receiver = "a.b.c"
            int i = idx - 1;
            if (i < 0 || !tokens.get(i).is(".")) {
                return "";
            }
            // ドット連鎖を逆方向に集める
            StringBuilder sb = new StringBuilder();
            int j = i;
            while (j >= 0) {
                JavaToken t = tokens.get(j);
                if (t.is(".")) {
                    j--;
                    continue;
                }
                if (t.type == JavaToken.Type.IDENT) {
                    sb.insert(0, t.text);
                    if (j - 1 >= 0 && tokens.get(j - 1).is(".")) {
                        sb.insert(0, '.');
                        j -= 2;
                    } else {
                        break;
                    }
                } else {
                    break;
                }
            }
            return sb.toString();
        }

        private boolean isControlKeyword(String s) {
            return "if".equals(s) || "while".equals(s) || "for".equals(s)
                    || "switch".equals(s) || "synchronized".equals(s)
                    || "catch".equals(s) || "return".equals(s)
                    || "throw".equals(s) || "new".equals(s)
                    || "do".equals(s) || "else".equals(s) || "try".equals(s)
                    || "finally".equals(s);
        }

        private static String stripAnnotations(String s) {
            // 単純に @Foo(...) や @Foo を取り除く
            return s.replaceAll("@\\w+(\\.\\w+)*(\\([^)]*\\))?", " ").trim();
        }

        private static String normalizeType(String s) {
            return s.replaceAll("\\s+", " ").trim();
        }

        // --- ヘルパ ---

        private List<String> readAnnotations() {
            List<String> result = new ArrayList<>();
            while (!atEnd() && peek().is("@")) {
                if (peek(1).isKw("interface")) {
                    return result;
                }
                next();
                StringBuilder name = new StringBuilder();
                while (peek().type == JavaToken.Type.IDENT) {
                    name.append(next().text);
                    if (peek().is(".")) {
                        next();
                        name.append('.');
                    } else {
                        break;
                    }
                }
                String args = "";
                if (peek().is("(")) {
                    int s = peek().start;
                    skipBalanced("(", ")");
                    int e = idx > 0 ? tokens.get(idx - 1).end : s;
                    args = src.substring(s, e);
                }
                result.add(name.toString() + args);
            }
            return result;
        }

        private List<String> readModifiers() {
            List<String> result = new ArrayList<>();
            while (!atEnd()
                    && peek().type == JavaToken.Type.IDENT
                    && MODIFIERS.contains(peek().text)) {
                result.add(next().text);
            }
            return result;
        }

        private String readDottedName() {
            StringBuilder sb = new StringBuilder();
            while (!atEnd() && peek().type == JavaToken.Type.IDENT) {
                sb.append(next().text);
                if (peek().is(".")) {
                    next();
                    sb.append('.');
                } else {
                    break;
                }
            }
            return sb.toString();
        }

        private String readTypeName() {
            int s = peek().start;
            int e = s;
            int depth = 0;
            while (!atEnd()) {
                JavaToken t = peek();
                if (depth == 0
                        && (t.is(",") || t.is("{") || t.is(";")
                            || t.isKw("implements") || t.isKw("extends")
                            || t.isKw("permits"))) {
                    break;
                }
                if (t.is("<")) {
                    depth++;
                } else if (t.is(">")) {
                    depth--;
                } else if (t.is(">>") || t.is(">>>")) {
                    depth -= t.text.length();
                }
                e = t.end;
                next();
            }
            return normalizeType(stripAnnotations(src.substring(s, e)));
        }

        private List<String> readTypeList() {
            List<String> result = new ArrayList<>();
            while (!atEnd()) {
                if (peek().is("{") || peek().is(";")) {
                    break;
                }
                String n = readTypeName();
                if (!n.isEmpty()) {
                    result.add(n);
                }
                if (peek().is(",")) {
                    next();
                } else {
                    break;
                }
            }
            return result;
        }

        private void consume(String s) {
            if (peek().is(s)) {
                next();
            }
        }

        private void skipUntilSemicolon() {
            while (!atEnd() && !peek().is(";")) {
                next();
            }
            if (peek().is(";")) {
                next();
            }
        }

        private void skipUntilSemicolonRespectingBlocks() {
            int d = 0;
            while (!atEnd()) {
                JavaToken t = peek();
                if (d == 0 && t.is(";")) {
                    next();
                    return;
                }
                if (t.is("(") || t.is("[") || t.is("{")) {
                    d++;
                } else if (t.is(")") || t.is("]") || t.is("}")) {
                    if (d > 0) {
                        d--;
                    } else {
                        return;
                    }
                }
                next();
            }
        }

        private void skipBalanced(String open, String close) {
            if (!peek().is(open)) {
                return;
            }
            int startLine = peek().line;
            next();
            int depth = 1;
            boolean angle = "<".equals(open);
            while (!atEnd() && depth > 0) {
                JavaToken t = peek();
                if (t.is(open)) {
                    depth++;
                } else if (t.is(close)) {
                    depth--;
                    if (depth == 0) {
                        next();
                        return;
                    }
                } else if (angle && (t.is(">>") || t.is(">>>"))) {
                    depth -= t.text.length();
                    if (depth <= 0) {
                        next();
                        return;
                    }
                }
                next();
            }
            warn(startLine, "EOF reached before matching '" + close + "'");
        }
    }
}
