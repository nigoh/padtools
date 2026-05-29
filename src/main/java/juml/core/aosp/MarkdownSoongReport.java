// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.aosp;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * {@link AndroidBpModule} のリストを Markdown レポートに整形する。
 *
 * <p>章構成:</p>
 * <ol>
 *   <li>サマリー (モジュール数 / カテゴリ別件数)</li>
 *   <li>カテゴリ別モジュール一覧 (name / type / 依存数 / srcs 数 / 場所)</li>
 *   <li>外部依存 (このプロジェクト内で宣言されていないモジュール名) のランキング</li>
 * </ol>
 */
public final class MarkdownSoongReport {

    private MarkdownSoongReport() {
    }

    public static String render(List<AndroidBpModule> modules) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Soong (Android.bp) Module Report\n\n");
        if (modules == null || modules.isEmpty()) {
            sb.append("(no Android.bp modules found)\n");
            return sb.toString();
        }

        // カテゴリ別集計
        Map<String, List<AndroidBpModule>> byCategory = new LinkedHashMap<>();
        java.util.Set<String> localNames = new java.util.LinkedHashSet<>();
        for (AndroidBpModule m : modules) {
            byCategory.computeIfAbsent(m.getCategory(), k -> new ArrayList<>()).add(m);
            if (!m.getName().isEmpty()) localNames.add(m.getName());
        }

        sb.append("- Total modules: ").append(modules.size()).append('\n');
        for (Map.Entry<String, List<AndroidBpModule>> e : byCategory.entrySet()) {
            sb.append("- ").append(e.getKey()).append(": ")
                    .append(e.getValue().size()).append('\n');
        }
        sb.append('\n');

        for (Map.Entry<String, List<AndroidBpModule>> e : byCategory.entrySet()) {
            sb.append("## ").append(e.getKey()).append(" modules\n\n");
            sb.append("| Name | Type | Deps | Srcs | Location |\n");
            sb.append("|---|---|---|---|---|\n");
            List<AndroidBpModule> sorted = new ArrayList<>(e.getValue());
            sorted.sort(Comparator.comparing(AndroidBpModule::getName));
            for (AndroidBpModule m : sorted) {
                String loc = m.getFile().isEmpty() ? ""
                        : "`" + m.getFile() + ":" + m.getLineHint() + "`";
                sb.append("| `").append(m.getName()).append("` | `")
                        .append(m.getType()).append("` | ")
                        .append(m.getDeps().size()).append(" | ")
                        .append(m.getSrcs().size()).append(" | ")
                        .append(loc).append(" |\n");
            }
            sb.append('\n');
        }

        // 外部依存ランキング (このプロジェクトに宣言されてない deps)
        Map<String, Integer> external = new TreeMap<>();
        for (AndroidBpModule m : modules) {
            for (String dep : m.getDeps()) {
                if (!localNames.contains(dep)) {
                    external.merge(dep, 1, Integer::sum);
                }
            }
        }
        if (!external.isEmpty()) {
            sb.append("## External dependencies (most-referenced)\n\n");
            sb.append("| Dependency | Reference count |\n");
            sb.append("|---|---|\n");
            List<Map.Entry<String, Integer>> rank = new ArrayList<>(external.entrySet());
            rank.sort((a, b) -> {
                int c = Integer.compare(b.getValue(), a.getValue());
                if (c != 0) return c;
                return a.getKey().compareTo(b.getKey());
            });
            int limit = Math.min(rank.size(), 50);
            for (int i = 0; i < limit; i++) {
                sb.append("| `").append(rank.get(i).getKey()).append("` | ")
                        .append(rank.get(i).getValue()).append(" |\n");
            }
            if (rank.size() > limit) {
                sb.append("\n_(+").append(rank.size() - limit).append(" more)_\n");
            }
        }
        return sb.toString();
    }
}
