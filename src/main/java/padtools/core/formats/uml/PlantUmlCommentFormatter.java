package padtools.core.formats.uml;

/**
 * PlantUML 図のコメント/ノート整形を担う共有ユーティリティ。
 *
 * <p>クラス図・シーケンス図など複数の生成器で重複していたインラインコメントの無害化・
 * 単語境界での折り返し・note 本文の整形を 1 箇所に集約する。状態を持たない純粋関数のみ。</p>
 */
final class PlantUmlCommentFormatter {

    private PlantUmlCommentFormatter() {
    }

    /**
     * PlantUML の {@code ..} セパレータと干渉する文字を抑止し、長さも制限する。
     */
    static String sanitizeInlineComment(String s, int maxLen) {
        // PlantUML の class body 内でレイアウトを乱す制御文字を除去
        String t = s.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ').trim();
        // 末尾の '..' は区切りと干渉するためスペースに置換
        t = t.replaceAll("\\.\\.+$", ".");
        if (maxLen > 0 && t.length() > maxLen) {
            t = t.substring(0, Math.max(1, maxLen - 1)) + "…";
        }
        return t;
    }

    /**
     * テキストを単語境界（スペース）で折り返し、maxLen 文字以内の行に分割する。
     * スペースが見つからない場合は maxLen 文字でハードブレークする。
     */
    static String wordWrap(String s, int maxLen) {
        if (maxLen <= 0 || s == null || s.length() <= maxLen) {
            return s;
        }
        StringBuilder sb = new StringBuilder();
        int start = 0;
        while (start < s.length()) {
            if (s.length() - start <= maxLen) {
                sb.append(s, start, s.length());
                break;
            }
            int end = start + maxLen;
            int breakAt = s.lastIndexOf(' ', end);
            if (breakAt <= start) {
                // スペースが見つからないためハードブレーク
                breakAt = end;
                sb.append(s, start, breakAt).append('\n');
                start = breakAt;
            } else {
                sb.append(s, start, breakAt).append('\n');
                start = breakAt + 1; // スペースをスキップ
            }
        }
        return sb.toString();
    }

    /** note ブロックの本文を 1 行ずつ書き出す。maxLen &gt; 0 のとき wordWrap を適用。 */
    static void appendNoteBody(StringBuilder out, String comment, String indent, int maxLen) {
        String[] lines = comment.split("\n", -1);
        for (String line : lines) {
            String t = line.replace('\r', ' ').replace('\t', ' ').trim();
            if (t.isEmpty()) {
                continue;
            }
            String wrapped = wordWrap(t, maxLen);
            for (String wl : wrapped.split("\n", -1)) {
                if (!wl.isEmpty()) {
                    out.append(indent).append("  ").append(wl).append('\n');
                }
            }
        }
    }
}
