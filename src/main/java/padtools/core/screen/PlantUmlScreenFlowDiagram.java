// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package padtools.core.screen;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link ScreenTransition} のリストを PlantUML 状態遷移図に整形する。
 *
 * <p>各 Activity を状態ノードとして描画し、遷移種別に応じた矢印で接続する:</p>
 * <ul>
 *   <li>{@link ScreenTransition.Kind#START_ACTIVITY}: 通常矢印</li>
 *   <li>{@link ScreenTransition.Kind#START_FOR_RESULT}: 双方向矢印 (結果受け取り)</li>
 *   <li>{@link ScreenTransition.Kind#SET_CLASS}: 破線</li>
 * </ul>
 */
public final class PlantUmlScreenFlowDiagram {

    private PlantUmlScreenFlowDiagram() {
    }

    public static String render(List<ScreenTransition> transitions) {
        StringBuilder sb = new StringBuilder();
        sb.append("@startuml\n");
        sb.append("title Screen Flow (Intent + Screen.push)\n");
        sb.append("skinparam shadowing false\n");
        sb.append("skinparam state {\n");
        sb.append("  BackgroundColor #F0F8FF\n");
        sb.append("  BorderColor #3070A0\n");
        sb.append("}\n");

        // ノード集合: from + target すべて
        Set<String> nodes = new LinkedHashSet<>();
        for (ScreenTransition t : transitions) {
            if (!t.getFromSimpleName().isEmpty()) nodes.add(t.getFromSimpleName());
            if (!t.getTargetSimpleName().isEmpty()) nodes.add(t.getTargetSimpleName());
        }
        for (String n : nodes) {
            sb.append("state \"").append(escape(n)).append("\" as ").append(alias(n))
                    .append('\n');
        }

        // 重複エッジを集約
        Map<String, EdgeAgg> edges = new LinkedHashMap<>();
        for (ScreenTransition t : transitions) {
            String fromS = t.getFromSimpleName();
            String toS = t.getTargetSimpleName();
            if (fromS.isEmpty() || toS.isEmpty()) continue;
            String key = fromS + "->" + toS + "/" + t.getKind();
            EdgeAgg agg = edges.computeIfAbsent(key,
                    k -> new EdgeAgg(fromS, toS, t.getKind()));
            agg.count++;
            if (!t.getFromMethod().isEmpty()) {
                agg.methods.add(t.getFromMethod());
            }
        }
        for (EdgeAgg e : edges.values()) {
            String arrow = arrowFor(e.kind);
            String label = e.kind.name();
            if (e.count > 1) label += " x" + e.count;
            if (!e.methods.isEmpty()) {
                String head = e.methods.iterator().next();
                label = head + "()\\n" + label;
            }
            sb.append(alias(e.from)).append(' ').append(arrow).append(' ')
                    .append(alias(e.to)).append(" : ").append(label).append('\n');
        }
        sb.append("@enduml\n");
        return sb.toString();
    }

    // State 図で有効な矢印のみ使う（双方向 <--> や ..> は state 図では構文エラーになる）。
    private static String arrowFor(ScreenTransition.Kind kind) {
        switch (kind) {
            case START_FOR_RESULT: return "-[#1f6fb0]->";
            case SET_CLASS: return "-[#888888,dashed]->";
            case SCREEN_PUSH: return "-[#2e8b57]->";
            case FRAGMENT_TXN: return "-[#b8860b]->";
            case NAV_ACTION: return "-[#8a2be2]->";
            case COMPOSE_NAVIGATE: return "-[#d2691e]->";
            default: return "-->";
        }
    }

    private static String alias(String id) {
        StringBuilder sb = new StringBuilder("s_");
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            sb.append(Character.isLetterOrDigit(c) ? c : '_');
        }
        return sb.toString();
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\"", "\\\"");
    }

    private static final class EdgeAgg {
        final String from;
        final String to;
        final ScreenTransition.Kind kind;
        int count = 0;
        final Set<String> methods = new LinkedHashSet<>();

        EdgeAgg(String from, String to, ScreenTransition.Kind kind) {
            this.from = from;
            this.to = to;
            this.kind = kind;
        }
    }
}
