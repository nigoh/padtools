package padtools.core.formats.java;

import padtools.util.ErrorListener;

import java.util.List;
import java.util.Set;

/**
 * Android/通常の Java ソースコードを PadTools の SPD 形式テキストに変換するファサード。
 *
 * <p>軽量な字句解析器 ({@link JavaLexer}) と構文解析器 ({@link JavaParser}) を内部的に呼び出して
 * SPD テキストを生成する。完全な Java コンパイラではないが、一般的な制御構造
 * (if/else, while, do-while, for, switch, try-catch, return, throw, break, continue)
 * を PAD コマンドに変換する。</p>
 *
 * <p>主な利用方法:</p>
 * <pre>{@code
 *   String spd = JavaSourceConverter.convert(javaSource);
 *   // または:
 *   JavaSourceConverter.Options opt = new JavaSourceConverter.Options();
 *   opt.splitByMethod = false;
 *   String spd = JavaSourceConverter.convert(javaSource, opt);
 * }</pre>
 */
public final class JavaSourceConverter {

    /** 変換オプション。 */
    public static class Options {
        /** true: 各メソッドを独立した PAD ブロックとして出力 (デフォルト). false: クラス単位に統合。 */
        public boolean splitByMethod = true;
        /** メソッドの開始/終了に :terminal を付与する。 */
        public boolean includeTerminals = true;
        /** return/throw を :terminal として出力する。false の場合は通常処理ノード。 */
        public boolean returnAsTerminal = true;
        /** for 文を init/while/update に展開する。false の場合 :while で 1 つにまとめる。 */
        public boolean unrollFor = true;
        /** クラス名修飾区切り文字 (例: "Foo.bar")。 */
        public String classSeparator = ".";
        /** 出力対象とするメソッド名フィルタ。null または空なら全メソッド。 */
        public Set<String> methodFilter = null;
        /** 出力先頭に凡例 (各 PAD ノード形状の例) を付与する。 */
        public boolean includeLegend = false;
    }

    /** デフォルト Options で変換。 */
    public static String convert(String javaSource) {
        return convert(javaSource, null, null);
    }

    /** オプション付き変換。 */
    public static String convert(String javaSource, Options opts) {
        return convert(javaSource, opts, null);
    }

    /**
     * オプション + エラーリスナー付き変換。
     * パース不能なトークンやスキップ箇所があれば {@code listener} に通知する。
     */
    public static String convert(String javaSource, Options opts, ErrorListener listener) {
        if (javaSource == null) {
            throw new IllegalArgumentException("javaSource is null");
        }
        Options o = (opts == null) ? new Options() : opts;
        ErrorListener l = listener != null ? listener : ErrorListener.silent();
        JavaLexer lex = new JavaLexer(javaSource);
        List<JavaToken> tokens = lex.tokenize();
        JavaParser p = new JavaParser(tokens, javaSource, o, l);
        p.parseFile();
        String body = p.result();
        if (o.includeLegend) {
            return buildLegend() + (body.isEmpty() ? "" : "\n" + body);
        }
        return body;
    }

    /**
     * PAD ノード形状の凡例 SPD を返す。SPD のコメント + 実ノードで描画される。
     */
    private static String buildLegend() {
        StringBuilder sb = new StringBuilder();
        sb.append("# === Legend (凡例) ===\n");
        sb.append(":terminal 凡例: 開始/終了端子 (:terminal)\n");
        sb.append("通常処理ノード (テキスト行)\n");
        sb.append(":call サブルーチン呼出 (:call)\n");
        sb.append(":comment コメントボックス (:comment)\n");
        sb.append(":if 条件分岐 (:if / :else)\n");
        sb.append("\ttrue 処理\n");
        sb.append(":else\n");
        sb.append("\tfalse 処理\n");
        sb.append(":while 前判定ループ (:while)\n");
        sb.append("\tループ本体\n");
        sb.append(":dowhile 後判定ループ (:dowhile)\n");
        sb.append("\tループ本体\n");
        sb.append(":switch 多分岐 (:switch / :case)\n");
        sb.append(":case ケース A\n");
        sb.append("\tケースA 処理\n");
        sb.append(":case ケース B\n");
        sb.append("\tケースB 処理\n");
        sb.append(":terminal 凡例ここまで\n");
        return sb.toString();
    }

    private JavaSourceConverter() {
        // ユーティリティクラス
    }
}
