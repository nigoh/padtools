// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.screen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link ScreenTransition} のリストを Markdown レポートに整形する。
 *
 * <p>章構成:</p>
 * <ol>
 *   <li>サマリー (Activity 数、遷移エッジ数)</li>
 *   <li>Activity ごとの入出力 (どこから来てどこへ行くか)</li>
 *   <li>遷移エッジ詳細 (caller / target / kind / file:line)</li>
 * </ol>
 */
public final class MarkdownScreenFlowReport {

    private MarkdownScreenFlowReport() {
    }

    public static String render(List<ScreenTransition> transitions) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Screen Flow Report (Intent + Screen.push)\n\n");
        if (transitions == null || transitions.isEmpty()) {
            sb.append("(no screen transitions detected)\n");
            return sb.toString();
        }

        // ノード集合 + 入出力
        Map<String, Set<String>> outgoing = new LinkedHashMap<>();
        Map<String, Set<String>> incoming = new LinkedHashMap<>();
        Set<String> activities = new LinkedHashSet<>();
        for (ScreenTransition t : transitions) {
            String from = t.getFromSimpleName();
            String to = t.getTargetSimpleName();
            if (!from.isEmpty()) activities.add(from);
            if (!to.isEmpty()) activities.add(to);
            if (!from.isEmpty() && !to.isEmpty()) {
                outgoing.computeIfAbsent(from, k -> new LinkedHashSet<>()).add(to);
                incoming.computeIfAbsent(to, k -> new LinkedHashSet<>()).add(from);
            }
        }

        sb.append("- Activities involved: ").append(activities.size()).append('\n');
        sb.append("- Transitions: ").append(transitions.size()).append('\n');
        sb.append('\n');

        sb.append("## Activities\n\n");
        sb.append("| Activity | Outgoing | Incoming |\n");
        sb.append("|---|---|---|\n");
        List<String> sorted = new ArrayList<>(activities);
        sorted.sort(Comparator.naturalOrder());
        for (String a : sorted) {
            String out = joinOrDash(outgoing.get(a));
            String in = joinOrDash(incoming.get(a));
            sb.append("| `").append(a).append("` | ").append(out).append(" | ")
                    .append(in).append(" |\n");
        }
        sb.append('\n');

        sb.append("## Transitions\n\n");
        sb.append("| From | Method | Kind | To | Location |\n");
        sb.append("|---|---|---|---|---|\n");
        for (ScreenTransition t : transitions) {
            String loc = t.getFile().isEmpty() ? ""
                    : "`" + t.getFile() + ":" + t.getLineHint() + "`";
            sb.append("| `").append(t.getFromSimpleName()).append("` | ")
                    .append(t.getFromMethod().isEmpty() ? "—"
                            : "`" + t.getFromMethod() + "`")
                    .append(" | ").append(t.getKind().name())
                    .append(" | `").append(t.getTargetSimpleName()).append("`")
                    .append(" | ").append(loc).append(" |\n");
        }
        appendRoutes(sb, transitions);
        return sb.toString();
    }

    /** 起点からの多段遷移ルートを列挙して追記する。 */
    private static void appendRoutes(StringBuilder sb, List<ScreenTransition> transitions) {
        List<List<String>> routes = ScreenRouteBuilder.routes(transitions);
        sb.append("\n## Routes (entry → ...)\n\n");
        if (routes.isEmpty()) {
            sb.append("(no multi-step routes; transitions form isolated edges or cycles)\n");
            return;
        }
        for (List<String> route : routes) {
            sb.append("- ").append(String.join(" → ", route)).append('\n');
        }
    }

    private static String joinOrDash(Set<String> set) {
        if (set == null || set.isEmpty()) return "—";
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (String s : set) {
            if (i > 0) sb.append(", ");
            sb.append('`').append(s).append('`');
            i++;
        }
        return sb.toString();
    }
}
