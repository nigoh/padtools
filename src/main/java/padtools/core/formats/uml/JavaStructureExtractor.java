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
            "volatile", "sealed"));

    /** Java ソースから ClassInfo のリストを返す。 */
    public static List<JavaClassInfo> extract(String source) {
        return extract(source, null);
    }

    /** エラーリスナー付き。 */
    public static List<JavaClassInfo> extract(String source, ErrorListener listener) {
        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }
        // Java の Unicode エスケープは字句解析の前に展開される (JLS 3.3)。
        // 識別子・キーワードを含むエスケープを正しく扱うため、入口で 1 度だけ展開する。
        String expanded = JavaLexer.expandUnicodeEscapes(source);
        List<JavaToken> tokens = new JavaLexer(expanded).tokenize();
        List<JavaCommentScanner.Comment> comments = JavaCommentScanner.scan(expanded);
        Extractor e = new Extractor(tokens, expanded, comments,
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
            h.getImports().addAll(c.getImports());
            h.getModifiers().addAll(c.getModifiers());
            h.getAnnotations().addAll(c.getAnnotations());
            h.setSuperClass(c.getSuperClass());
            h.getInterfaces().addAll(c.getInterfaces());
            h.setEnclosingClass(c.getEnclosingClass());
            h.setAaosCategory(c.getAaosCategory());
            h.setAndroidComponentType(c.getAndroidComponentType());
            // モジュール宣言の directive は header-only モードでも保持する
            // (モジュールグラフ系の集計に必要なため)。
            h.getModuleDirectives().addAll(c.getModuleDirectives());
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
        /**
         * メソッド本体内で {@code [this.] IDENT = <inline>} を検出したが、
         * 解析時点で同名フィールドがまだ宣言されていなかったケースを保留する。
         * クラス本体のパース完了後にマッチを試みる。
         */
        private final List<PendingAssignment> pendingFieldAssignments = new ArrayList<>();
        private String packageName = "";
        /** ファイルレベルで宣言された import の完全修飾名 (またはワイルドカード)。 */
        private final List<String> imports = new ArrayList<>();
        private int idx;
        /**
         * 現在ネスト中の switch 文/式の深さ。{@code yield expr;} を switch アーム
         * 内でだけ {@link JavaMethodInfo.Yield} として認識するために使う。0 なら
         * switch の外。
         */
        private int switchDepth;

        private static final class PendingAssignment {
            final JavaClassInfo cls;
            final String fieldName;
            final List<JavaMethodInfo> inlineMethods;

            PendingAssignment(JavaClassInfo cls, String fieldName,
                              List<JavaMethodInfo> inlineMethods) {
                this.cls = cls;
                this.fieldName = fieldName;
                this.inlineMethods = inlineMethods;
            }
        }

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
                    next(); // import
                    boolean isStatic = peek().isKw("static");
                    if (isStatic) {
                        next();
                    }
                    String imp = readImportName();
                    if (!imp.isEmpty()) {
                        imports.add(isStatic ? "static " + imp : imp);
                    }
                    consume(";");
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
                // module-info.java の宣言。`module Foo { ... }` または
                // `open module Foo { ... }` (JLS §7.7)。`module`/`open` は
                // 文脈依存キーワード (IDENT) なので isKw で識別する。
                if (peek().isKw("module")
                        || (peek().isKw("open") && peek(1).isKw("module"))) {
                    parseModuleDecl(mods, ann, comment);
                    continue;
                }
                next();
            }
            resolvePendingFieldAssignments();
        }

        /**
         * クラス本体パース中に出現したが宣言順の都合で同名フィールドにマッチできなかった
         * 代入 ({@link PendingAssignment}) を、最終的にフィールドへ紐づける。
         * 該当フィールドが見つからない場合は破棄する (解析対象外のローカル変数等)。
         */
        private void resolvePendingFieldAssignments() {
            for (PendingAssignment p : pendingFieldAssignments) {
                JavaFieldInfo f = findFieldByName(p.cls, p.fieldName);
                if (f != null) {
                    f.getInlineMethods().addAll(p.inlineMethods);
                }
            }
            pendingFieldAssignments.clear();
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
            info.getImports().addAll(imports);
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

        /**
         * {@code [open] module a.b.c { ... }} を解析する (JLS §7.7)。
         *
         * <p>本体は {@code requires} / {@code exports} / {@code opens} /
         * {@code uses} / {@code provides ... with ...} の各ディレクティブで
         * 構成される。{@code module}/{@code open}/{@code requires} 等は
         * 文脈依存キーワード (IDENT) なので {@link JavaToken#isKw(String)}
         * で識別する。</p>
         */
        private void parseModuleDecl(List<String> mods, List<String> annotations,
                                      String comment) {
            JavaClassInfo info = new JavaClassInfo();
            info.setKind(JavaClassInfo.Kind.MODULE);
            info.setPackageName(packageName);
            info.getModifiers().addAll(mods);
            info.getAnnotations().addAll(annotations);
            info.setComment(comment);

            if (peek().isKw("open")) {
                next();
                info.getModifiers().add("open");
            }
            if (!peek().isKw("module")) {
                // 万一誤検出だった場合のセーフネット
                results.add(info);
                return;
            }
            next(); // module
            String moduleName = readDottedName();
            info.setSimpleName(moduleName);

            if (!peek().is("{")) {
                // 本体が無い場合は名前だけ記録して終わる
                if (peek().is(";")) {
                    next();
                }
                results.add(info);
                return;
            }
            next(); // {
            parseModuleBody(info);
            results.add(info);
        }

        /** {@code module} の本体ディレクティブを {@code }} まで読む。 */
        private void parseModuleBody(JavaClassInfo info) {
            while (!atEnd() && !peek().is("}")) {
                if (peek().is(";")) {
                    next();
                    continue;
                }
                // 各ディレクティブは contextual keyword で始まる
                if (peek().isKw("requires")) {
                    next();
                    List<String> dmods = new ArrayList<>();
                    // requires の `transitive`/`static` は contextual keyword なので
                    // 次のトークンが IDENT (= モジュール名の継続) のときだけ修飾子扱い
                    while ((peek().isKw("transitive") || peek().isKw("static"))
                            && peek(1).type == JavaToken.Type.IDENT) {
                        dmods.add(next().text);
                    }
                    String target = readDottedName();
                    info.getModuleDirectives().add(new JavaModuleDirective(
                            JavaModuleDirective.Kind.REQUIRES, target, dmods, null));
                    consume(";");
                    continue;
                }
                if (peek().isKw("exports") || peek().isKw("opens")) {
                    JavaModuleDirective.Kind k = peek().isKw("exports")
                            ? JavaModuleDirective.Kind.EXPORTS
                            : JavaModuleDirective.Kind.OPENS;
                    next();
                    String pkg = readDottedName();
                    List<String> tgts = new ArrayList<>();
                    if (peek().isKw("to")) {
                        next();
                        tgts.addAll(readDottedNameList());
                    }
                    info.getModuleDirectives().add(new JavaModuleDirective(
                            k, pkg, null, tgts));
                    consume(";");
                    continue;
                }
                if (peek().isKw("uses")) {
                    next();
                    String service = readDottedName();
                    info.getModuleDirectives().add(new JavaModuleDirective(
                            JavaModuleDirective.Kind.USES, service, null, null));
                    consume(";");
                    continue;
                }
                if (peek().isKw("provides")) {
                    next();
                    String service = readDottedName();
                    List<String> impls = new ArrayList<>();
                    if (peek().isKw("with")) {
                        next();
                        impls.addAll(readDottedNameList());
                    }
                    info.getModuleDirectives().add(new JavaModuleDirective(
                            JavaModuleDirective.Kind.PROVIDES, service, null, impls));
                    consume(";");
                    continue;
                }
                // 未知のトークンは 1 つ消費して継続 (前方互換)
                next();
            }
            if (!atEnd() && peek().is("}")) {
                next();
            }
        }

        /** {@code A.b.C, D.e.F, ...} を読み取って文字列リストとして返す。 */
        private List<String> readDottedNameList() {
            List<String> out = new ArrayList<>();
            String first = readDottedName();
            if (!first.isEmpty()) {
                out.add(first);
            }
            while (peek().is(",")) {
                next();
                String n = readDottedName();
                if (!n.isEmpty()) {
                    out.add(n);
                }
            }
            return out;
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
            // 型 + 最初の名前を読み込み (; / = / , に到達するまで)
            int depth = 0;
            while (!atEnd()) {
                JavaToken t = peek();
                if (depth == 0 && (t.is(";") || t.is("=") || t.is(","))) {
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
            JavaFieldInfo first = addField(cls, mods, annotations, comment, fieldName, type);
            // 初期化子が匿名クラス/ラムダなら本体を吸い上げて f.inlineMethods に格納する。
            if (!atEnd() && peek().is("=")) {
                next(); // '=' を消費
                tryParseInlineInitializer(first);
                skipUntilCommaOrSemicolonRespectingBlocks();
            }
            // 追加変数 (int a, b, c = 1)
            while (!atEnd() && peek().is(",")) {
                next(); // ','
                if (atEnd() || peek().type != JavaToken.Type.IDENT) {
                    break;
                }
                String name2 = next().text;
                // 配列ブラケット `b[]` を型に追加
                String type2 = type;
                while (!atEnd() && peek().is("[") && peek(1).is("]")) {
                    next();
                    next();
                    type2 = type2 + "[]";
                }
                JavaFieldInfo extra = addField(cls, mods, annotations, comment, name2, type2);
                if (!atEnd() && peek().is("=")) {
                    next();
                    tryParseInlineInitializer(extra);
                    skipUntilCommaOrSemicolonRespectingBlocks();
                }
            }
            // ; までスキップ
            skipUntilSemicolonRespectingBlocks();
        }

        private JavaFieldInfo addField(JavaClassInfo cls, List<String> mods,
                                        List<String> annotations, String comment,
                                        String name, String type) {
            JavaFieldInfo f = new JavaFieldInfo();
            f.setName(name);
            f.setType(normalizeType(stripAnnotations(type)));
            f.setVisibility(Visibility.fromModifiers(mods));
            f.setStatic(mods.contains("static"));
            f.setFinal(mods.contains("final"));
            f.getAnnotations().addAll(annotations);
            f.setComment(comment);
            cls.getFields().add(f);
            return f;
        }

        /**
         * 初期化式の途中から {@code ,} または {@code ;} までを括弧深度を考慮して読み飛ばす。
         * フィールドの複数宣言 ({@code int a = 1, b = 2;}) で次の変数に進むために使う。
         */
        private void skipUntilCommaOrSemicolonRespectingBlocks() {
            int d = 0;
            while (!atEnd()) {
                JavaToken t = peek();
                if (d == 0 && (t.is(";") || t.is(","))) {
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

        /**
         * フィールド初期化子が {@code new SomeType(...) {...}} (匿名クラス) または
         * {@code args -> body} (ラムダ) または {@code Foo::bar} (メソッド参照) の場合、
         * その本体内に出現するメソッドを {@link JavaFieldInfo#getInlineMethods()} に格納する。
         *
         * <p>{@code =} は呼び出し側で既に消費済み。本メソッドは消費したトークンを
         * 巻き戻して呼び出し側の {@link #skipUntilSemicolonRespectingBlocks()} に
         * 安全に引き継ぐ責務を持つ (異常検出時は何もしない)。</p>
         */
        private void tryParseInlineInitializer(JavaFieldInfo f) {
            List<JavaMethodInfo> captured = tryParseInlineExpression(f.getType(), f.getName());
            if (captured != null && !captured.isEmpty()) {
                f.getInlineMethods().addAll(captured);
            }
        }

        /**
         * 現在位置から「関数を変数として設定」する式 (匿名クラス / ラムダ / メソッド参照)
         * を検出して、本体から抽出したメソッド一覧を返す。検出できなければ idx を元に戻して
         * null を返す。
         *
         * @param samTypeHint SAM 型のヒント (フィールド型・パラメータ型など)。null/空でも動く。
         * @param nameHint    ヒント用の変数名 (フィールド名/受信側など)。null 可。
         */
        private List<JavaMethodInfo> tryParseInlineExpression(String samTypeHint, String nameHint) {
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
                    return null;
                }
                // 引数 (...)
                if (probe < tokens.size() && tokens.get(probe).is("(")) {
                    probe = skipBalancedAt(probe, "(", ")");
                } else {
                    return null;
                }
                // 直後が { なら匿名クラス本体
                if (probe < tokens.size() && tokens.get(probe).is("{")) {
                    idx = probe;
                    next(); // '{' を消費
                    JavaClassInfo dummy = new JavaClassInfo();
                    dummy.setSimpleName("$anon");
                    // classStack には push しない (inner-class 判定の副作用回避)
                    parseClassBody(dummy);
                    return new ArrayList<>(dummy.getMethods());
                }
                idx = save;
                return null;
            }
            // ラムダ判定: x -> ... または (a, b) -> ...
            if (looksLikeLambdaStart()) {
                consumeLambdaParams();
                if (atEnd() || !peek().is("->")) {
                    idx = save;
                    return null;
                }
                next(); // '->'
                String samName = resolveSamMethodName(samTypeHint, nameHint);
                JavaMethodInfo m = new JavaMethodInfo();
                m.setName(samName);
                m.setVisibility(Visibility.PUBLIC);
                if (!atEnd() && peek().is("{")) {
                    next();
                    parseStatementBlock(m.getStatements());
                } else {
                    // expression-bodied lambda: 文末 ';' は消費せず呼び出し元に残す
                    parseLambdaExpressionBody(m.getStatements());
                }
                List<JavaMethodInfo> out = new ArrayList<>();
                out.add(m);
                return out;
            }
            // メソッド参照判定: IDENT (. IDENT)* :: IDENT  (`::new` は除外)
            if (looksLikeMethodReferenceStart()) {
                String receiver = consumeMethodReferenceReceiver();
                if (!peek().is("::")) {
                    idx = save;
                    return null;
                }
                next(); // '::'
                if (atEnd() || peek().type != JavaToken.Type.IDENT
                        || "new".equals(peek().text)) {
                    idx = save;
                    return null;
                }
                String refTarget = next().text;
                String samName = resolveSamMethodName(samTypeHint, nameHint);
                JavaMethodInfo m = new JavaMethodInfo();
                m.setName(samName);
                m.setVisibility(Visibility.PUBLIC);
                // 本体は単一呼び出しに展開しておく (シーケンス図がそのまま辿れる)
                m.getStatements().add(new JavaMethodInfo.Call(receiver, refTarget));
                List<JavaMethodInfo> out = new ArrayList<>();
                out.add(m);
                return out;
            }
            return null;
        }

        /** 現在位置から {@code IDENT (. IDENT)* ::} の形で始まっていそうかを peek で判定する。 */
        private boolean looksLikeMethodReferenceStart() {
            if (peek().type != JavaToken.Type.IDENT) {
                return false;
            }
            int probe = idx + 1;
            while (probe + 1 < tokens.size()
                    && tokens.get(probe).is(".")
                    && tokens.get(probe + 1).type == JavaToken.Type.IDENT) {
                probe += 2;
            }
            return probe < tokens.size() && tokens.get(probe).is("::");
        }

        /** {@link #looksLikeMethodReferenceStart()} が true の前提で受信側を消費して文字列化する。 */
        private String consumeMethodReferenceReceiver() {
            StringBuilder sb = new StringBuilder();
            sb.append(next().text);
            while (!atEnd() && peek().is(".")
                    && idx + 1 < tokens.size()
                    && tokens.get(idx + 1).type == JavaToken.Type.IDENT) {
                next(); // '.'
                sb.append('.').append(next().text);
            }
            return sb.toString();
        }

        /**
         * expression-bodied ラムダの本体 ({@code (...) -> EXPR} の EXPR 部) を読む。
         * 通常の {@link #parseExpressionStatement} と異なり、終端の {@code ;} は消費しない
         * (フィールド宣言の終端 {@code ;} はフィールドパーサ側で処理させるため)。
         * 呼び出し引数の中に現れた expression-bodied ラムダ ({@code foo(v -> bar(), 1)})
         * にも対応するため、parenDepth=0 で外側の {@code )} / {@code ,} に出会ったら
         * 消費せずに戻る。
         */
        private void parseLambdaExpressionBody(List<JavaMethodInfo.Statement> out) {
            int parenDepth = 0;
            while (!atEnd()) {
                JavaToken t = peek();
                if (parenDepth == 0 && (t.is(";") || t.is("}")
                        || t.is(")") || t.is(","))) {
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
         * フィールド宣言型から、ラムダ/メソッド参照に対応する SAM メソッド名を推定する。
         * よく使う型は組み込みマップで解決し、未知でもサフィックス(Listener/Handler/Callback/
         * Observer/Action) を検出できれば命名規約から推定する。最終的に解決できなければ
         * {@code "<inline>"} を返す。
         */
        private static String resolveSamMethodName(String type) {
            return resolveSamMethodName(type, null);
        }

        private static String resolveSamMethodName(String type, String nameHint) {
            if (type == null || type.isEmpty()) {
                // nameHint が set+型名 パターン (例: setOnCheckedChangeListener → OnCheckedChangeListener)
                // なら型名を抽出して SAM_FALLBACK / 命名規約で解決を再試行する
                if (nameHint != null && nameHint.length() > 3
                        && nameHint.startsWith("set")
                        && Character.isUpperCase(nameHint.charAt(3))) {
                    return resolveSamMethodName(nameHint.substring(3), null);
                }
                // onXxx 形式の nameHint ならそのまま SAM メソッド名として採用
                if (nameHint != null && nameHint.length() > 2
                        && nameHint.startsWith("on")
                        && Character.isUpperCase(nameHint.charAt(2))) {
                    return nameHint;
                }
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
            if (mapped != null) {
                return mapped;
            }
            // 命名規約による推定: <Stem><Suffix> → <stem>
            // 例: PrintHandler → print / OnFooListener → onFoo / MyCallback → my
            for (String suf : SAM_NAME_SUFFIXES) {
                if (t.endsWith(suf) && t.length() > suf.length()) {
                    String stem = t.substring(0, t.length() - suf.length());
                    if (!stem.isEmpty()) {
                        return Character.toLowerCase(stem.charAt(0)) + stem.substring(1);
                    }
                }
            }
            // 命名規約に当たらないが、フィールド/受け取り変数名が onXxx 形式ならそれを採用
            if (nameHint != null && nameHint.length() > 2
                    && nameHint.startsWith("on")
                    && Character.isUpperCase(nameHint.charAt(2))) {
                return nameHint;
            }
            return "<inline>";
        }

        private static final String[] SAM_NAME_SUFFIXES = {
                "Listener", "Handler", "Callback", "Observer", "Action"
        };

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
            // throws Foo, Bar.Baz, com.x.Quux
            if (!atEnd() && peek().isKw("throws")) {
                next();
                m.getThrowsTypes().addAll(readTypeList());
            }
            // 残り (アノテーション付きパラメータ後の改行ノイズなど) を { または ; までスキップ
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
            // ローカルクラス / record / interface / enum: 修飾子・アノテーション前置を許容
            int localDeclKw = peekLocalClassDeclKeyword();
            if (localDeclKw >= 0) {
                parseLocalClassDecl();
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
            // `yield expr;` は switch アーム内でのみ文として意味を持つ
            // (Java 14+ switch 式)。switch の外では IDENT として扱う。
            if (switchDepth > 0 && peek().isKw("yield") && looksLikeYieldStatement()) {
                parseYield(out);
                return;
            }
            parseExpressionStatement(out);
        }

        /**
         * 現在位置の {@code yield} が文として始まっているかを判定する。
         * 直後に {@code (} ({@code yield(...)} 呼び出し)、{@code =} (代入)、
         * {@code .} (フィールド/メソッドアクセス) が続く場合は通常の式と
         * みなして false を返す。
         */
        private boolean looksLikeYieldStatement() {
            JavaToken nxt = peek(1);
            if (nxt.is("(") || nxt.is(".") || nxt.is("=") || nxt.is(";")
                    || nxt.is("::")) {
                return false;
            }
            return true;
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
                // Java 14+ switch 式: return switch(x) {...}; を構造化して取り込む
                if (t.isKw("switch") && peek(1).is("(")) {
                    endPos = t.end;
                    parseSwitch(out);
                    continue;
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
                // Java 14+ switch 式: throw switch(x) {...}; を構造化して取り込む
                if (t.isKw("switch") && peek(1).is("(")) {
                    endPos = t.end;
                    parseSwitch(out);
                    continue;
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

        /**
         * 現在位置がローカルクラス宣言の開始に見える場合、{@code class}/{@code record}/
         * {@code interface}/{@code enum} キーワードのトークン位置を返す。違えば -1。
         * 修飾子 ({@code final}, {@code abstract}, {@code static}) とアノテーション
         * ({@code @Foo} / {@code @Foo(...)}) を間に許容する。
         */
        private int peekLocalClassDeclKeyword() {
            int p = idx;
            while (p < tokens.size()) {
                JavaToken t = tokens.get(p);
                if (t.isKw("class") || t.isKw("interface")
                        || t.isKw("enum") || t.isKw("record")) {
                    return p;
                }
                if (t.is("@") && p + 1 < tokens.size()
                        && tokens.get(p + 1).type == JavaToken.Type.IDENT) {
                    p += 2;
                    while (p < tokens.size()
                            && (tokens.get(p).type == JavaToken.Type.IDENT
                                || tokens.get(p).is("."))) {
                        p++;
                    }
                    if (p < tokens.size() && tokens.get(p).is("(")) {
                        p = skipBalancedAt(p, "(", ")");
                    }
                    continue;
                }
                if (t.type == JavaToken.Type.IDENT && MODIFIERS.contains(t.text)) {
                    p++;
                    continue;
                }
                return -1;
            }
            return -1;
        }

        /**
         * メソッド本体内のローカル型宣言 ({@code class}/{@code record}/
         * {@code interface}/{@code enum}) を読み、トップレベル/メンバ宣言と同じく
         * {@link #results} に追加する。enclosingClass には現在の最も内側のクラス名を
         * 設定する。
         */
        private void parseLocalClassDecl() {
            int declStart = peek().start;
            List<String> annotations = readAnnotations();
            List<String> mods = readModifiers();
            String comment = findCommentBefore(declStart);
            if (peek().isKw("class")) {
                parseClassDecl(JavaClassInfo.Kind.CLASS, mods, annotations, comment);
            } else if (peek().isKw("interface")) {
                parseClassDecl(JavaClassInfo.Kind.INTERFACE, mods, annotations, comment);
            } else if (peek().isKw("enum")) {
                parseClassDecl(JavaClassInfo.Kind.ENUM, mods, annotations, comment);
            } else if (peek().isKw("record")) {
                parseClassDecl(JavaClassInfo.Kind.RECORD, mods, annotations, comment);
            } else {
                // 万一一致しない場合は文として進める
                next();
            }
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
            switchDepth++;
            try {
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
                            // パターン case の `when` ガード (Java 21+) も含めて
                            // ラベル末端 (`:` か `->`) まで読む。括弧深度を加味して
                            // ラムダ等での `,` を case 区切りと誤認しない。
                            int paren = 0;
                            while (!atEnd()
                                    && !(paren == 0 && (peek().is(":") || peek().is("->")))) {
                                JavaToken t = peek();
                                if (t.is("(") || t.is("[") || t.is("{")) {
                                    paren++;
                                } else if (t.is(")") || t.is("]") || t.is("}")) {
                                    if (paren > 0) {
                                        paren--;
                                    }
                                }
                                e = t.end;
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
            } finally {
                switchDepth--;
            }
        }

        /**
         * {@code yield expr;} 文 (Java 14+ switch 式) を読む。
         * 式中の呼び出しは {@link JavaMethodInfo.Call} として {@code out} に追加し、
         * 最後に {@link JavaMethodInfo.Yield} を 1 件追加する。
         * {@code yield} は文脈依存キーワード (IDENT) なので、呼び出し側で
         * switch アーム内であることを確認してから呼ぶこと。
         */
        private void parseYield(List<JavaMethodInfo.Statement> out) {
            next(); // yield
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
                // yield switch(...) のネスト switch 式も構造化して取り込む
                if (t.isKw("switch") && peek(1).is("(")) {
                    endPos = t.end;
                    parseSwitch(out);
                    continue;
                }
                if (t.is("(") || t.is("[")) {
                    parenDepth++;
                } else if (t.is(")") || t.is("]")) {
                    if (parenDepth > 0) {
                        parenDepth--;
                    }
                } else if (t.is("{")) {
                    // ラムダ本体・匿名クラス・配列初期化など
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
            out.add(new JavaMethodInfo.Yield(expr));
            if (!atEnd() && peek().is(";")) {
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
         *
         * <p>文頭で {@code [this.] IDENT = <lambda|匿名クラス|メソッド参照>} のパターンを
         * 検出した場合は、囲っているクラスに同名フィールドがあればそのフィールドの
         * {@link JavaFieldInfo#getInlineMethods()} にコールバック本体を取り込む
         * (コンストラクタ内/任意メソッド内のフィールド代入対応)。</p>
         */
        private void parseExpressionStatement(List<JavaMethodInfo.Statement> out) {
            // 文頭の "[this.] IDENT = <inline>" を検出
            tryCaptureFieldAssignmentInline();
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
                    // Java 14+ の switch 式 (`int x = switch(y){...};` /
                    // `foo(switch(y){...})`) を構造化して取り込む。switch 自身は
                    // 呼び出しではないので Call 化はせず、parseSwitch に委譲。
                    if ("switch".equals(name) && !afterNew) {
                        parseSwitch(out);
                        continue;
                    }
                    if (!isControlKeyword(name) && !afterNew) {
                        String receiver = findReceiver();
                        JavaMethodInfo.Call call = new JavaMethodInfo.Call(receiver, name);
                        out.add(call);
                        // 引数の先頭が lambda/匿名クラス/メソッド参照ならコールバックとして
                        // この Call.inlineMethods に紐づける (setOnClickListener(...) 等)
                        next(); // IDENT
                        if (!atEnd() && peek().is("(")) {
                            next(); // '('
                            // 先に定数シンボル ({@code FOO.BAR_BAZ}) を probe-only で確認
                            tryCaptureFirstConstantArgument(call);
                            tryCaptureFirstInlineArgument(call);
                        }
                        // 上で `(` を 1 つ消費したが、外側の parenDepth とは別管理にした
                        // ため、ここで `)` まで読み進める間の depth 整合性を保つ必要がある。
                        // 簡略化のため parenDepth に +1 して `)` での -1 を待つ。
                        parenDepth++;
                        continue;
                    }
                }
                // メソッド参照: Foo::bar / obj.field::bar (`::new` は除外)
                if (t.is("::") && peek(1).type == JavaToken.Type.IDENT) {
                    String name = peek(1).text;
                    if (!"new".equals(name)) {
                        String receiver = findReceiverBeforeColonColon();
                        out.add(new JavaMethodInfo.Call(receiver, name));
                    }
                }
                next();
            }
        }

        /**
         * 文頭で {@code [this.] IDENT = <inline>} の代入パターンを検出した場合、
         * 同名フィールドの inlineMethods にコールバック本体を取り込み、
         * 代入の右辺 (inline 部分) を消費する。{@code =} は消費するが文末 {@code ;}
         * は呼び出し元 ({@link #parseExpressionStatement}) で処理させる。
         *
         * <p>該当パターンに見えない場合は idx を変更しない。</p>
         */
        private void tryCaptureFieldAssignmentInline() {
            if (classStack.isEmpty()) {
                return;
            }
            int save = idx;
            String targetName = null;
            // `this . IDENT =` あるいは `IDENT =` を検出
            if (peek().isKw("this") && peek(1).is(".")
                    && peek(2).type == JavaToken.Type.IDENT
                    && peek(3).is("=") && !peek(3).is("==")) {
                idx += 2; // this .
                targetName = peek().text;
                idx += 2; // IDENT =
            } else if (peek().type == JavaToken.Type.IDENT
                    && peek(1).is("=") && !peek(1).is("==")) {
                targetName = peek().text;
                idx += 2;
            } else {
                return;
            }
            // 右辺で inline 式を試す
            JavaClassInfo cls = classStack.get(classStack.size() - 1);
            JavaFieldInfo field = findFieldByName(cls, targetName);
            String hint = field != null ? field.getType() : null;
            List<JavaMethodInfo> captured = tryParseInlineExpression(hint, targetName);
            if (captured == null || captured.isEmpty()) {
                idx = save;
                return;
            }
            if (field != null) {
                field.getInlineMethods().addAll(captured);
            } else {
                // フィールドがクラスに見つからない場合は遅延解決のため pendingAssignments
                // に積んでおき、クラス本体パース完了後にマッチさせる
                pendingFieldAssignments.add(new PendingAssignment(cls, targetName, captured));
            }
            // 右辺は inline 部分のみ消費した。文末 `;` は外側で処理。
        }

        /**
         * 呼び出しの最初の引数がラムダ/匿名クラスなら、それを {@code call.inlineMethods}
         * に取り込む。呼び出し直前の {@code (} は既に消費済み。該当しなければ idx を変更しない。
         *
         * <p>メソッド参照 ({@code Foo::bar}) は引数として現れたときは parseExpressionStatement
         * の {@code ::} ハンドラで親メソッドの呼び出しリストに記録するため、ここでは扱わない。</p>
         */
        private void tryCaptureFirstInlineArgument(JavaMethodInfo.Call call) {
            // ラムダ・匿名クラスのみを対象とする (メソッド参照は除外)
            if (!peek().isKw("new") && !looksLikeLambdaStart()) {
                return;
            }
            int save = idx;
            List<JavaMethodInfo> captured = tryParseInlineExpression(null, call.getMethodName());
            if (captured == null || captured.isEmpty()) {
                idx = save;
                return;
            }
            call.getInlineMethods().addAll(captured);
        }

        /**
         * 呼び出しの第 1 引数が定数シンボル参照 (例:
         * {@code VehiclePropertyIds.HVAC_TEMPERATURE_SET},
         * {@code Manifest.permission.READ_PHONE_STATE},
         * 単独 {@code MAX_VALUE}) の場合に、その文字列を
         * {@link JavaMethodInfo.Call#setFirstArgLabel} に格納する。
         *
         * <p>シーケンス図のラベルで {@code getProperty(HVAC_TEMPERATURE_SET)} のように
         * 引数を併記するため。判定基準は「ドット区切りの IDENT 列で、末尾セグメントが
         * UPPERCASE_WITH_UNDERSCORES 形式」。本メソッドは probe-only で idx を進めない
         * (引数本体の消費は呼び出し元の parseExpressionStatement に任せる)。</p>
         */
        private void tryCaptureFirstConstantArgument(JavaMethodInfo.Call call) {
            if (atEnd() || peek().type != JavaToken.Type.IDENT) {
                return;
            }
            StringBuilder sb = new StringBuilder();
            sb.append(peek().text);
            int probe = idx + 1;
            while (probe + 1 < tokens.size()
                    && tokens.get(probe).is(".")
                    && tokens.get(probe + 1).type == JavaToken.Type.IDENT) {
                sb.append('.').append(tokens.get(probe + 1).text);
                probe += 2;
            }
            // 引数の終端: ',' または ')' のいずれかが直後でないと「単一の定数参照」とは
            // 言えない (`FOO + 1` や `FOO.method()` のような複合式を誤検出しないため)
            if (probe >= tokens.size()) {
                return;
            }
            JavaToken nxt = tokens.get(probe);
            if (!nxt.is(",") && !nxt.is(")")) {
                return;
            }
            String full = sb.toString();
            int lastDot = full.lastIndexOf('.');
            String lastSegment = lastDot < 0 ? full : full.substring(lastDot + 1);
            if (!looksLikeConstantSymbol(lastSegment)) {
                return;
            }
            call.setFirstArgLabel(full);
        }

        /**
         * Java の定数命名規約 {@code UPPER_CASE_WITH_UNDERSCORES} に従う識別子か。
         * 大文字始まりで、英大文字・数字・アンダースコアのみで構成され、長さ 2 以上。
         * ({@code F} のような 1 文字は誤検出を避けて除外。{@code PI} は採用。)
         */
        private static boolean looksLikeConstantSymbol(String name) {
            if (name == null || name.length() < 2) {
                return false;
            }
            if (!Character.isUpperCase(name.charAt(0))) {
                return false;
            }
            for (int i = 0; i < name.length(); i++) {
                char c = name.charAt(i);
                if (!Character.isUpperCase(c) && !Character.isDigit(c) && c != '_') {
                    return false;
                }
            }
            return true;
        }

        private static JavaFieldInfo findFieldByName(JavaClassInfo cls, String name) {
            if (cls == null || name == null) {
                return null;
            }
            for (JavaFieldInfo f : cls.getFields()) {
                if (name.equals(f.getName())) {
                    return f;
                }
            }
            return null;
        }

        /**
         * {@code ::} の直前を起点に receiver を組み立てる。
         * {@code Foo::bar} は {@code Foo}、{@code a.b.c::m} は {@code a.b.c}。
         * メソッド呼び出しのレシーバ検出 ({@link #findReceiver()}) は直前が
         * {@code .} の場合だけ拾うが、メソッド参照は識別子自身もレシーバなので
         * 起点が異なる。
         */
        private String findReceiverBeforeColonColon() {
            int i = idx - 1;
            StringBuilder sb = new StringBuilder();
            int j = i;
            while (j >= 0) {
                JavaToken t = tokens.get(j);
                if (t.is(".")) {
                    sb.insert(0, '.');
                    j--;
                    continue;
                }
                if (t.type == JavaToken.Type.IDENT) {
                    sb.insert(0, t.text);
                    if (j - 1 >= 0 && tokens.get(j - 1).is(".")) {
                        j--;
                        continue;
                    }
                    break;
                }
                break;
            }
            return sb.toString();
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
            // @Foo / @Foo.Bar / @Foo(args) を取り除く。引数部分はネストした括弧や
            // 文字列リテラル (@Foo(bar = @Baz("x"))) を考慮して手動で対応する括弧
            // までスキップする。正規表現の [^)]* だと最初の ) で止まってしまうため。
            if (s == null || s.indexOf('@') < 0) {
                return s == null ? null : s.trim();
            }
            StringBuilder out = new StringBuilder(s.length());
            int i = 0;
            int n = s.length();
            while (i < n) {
                char c = s.charAt(i);
                if (c == '@' && i + 1 < n
                        && (Character.isJavaIdentifierStart(s.charAt(i + 1)))) {
                    // @ + 識別子 (a.b.c)
                    i++;
                    while (i < n) {
                        char ic = s.charAt(i);
                        if (Character.isJavaIdentifierPart(ic) || ic == '.') {
                            i++;
                        } else {
                            break;
                        }
                    }
                    // 引数 (...) はネスト・文字列対応でスキップ
                    if (i < n && s.charAt(i) == '(') {
                        i = skipBalancedParens(s, i, n);
                    }
                    out.append(' ');
                    continue;
                }
                out.append(c);
                i++;
            }
            return out.toString().trim();
        }

        /**
         * {@code s[from]} の {@code (} に対応する {@code )} の次のインデックスを返す。
         * 文字列リテラルとネストを考慮する。
         */
        private static int skipBalancedParens(String s, int from, int n) {
            int depth = 0;
            int i = from;
            while (i < n) {
                char c = s.charAt(i);
                if (c == '"' || c == '\'') {
                    char quote = c;
                    i++;
                    while (i < n && s.charAt(i) != quote) {
                        if (s.charAt(i) == '\\' && i + 1 < n) {
                            i += 2;
                        } else {
                            i++;
                        }
                    }
                    if (i < n) {
                        i++;
                    }
                    continue;
                }
                if (c == '(') {
                    depth++;
                } else if (c == ')') {
                    depth--;
                    if (depth == 0) {
                        return i + 1;
                    }
                }
                i++;
            }
            return i;
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

        /**
         * import 宣言の本体 ({@code foo.bar.Baz} もしくは {@code foo.bar.*}) を読み取る。
         * 末尾の {@code ;} は呼び出し側で消費する。
         */
        private String readImportName() {
            StringBuilder sb = new StringBuilder();
            while (!atEnd() && peek().type == JavaToken.Type.IDENT) {
                sb.append(next().text);
                if (peek().is(".")) {
                    next();
                    sb.append('.');
                    if (peek().is("*")) {
                        next();
                        sb.append('*');
                        break;
                    }
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
