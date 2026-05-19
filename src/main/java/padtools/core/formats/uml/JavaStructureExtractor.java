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

    /**
     * ヘッダ情報のみ (package / simpleName / kind / modifiers / superClass / interfaces /
     * enclosingClass / アノテーション名) を抽出する軽量モード。
     *
     * <p>fields / methods / comments / enumConstants は破棄され、各 ClassInfo の
     * {@link JavaClassInfo#isDetailed()} は false。大規模プロジェクトでヒープを抑えつつ
     * 一覧表示・ツリー構築・図のフィルタリングだけを行いたい場合に使う。詳細が必要に
     * なったら {@link ClassIndex#detail} で対象クラスだけ Stage B 化する。</p>
     */
    public static List<JavaClassInfo> extractHeadersOnly(String source, ErrorListener listener) {
        List<JavaClassInfo> full = extract(source, listener);
        List<JavaClassInfo> headers = new ArrayList<>(full.size());
        for (JavaClassInfo c : full) {
            JavaClassInfo h = new JavaClassInfo();
            h.setPackageName(c.getPackageName());
            h.setSimpleName(c.getSimpleName());
            h.setKind(c.getKind());
            h.getModifiers().addAll(c.getModifiers());
            h.getAnnotations().addAll(c.getAnnotations());
            h.setSuperClass(c.getSuperClass());
            h.getInterfaces().addAll(c.getInterfaces());
            h.setEnclosingClass(c.getEnclosingClass());
            h.setAaosCategory(c.getAaosCategory());
            h.setAndroidComponentType(c.getAndroidComponentType());
            // fields / methods / enumConstants / comment は破棄
            h.setDetailed(false);
            headers.add(h);
        }
        return Collections.unmodifiableList(headers);
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
                if (peek().isKw("record")) {
                    parseClassDecl(JavaClassInfo.Kind.RECORD, mods, ann, comment);
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
            // record のコンパクトコンストラクタ引数: record Foo(int x, int y)
            if (kind == JavaClassInfo.Kind.RECORD && peek().is("(")) {
                skipBalanced("(", ")");
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

        /** enum 定数を ; or } まで読み取り、引数/無名サブクラス body は名前のみ拾って中身はスキップ。 */
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
                if (peek().isKw("record")) {
                    parseClassDecl(JavaClassInfo.Kind.RECORD, mods, annotations, comment);
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
            // 初期化子が匿名クラス/ラムダなら本体を吸い上げて f.inlineMethods に格納する。
            // 失敗時は idx を元に戻し、後段の skipUntilSemicolonRespectingBlocks() に委ねる。
            if (!atEnd() && peek().is("=")) {
                next(); // '=' を消費
                tryParseInlineInitializer(f);
            }
            // ; までスキップ
            skipUntilSemicolonRespectingBlocks();
        }

        /**
         * フィールド初期化子が {@code new SomeType(...) {...}} (匿名クラス) または
         * {@code args -> body} (ラムダ) の場合、その本体内に出現するメソッドを
         * {@link JavaFieldInfo#getInlineMethods()} に格納する。
         *
         * <p>{@code =} は呼び出し側で既に消費済み。本メソッドは消費したトークンを
         * 巻き戻して呼び出し側の {@link #skipUntilSemicolonRespectingBlocks()} に
         * 安全に引き継ぐ責務を持つ (異常検出時は何もしない)。</p>
         */
        private void tryParseInlineInitializer(JavaFieldInfo f) {
            int save = idx;
            // 匿名クラス判定: new TypeName (args) { ... }
            if (peek().isKw("new")) {
                int probe = idx + 1;
                // 型名 (a.b.C<T>) をスキップ
                while (probe < tokens.size()) {
                    JavaToken t = tokens.get(probe);
                    if (t.type == JavaToken.Type.IDENT || t.is(".")) {
                        probe++;
                        continue;
                    }
                    break;
                }
                // ジェネリック型引数
                if (probe < tokens.size() && tokens.get(probe).is("<")) {
                    probe = skipBalancedAt(probe, "<", ">");
                }
                // 配列の場合 (new int[]{...}) は無視
                if (probe < tokens.size() && tokens.get(probe).is("[")) {
                    return;
                }
                // 引数 (...)
                if (probe < tokens.size() && tokens.get(probe).is("(")) {
                    probe = skipBalancedAt(probe, "(", ")");
                } else {
                    return;
                }
                // 直後が { なら匿名クラス本体
                if (probe < tokens.size() && tokens.get(probe).is("{")) {
                    idx = probe;
                    next(); // '{' を消費
                    JavaClassInfo dummy = new JavaClassInfo();
                    dummy.setSimpleName("$anon");
                    // classStack には push しない (inner-class 判定の副作用回避)
                    parseClassBody(dummy);
                    f.getInlineMethods().addAll(dummy.getMethods());
                    return;
                }
                idx = save;
                return;
            }
            // ラムダ判定: x -> ... または (a, b) -> ...
            if (looksLikeLambdaStart()) {
                consumeLambdaParams();
                if (atEnd() || !peek().is("->")) {
                    idx = save;
                    return;
                }
                next(); // '->'
                String samName = resolveSamMethodName(f.getType());
                JavaMethodInfo m = new JavaMethodInfo();
                m.setName(samName);
                m.setVisibility(Visibility.PUBLIC);
                if (!atEnd() && peek().is("{")) {
                    next();
                    parseStatementBlock(m.getStatements());
                } else {
                    // expression-bodied lambda: フィールド終端 ';' は消費せず呼び出し元に残す
                    parseLambdaExpressionBody(m.getStatements());
                }
                f.getInlineMethods().add(m);
                return;
            }
            // それ以外の初期化子: 巻き戻して何もしない
            idx = save;
        }

        /**
         * expression-bodied ラムダの本体 ({@code (...) -> EXPR} の EXPR 部) を読む。
         * 通常の {@link #parseExpressionStatement} と異なり、終端の {@code ;} は消費しない
         * (フィールド宣言の終端 {@code ;} はフィールドパーサ側で処理させるため)。
         */
        private void parseLambdaExpressionBody(List<JavaMethodInfo.Statement> out) {
            int parenDepth = 0;
            while (!atEnd()) {
                JavaToken t = peek();
                if (parenDepth == 0 && t.is(";")) {
                    return;
                }
                if (parenDepth == 0 && t.is("}")) {
                    return;
                }
                if (t.is("(") || t.is("[")) {
                    parenDepth++;
                } else if (t.is(")") || t.is("]")) {
                    if (parenDepth > 0) {
                        parenDepth--;
                    }
                }
                if (t.type == JavaToken.Type.IDENT && peek(1).is("(")) {
                    String name = t.text;
                    boolean afterNew = idx > 0
                            && tokens.get(idx - 1).isKw("new");
                    if (!isControlKeyword(name) && !afterNew) {
                        String receiver = findReceiver();
                        out.add(new JavaMethodInfo.Call(receiver, name));
                    }
                }
                next();
            }
        }

        /**
         * 指定位置 {@code from} のトークンが {@code open} なら対応する {@code close} の
         * 次の位置を返す。バランスしなければ tokens.size() を返す。
         * {@link #skipBalanced(String, String)} と異なり {@code idx} を変更しない peek 専用。
         */
        private int skipBalancedAt(int from, String open, String close) {
            if (from >= tokens.size() || !tokens.get(from).is(open)) {
                return from;
            }
            int d = 1;
            int i = from + 1;
            boolean angle = "<".equals(open);
            while (i < tokens.size() && d > 0) {
                JavaToken t = tokens.get(i);
                if (t.is(open)) {
                    d++;
                } else if (t.is(close)) {
                    d--;
                    if (d == 0) {
                        return i + 1;
                    }
                } else if (angle && (t.is(">>") || t.is(">>>"))) {
                    d -= t.text.length();
                    if (d <= 0) {
                        return i + 1;
                    }
                }
                i++;
            }
            return i;
        }

        /**
         * 現在位置から「ラムダ式の開始」に見えるか? を peek で判定する。
         * 単一識別子の後に {@code ->} が来るか、釣り合った {@code (...)} の後に {@code ->}
         * が来るパターンを検出する。
         */
        private boolean looksLikeLambdaStart() {
            if (peek().type == JavaToken.Type.IDENT && peek(1).is("->")) {
                return true;
            }
            if (peek().is("(")) {
                int after = skipBalancedAt(idx, "(", ")");
                if (after < tokens.size() && tokens.get(after).is("->")) {
                    return true;
                }
            }
            return false;
        }

        /** ラムダのパラメータ部 (識別子 1 個または {@code (a, b)}) を消費する。 */
        private void consumeLambdaParams() {
            if (peek().is("(")) {
                skipBalanced("(", ")");
            } else if (peek().type == JavaToken.Type.IDENT) {
                next();
            }
        }

        /**
         * フィールド宣言型から、ラムダに対応する SAM メソッド名を推定する。
         * よく使う型は組み込みマップで解決し、未知なら {@code "<inline>"} を返す。
         */
        private static String resolveSamMethodName(String type) {
            if (type == null || type.isEmpty()) {
                return "<inline>";
            }
            // ジェネリックを取り除く
            String t = type;
            int lt = t.indexOf('<');
            if (lt >= 0) {
                t = t.substring(0, lt);
            }
            // 配列を取り除く
            t = t.replace("[]", "").trim();
            // 末尾のシンプル名にする
            int dot = t.lastIndexOf('.');
            if (dot >= 0) {
                t = t.substring(dot + 1);
            }
            String mapped = SAM_FALLBACK.get(t);
            return mapped != null ? mapped : "<inline>";
        }

        private static final java.util.Map<String, String> SAM_FALLBACK;
        static {
            java.util.Map<String, String> m = new java.util.HashMap<>();
            m.put("Runnable", "run");
            m.put("OnClickListener", "onClick");
            m.put("OnLongClickListener", "onLongClick");
            m.put("OnFocusChangeListener", "onFocusChange");
            m.put("OnCheckedChangeListener", "onCheckedChanged");
            m.put("OnItemSelectedListener", "onItemSelected");
            m.put("OnItemClickListener", "onItemClick");
            m.put("OnTouchListener", "onTouch");
            m.put("OnSeekBarChangeListener", "onProgressChanged");
            m.put("OnEditorActionListener", "onEditorAction");
            m.put("OnKeyListener", "onKey");
            m.put("OnScrollListener", "onScrollStateChanged");
            m.put("OnRefreshListener", "onRefresh");
            m.put("Callback", "callback");
            m.put("Observer", "onChanged");
            m.put("Consumer", "accept");
            m.put("Supplier", "get");
            m.put("Function", "apply");
            m.put("Predicate", "test");
            m.put("BiConsumer", "accept");
            m.put("BiFunction", "apply");
            m.put("ActionListener", "actionPerformed");
            m.put("ChangeListener", "stateChanged");
            m.put("PropertyChangeListener", "propertyChange");
            SAM_FALLBACK = java.util.Collections.unmodifiableMap(m);
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
            JavaToken openBrace = peek();
            next();
            extractCallsInBody(m);
            // extractCallsInBody は対応する '}' を消費して戻る。
            // 直前に消費したトークン (= '}') の start を本体終端とする。
            int bodyStart = openBrace.end;
            int bodyEnd = idx > 0 ? tokens.get(idx - 1).start : openBrace.end;
            collectBodyComments(m, bodyStart, bodyEnd);
            cls.getMethods().add(m);
        }

        /** {@code [bodyStart, bodyEnd)} の範囲にあるコメントを {@link JavaMethodInfo#getBodyComments()} に追加する。 */
        private void collectBodyComments(JavaMethodInfo m, int bodyStart, int bodyEnd) {
            if (comments == null || comments.isEmpty() || bodyEnd <= bodyStart) {
                return;
            }
            for (JavaCommentScanner.Comment c : comments) {
                if (c.start >= bodyEnd) {
                    break;
                }
                if (c.start < bodyStart) {
                    continue;
                }
                String t = JavaCommentScanner.cleanText(c);
                if (!t.isEmpty()) {
                    m.getBodyComments().add(t);
                }
            }
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

        /**
         * メソッド本体 ({@code {} は呼び出し前に消費済み) を再帰的に解析し、
         * 文ツリーを {@link JavaMethodInfo#getStatements()} に格納する。
         * 終了時には対応する {@code }} まで消費している。
         */
        private void extractCallsInBody(JavaMethodInfo m) {
            parseStatementBlock(m.getStatements());
        }

        /** 開き {@code {} は消費済み、対応する {@code }} を消費して戻る。 */
        private void parseStatementBlock(List<JavaMethodInfo.Statement> out) {
            while (!atEnd()) {
                if (peek().is("}")) {
                    next();
                    return;
                }
                parseStatement(out);
            }
        }

        /** 1 つの文を読む。 */
        private void parseStatement(List<JavaMethodInfo.Statement> out) {
            if (peek().is(";")) {
                next();
                return;
            }
            if (peek().is("{")) {
                next();
                parseStatementBlock(out);
                return;
            }
            if (peek().isKw("if")) {
                parseIf(out);
                return;
            }
            if (peek().isKw("while")) {
                parseWhile(out);
                return;
            }
            if (peek().isKw("for")) {
                parseFor(out);
                return;
            }
            if (peek().isKw("do")) {
                parseDoWhile(out);
                return;
            }
            if (peek().isKw("switch")) {
                parseSwitch(out);
                return;
            }
            if (peek().isKw("try")) {
                parseTry(out);
                return;
            }
            if (peek().isKw("synchronized")) {
                parseSynchronized(out);
                return;
            }
            if (peek().isKw("return")) {
                parseReturn(out);
                return;
            }
            if (peek().isKw("throw")) {
                parseThrow(out);
                return;
            }
            if (peek().isKw("break")) {
                parseBreak(out);
                return;
            }
            if (peek().isKw("continue")) {
                parseContinue(out);
                return;
            }
            parseExpressionStatement(out);
        }

        /**
         * {@code return [expr];} 文を読む。式中の呼び出しは {@link JavaMethodInfo.Call} として
         * {@code out} に追加し、最後に {@link JavaMethodInfo.Return} を 1 件追加する。
         */
        private void parseReturn(List<JavaMethodInfo.Statement> out) {
            next(); // return
            int startPos = atEnd() ? 0 : peek().start;
            int endPos = startPos;
            int parenDepth = 0;
            while (!atEnd()) {
                JavaToken t = peek();
                if (parenDepth == 0 && t.is(";")) {
                    break;
                }
                if (parenDepth == 0 && t.is("}")) {
                    break;
                }
                if (t.is("(") || t.is("[")) {
                    parenDepth++;
                } else if (t.is(")") || t.is("]")) {
                    if (parenDepth > 0) {
                        parenDepth--;
                    }
                } else if (t.is("{")) {
                    // 匿名クラス本体・ラムダ本体・配列初期化など
                    next();
                    parseStatementBlock(out);
                    continue;
                }
                if (t.type == JavaToken.Type.IDENT && peek(1).is("(")) {
                    String name = t.text;
                    boolean afterNew = idx > 0 && tokens.get(idx - 1).isKw("new");
                    if (!isControlKeyword(name) && !afterNew) {
                        String receiver = findReceiver();
                        out.add(new JavaMethodInfo.Call(receiver, name));
                    }
                }
                endPos = t.end;
                next();
            }
            String expr = endPos > startPos ? src.substring(startPos, endPos).trim() : "";
            out.add(new JavaMethodInfo.Return(expr));
            if (!atEnd() && peek().is(";")) {
                next();
            }
        }

        /**
         * {@code throw expr;} 文を読む。式中の呼び出しは {@link JavaMethodInfo.Call} として
         * {@code out} に追加し、最後に {@link JavaMethodInfo.Throw} を 1 件追加する。
         */
        private void parseThrow(List<JavaMethodInfo.Statement> out) {
            next(); // throw
            int startPos = atEnd() ? 0 : peek().start;
            int endPos = startPos;
            int parenDepth = 0;
            while (!atEnd()) {
                JavaToken t = peek();
                if (parenDepth == 0 && t.is(";")) {
                    break;
                }
                if (parenDepth == 0 && t.is("}")) {
                    break;
                }
                if (t.is("(") || t.is("[")) {
                    parenDepth++;
                } else if (t.is(")") || t.is("]")) {
                    if (parenDepth > 0) {
                        parenDepth--;
                    }
                } else if (t.is("{")) {
                    next();
                    parseStatementBlock(out);
                    continue;
                }
                if (t.type == JavaToken.Type.IDENT && peek(1).is("(")) {
                    String name = t.text;
                    boolean afterNew = idx > 0 && tokens.get(idx - 1).isKw("new");
                    if (!isControlKeyword(name) && !afterNew) {
                        String receiver = findReceiver();
                        out.add(new JavaMethodInfo.Call(receiver, name));
                    }
                }
                endPos = t.end;
                next();
            }
            String expr = endPos > startPos ? src.substring(startPos, endPos).trim() : "";
            out.add(new JavaMethodInfo.Throw(expr));
            if (!atEnd() && peek().is(";")) {
                next();
            }
        }

        /** {@code break [label];} 文を読む。 */
        private void parseBreak(List<JavaMethodInfo.Statement> out) {
            next(); // break
            String label = "";
            if (!atEnd() && peek().type == JavaToken.Type.IDENT) {
                label = peek().text;
                next();
            }
            if (!atEnd() && peek().is(";")) {
                next();
            }
            out.add(new JavaMethodInfo.Break(label));
        }

        /** {@code continue [label];} 文を読む。 */
        private void parseContinue(List<JavaMethodInfo.Statement> out) {
            next(); // continue
            String label = "";
            if (!atEnd() && peek().type == JavaToken.Type.IDENT) {
                label = peek().text;
                next();
            }
            if (!atEnd() && peek().is(";")) {
                next();
            }
            out.add(new JavaMethodInfo.Continue(label));
        }

        /** {@code if (...)} 単体 (else 連鎖含む) を 1 つの {@link JavaMethodInfo.Block} として読む。 */
        private void parseIf(List<JavaMethodInfo.Statement> out) {
            next(); // if
            String cond = consumeParens(out);
            JavaMethodInfo.Block block = new JavaMethodInfo.Block(JavaMethodInfo.Block.Kind.IF);
            JavaMethodInfo.Branch first = new JavaMethodInfo.Branch("if", cond);
            block.getBranches().add(first);
            out.add(block);
            parseSubStatement(first.getBody());
            while (!atEnd() && peek().isKw("else")) {
                next(); // else
                if (peek().isKw("if")) {
                    next(); // if
                    String c2 = consumeParens(out);
                    JavaMethodInfo.Branch ei = new JavaMethodInfo.Branch("else if", c2);
                    block.getBranches().add(ei);
                    parseSubStatement(ei.getBody());
                } else {
                    JavaMethodInfo.Branch e = new JavaMethodInfo.Branch("else", "");
                    block.getBranches().add(e);
                    parseSubStatement(e.getBody());
                    break;
                }
            }
        }

        private void parseWhile(List<JavaMethodInfo.Statement> out) {
            next(); // while
            String cond = consumeParens(out);
            JavaMethodInfo.Block b = new JavaMethodInfo.Block(JavaMethodInfo.Block.Kind.WHILE);
            JavaMethodInfo.Branch br = new JavaMethodInfo.Branch("while", cond);
            b.getBranches().add(br);
            out.add(b);
            parseSubStatement(br.getBody());
        }

        private void parseFor(List<JavaMethodInfo.Statement> out) {
            next(); // for
            String header = consumeParens(out);
            JavaMethodInfo.Block b = new JavaMethodInfo.Block(JavaMethodInfo.Block.Kind.FOR);
            JavaMethodInfo.Branch br = new JavaMethodInfo.Branch("for", header);
            b.getBranches().add(br);
            out.add(b);
            parseSubStatement(br.getBody());
        }

        private void parseDoWhile(List<JavaMethodInfo.Statement> out) {
            next(); // do
            JavaMethodInfo.Block b = new JavaMethodInfo.Block(JavaMethodInfo.Block.Kind.DO_WHILE);
            JavaMethodInfo.Branch br = new JavaMethodInfo.Branch("do", "");
            b.getBranches().add(br);
            out.add(b);
            parseSubStatement(br.getBody());
            if (!atEnd() && peek().isKw("while")) {
                next(); // while
                String cond = consumeParens(out);
                br.setLabel(cond);
                if (peek().is(";")) {
                    next();
                }
            }
        }

        private void parseSynchronized(List<JavaMethodInfo.Statement> out) {
            next(); // synchronized
            String lock = consumeParens(out);
            JavaMethodInfo.Block b = new JavaMethodInfo.Block(JavaMethodInfo.Block.Kind.SYNCHRONIZED);
            JavaMethodInfo.Branch br = new JavaMethodInfo.Branch("synchronized", lock);
            b.getBranches().add(br);
            out.add(b);
            parseSubStatement(br.getBody());
        }

        private void parseSwitch(List<JavaMethodInfo.Statement> out) {
            next(); // switch
            String cond = consumeParens(out);
            JavaMethodInfo.Block sw = new JavaMethodInfo.Block(JavaMethodInfo.Block.Kind.SWITCH);
            // SWITCH 本体の式自体は最初の Branch に "switch" として保持
            JavaMethodInfo.Branch head = new JavaMethodInfo.Branch("switch", cond);
            sw.getBranches().add(head);
            out.add(sw);
            if (!peek().is("{")) {
                return;
            }
            next(); // {
            JavaMethodInfo.Branch currentCase = null;
            while (!atEnd() && !peek().is("}")) {
                if (peek().isKw("case") || peek().isKw("default")) {
                    String type;
                    String label;
                    if (peek().isKw("default")) {
                        next();
                        type = "default";
                        label = "";
                    } else {
                        next(); // case
                        int s = peek().start;
                        int e = s;
                        while (!atEnd() && !peek().is(":") && !peek().is("->")) {
                            e = peek().end;
                            next();
                        }
                        type = "case";
                        label = src.substring(s, e).trim();
                    }
                    if (!atEnd() && (peek().is(":") || peek().is("->"))) {
                        next();
                    }
                    currentCase = new JavaMethodInfo.Branch(type, label);
                    sw.getBranches().add(currentCase);
                } else if (currentCase != null) {
                    parseStatement(currentCase.getBody());
                } else {
                    // case の前に何かある異常系: 1 トークン進めるのみ
                    next();
                }
            }
            if (!atEnd() && peek().is("}")) {
                next();
            }
        }

        private void parseTry(List<JavaMethodInfo.Statement> out) {
            next(); // try
            // try-with-resources の括弧があれば読み飛ばす (内部の呼び出しは out に追加)
            if (peek().is("(")) {
                consumeParens(out);
            }
            JavaMethodInfo.Block t = new JavaMethodInfo.Block(JavaMethodInfo.Block.Kind.TRY);
            JavaMethodInfo.Branch tryBranch = new JavaMethodInfo.Branch("try", "");
            t.getBranches().add(tryBranch);
            out.add(t);
            parseSubStatement(tryBranch.getBody());
            while (!atEnd() && peek().isKw("catch")) {
                next();
                String except = consumeParens(out);
                JavaMethodInfo.Branch c = new JavaMethodInfo.Branch("catch", except);
                t.getBranches().add(c);
                parseSubStatement(c.getBody());
            }
            if (!atEnd() && peek().isKw("finally")) {
                next();
                JavaMethodInfo.Branch f = new JavaMethodInfo.Branch("finally", "");
                t.getBranches().add(f);
                parseSubStatement(f.getBody());
            }
        }

        /** {@code if(cond)} の後ろなど、単一文または {@code {}} ブロックを読む。 */
        private void parseSubStatement(List<JavaMethodInfo.Statement> out) {
            if (atEnd()) {
                return;
            }
            if (peek().is("{")) {
                next();
                parseStatementBlock(out);
            } else {
                parseStatement(out);
            }
        }

        /**
         * 開き {@code (} で始まる括弧内容を読み、ソース文字列としての中身を返す。
         * 内部に呼び出し式があれば {@code out} に追加 (条件式評価で実行される呼び出しは
         * 制御ブロックの直前に出るので、シーケンス図上もそのように見える)。
         */
        private String consumeParens(List<JavaMethodInfo.Statement> out) {
            if (!peek().is("(")) {
                return "";
            }
            int openEnd = peek().end;
            next(); // (
            int depth = 1;
            int closeStart = openEnd;
            while (!atEnd() && depth > 0) {
                JavaToken t = peek();
                if (t.is("(")) {
                    depth++;
                } else if (t.is(")")) {
                    depth--;
                    if (depth == 0) {
                        closeStart = t.start;
                        next();
                        return src.substring(openEnd, closeStart).trim();
                    }
                }
                if (t.type == JavaToken.Type.IDENT && peek(1).is("(")) {
                    String name = t.text;
                    boolean afterNew = idx > 0
                            && tokens.get(idx - 1).isKw("new");
                    if (!isControlKeyword(name) && !afterNew) {
                        String receiver = findReceiver();
                        out.add(new JavaMethodInfo.Call(receiver, name));
                    }
                }
                next();
            }
            return src.substring(openEnd, closeStart).trim();
        }

        /**
         * 通常の式文 ({@code ;} 終端) または特殊文を読む。途中に出現する呼び出しを
         * {@code out} に追加する。ブロック {@code {} に出会ったら再帰的に文として展開し、
         * 同じ {@code out} に呼び出しを追加する (匿名クラス本体・ラムダ本体・配列初期化など)。
         */
        private void parseExpressionStatement(List<JavaMethodInfo.Statement> out) {
            int parenDepth = 0;
            while (!atEnd()) {
                JavaToken t = peek();
                if (parenDepth == 0 && t.is(";")) {
                    next();
                    return;
                }
                if (parenDepth == 0 && t.is("}")) {
                    return;
                }
                if (t.is("(") || t.is("[")) {
                    parenDepth++;
                } else if (t.is(")") || t.is("]")) {
                    if (parenDepth > 0) {
                        parenDepth--;
                    }
                } else if (t.is("{")) {
                    // 匿名クラス本体・ラムダ本体・配列初期化など。
                    // 内部の呼び出しはフラットに out に追加する (構造化はしない)。
                    next();
                    parseStatementBlock(out);
                    continue;
                }
                if (t.type == JavaToken.Type.IDENT && peek(1).is("(")) {
                    String name = t.text;
                    boolean afterNew = idx > 0
                            && tokens.get(idx - 1).isKw("new");
                    if (!isControlKeyword(name) && !afterNew) {
                        String receiver = findReceiver();
                        out.add(new JavaMethodInfo.Call(receiver, name));
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
