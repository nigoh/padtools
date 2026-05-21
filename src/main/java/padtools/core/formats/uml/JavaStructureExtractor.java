package padtools.core.formats.uml;

import padtools.core.formats.java.JavaLexer;
import padtools.core.formats.java.JavaToken;
import padtools.util.ErrorListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Java ソースから {@link JavaClassInfo} のリストを抽出するパーサのファサード。
 *
 * <p>{@link JavaLexer} で得たトークン列を走査し、クラス/インタフェース/enum/@interface/
 * その内部のフィールド・メソッド・コンストラクタを構造化データとして返す。
 * メソッド本体内の呼び出しも {@link JavaMethodInfo#getCalls()} に記録するため、
 * シーケンス図生成に利用できる。</p>
 *
 * <p>実装は共有状態 {@link ParserState} と、宣言・文・式の各 {@code Parser}
 * ({@link DeclarationParser} / {@link StatementParser} / {@link ExpressionParser})
 * に分割されている。本クラスはそれらを配線して起動する入口のみを担う。</p>
 */
public final class JavaStructureExtractor {

    /** Java ソースから ClassInfo のリストを返す。 */
    public static List<JavaClassInfo> extract(String source) {
        return extract(source, null);
    }

    /**
     * 使用する Java パーサー実装。
     * {@code -Dpadtools.java.parser=javaparser} で JavaParser フロントエンドに切り替わる。
     * 既定は従来の手書きパーサー ({@code legacy})。
     */
    private static boolean useJavaParser() {
        return !"legacy".equalsIgnoreCase(
                System.getProperty("padtools.java.parser", "javaparser"));
    }

    /** エラーリスナー付き。 */
    public static List<JavaClassInfo> extract(String source, ErrorListener listener) {
        return extract(source, listener, null);
    }

    /**
     * {@code solver} を渡すと (JavaParser 実装時のみ) 呼び出し先をシンボル解決して
     * {@code Call.resolvedOwnerFqn} を埋める。legacy 実装では solver は無視される。
     */
    public static List<JavaClassInfo> extract(String source, ErrorListener listener,
            padtools.core.formats.java.jp.JpSolver solver) {
        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }
        if (useJavaParser()) {
            return padtools.core.formats.java.jp.JavaParserFrontend.parse(
                    source, false, listener != null ? listener : ErrorListener.silent(), solver);
        }
        // Java の Unicode エスケープは字句解析の前に展開される (JLS 3.3)。
        // 識別子・キーワードを含むエスケープを正しく扱うため、入口で 1 度だけ展開する。
        String expanded = JavaLexer.expandUnicodeEscapes(source);
        List<JavaToken> tokens = new JavaLexer(expanded).tokenize();
        List<JavaCommentScanner.Comment> comments = JavaCommentScanner.scan(expanded);
        ParserState state = new ParserState(tokens, expanded, comments,
                listener != null ? listener : ErrorListener.silent());
        DeclarationParser decl = new DeclarationParser(state);
        StatementParser stmt = new StatementParser(state);
        ExpressionParser expr = new ExpressionParser(state);
        state.decl = decl;
        state.stmt = stmt;
        state.expr = expr;
        decl.parseFile();
        return Collections.unmodifiableList(state.results);
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
}
