package padtools.core.funcdiff;

import padtools.core.formats.uml.JavaMethodInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@link MethodDiffAnalyzer.DiffResult} を Markdown レポート文字列に整形する。
 */
public final class MarkdownMethodDiffReport {

    private MarkdownMethodDiffReport() {
    }

    public static String render(MethodDiffAnalyzer.DiffResult result) {
        if (result == null) {
            return "# Function Diff Report\n\n(no data)\n";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("# Function Diff Report\n\n");
        appendSummary(sb, result);
        appendCallComparison(sb, result);
        appendDiffDetail(sb, result);
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Summary
    // -------------------------------------------------------------------------

    private static void appendSummary(StringBuilder sb, MethodDiffAnalyzer.DiffResult r) {
        sb.append("## Summary\n\n");
        sb.append("| Item | Value |\n");
        sb.append("|---|---|\n");
        sb.append("| Method A | `").append(r.specA.filePath)
          .append("` :: `").append(r.specA.label()).append("()` |\n");
        sb.append("| Method B | `").append(r.specB.filePath)
          .append("` :: `").append(r.specB.label()).append("()` |\n");
        sb.append("| Total calls (A / B) | ").append(r.totalCallsA)
          .append(" / ").append(r.totalCallsB).append(" |\n");

        int maxLen = Math.max(r.totalCallsA, r.totalCallsB);
        MethodDiffAnalyzer.SimilarityMetrics m = r.metrics;

        sb.append("| **LCS Similarity** | **")
          .append(fmt2(m.lcsSimilarity))
          .append("** (LCS_length=").append(m.lcsLen)
          .append(" / max(").append(r.totalCallsA).append(',').append(r.totalCallsB)
          .append(")=").append(maxLen).append(") |\n");

        sb.append("| **Edit Distance** | **")
          .append(m.editDistance)
          .append("** — Normalized similarity: ")
          .append(fmt2(m.normalizedEditSimilarity))
          .append(" (1 − ").append(m.editDistance).append('/').append(maxLen)
          .append(") |\n");

        sb.append("| **Jaccard Coefficient** | **")
          .append(fmt2(m.jaccard))
          .append("** (|A∩B| / |A∪B|) |\n");

        if (r.matchCount + r.partialCount > 0) {
            sb.append("| **Avg. Confidence** | **")
              .append(fmt2(r.avgConfidence))
              .append("** (matched pairs only) |\n");
        }

        sb.append("\n### Formula Reference\n\n");
        sb.append("- LCS Similarity = LCS_length / max(|A|, |B|)"
                + " — 順序保持の共通部分割合\n");
        sb.append("- Edit Distance (Levenshtein)"
                + " = min(insertions + deletions + substitutions)\n");
        sb.append("- Normalized Edit Similarity"
                + " = 1 − edit_distance / max(|A|, |B|)\n");
        sb.append("- Jaccard = |A∩B| / |A∪B|"
                + " — 集合ベース（順序無視・重複排除）\n");
        sb.append("- Confidence = receiver\\_score×0.4"
                + " + firstArg\\_score×0.3 + position\\_score×0.3\n");
        sb.append('\n');
    }

    // -------------------------------------------------------------------------
    // Call Comparison table
    // -------------------------------------------------------------------------

    private static void appendCallComparison(StringBuilder sb,
                                              MethodDiffAnalyzer.DiffResult r) {
        sb.append("## Call Comparison\n\n");
        sb.append("| # | Status | Method A call | Method B call | Confidence | Notes |\n");
        sb.append("|---|---|---|---|---|---|\n");

        int seq = 1;
        for (MethodDiffAnalyzer.DiffRow row : r.rows) {
            String callAStr = row.callA != null ? callLabel(row.callA) : "—";
            String callBStr = row.callB != null ? callLabel(row.callB) : "—";
            String confStr = row.confidence >= 0 ? fmt2(row.confidence) : "—";
            String notes = row.detail != null ? escapePipe(row.detail) : "";

            sb.append("| ").append(seq++).append(" | ")
              .append(row.kind).append(" | `")
              .append(callAStr).append("` | `")
              .append(callBStr).append("` | ")
              .append(confStr).append(" | ")
              .append(notes).append(" |\n");
        }
        sb.append('\n');
    }

    // -------------------------------------------------------------------------
    // Diff Detail
    // -------------------------------------------------------------------------

    private static void appendDiffDetail(StringBuilder sb, MethodDiffAnalyzer.DiffResult r) {
        List<MethodDiffAnalyzer.DiffRow> onlyA = new ArrayList<>();
        List<MethodDiffAnalyzer.DiffRow> onlyB = new ArrayList<>();
        List<MethodDiffAnalyzer.DiffRow> partial = new ArrayList<>();

        for (MethodDiffAnalyzer.DiffRow row : r.rows) {
            switch (row.kind) {
                case ONLY_A:  onlyA.add(row);   break;
                case ONLY_B:  onlyB.add(row);   break;
                case PARTIAL: partial.add(row);  break;
                default: break;
            }
        }

        if (onlyA.isEmpty() && onlyB.isEmpty() && partial.isEmpty()) {
            sb.append("## Diff Detail\n\nNo differences found.\n");
            return;
        }

        sb.append("## Diff Detail\n\n");

        if (!onlyA.isEmpty()) {
            sb.append("### Only in A\n\n");
            for (MethodDiffAnalyzer.DiffRow row : onlyA) {
                sb.append("- `").append(callLabel(row.callA)).append('`');
                if (row.callA.getReceiver() != null) {
                    sb.append(" — receiver: `").append(row.callA.getReceiver()).append('`');
                }
                sb.append('\n');
            }
            sb.append('\n');
        }

        if (!onlyB.isEmpty()) {
            sb.append("### Only in B\n\n");
            for (MethodDiffAnalyzer.DiffRow row : onlyB) {
                sb.append("- `").append(callLabel(row.callB)).append('`');
                if (row.callB.getReceiver() != null) {
                    sb.append(" — receiver: `").append(row.callB.getReceiver()).append('`');
                }
                sb.append('\n');
            }
            sb.append('\n');
        }

        if (!partial.isEmpty()) {
            sb.append("### Partial Match\n\n");
            for (MethodDiffAnalyzer.DiffRow row : partial) {
                sb.append("- `").append(row.callA.getMethodName()).append("()`");
                if (row.detail != null) {
                    sb.append(" — ").append(row.detail);
                }
                sb.append('\n');
            }
            sb.append('\n');
        }
    }

    // -------------------------------------------------------------------------
    // ヘルパー
    // -------------------------------------------------------------------------

    private static String callLabel(JavaMethodInfo.Call c) {
        String base = c.getReceiver() != null
                ? c.getReceiver() + "." + c.getMethodName() + "()"
                : c.getMethodName() + "()";
        if (c.getFirstArgLabel() != null) {
            base = base.replace("()", "(" + c.getFirstArgLabel() + ")");
        }
        return base;
    }

    private static String fmt2(double v) {
        return String.format(Locale.ROOT, "%.3f", v);
    }

    private static String escapePipe(String s) {
        return s.replace("|", "\\|");
    }
}
