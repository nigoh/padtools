package padtools.core.aosp;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link AndroidBpModule} のリストを PlantUML 依存図に整形する。
 *
 * <p>各モジュールをコンポーネントとして描画し、依存先 (libs/static_libs/etc.) を矢印で接続。
 * 同じプロジェクト内に宣言されたモジュールは category ({@code cc/java/android/aidl/hidl/build/other})
 * 単位で package グループ化される。プロジェクト外のモジュール (依存名のみ判明) は
 * {@code external} グループにまとめる。</p>
 */
public final class PlantUmlSoongDependencyDiagram {

    private PlantUmlSoongDependencyDiagram() {
    }

    public static String render(List<AndroidBpModule> modules) {
        StringBuilder sb = new StringBuilder();
        sb.append("@startuml\n");
        sb.append("title Soong (Android.bp) Module Dependencies\n");
        sb.append("skinparam componentStyle rectangle\n");
        sb.append("skinparam shadowing false\n");

        // name → module 索引
        Map<String, AndroidBpModule> byName = new LinkedHashMap<>();
        for (AndroidBpModule m : modules) {
            if (!m.getName().isEmpty()) {
                byName.put(m.getName(), m);
            }
        }

        // category 別 package グループ
        Map<String, List<AndroidBpModule>> byCategory = new LinkedHashMap<>();
        for (AndroidBpModule m : modules) {
            byCategory.computeIfAbsent(m.getCategory(), k -> new java.util.ArrayList<>())
                    .add(m);
        }
        for (Map.Entry<String, List<AndroidBpModule>> e : byCategory.entrySet()) {
            sb.append("package \"").append(e.getKey()).append("\" {\n");
            for (AndroidBpModule m : e.getValue()) {
                sb.append("  component \"").append(escape(m.getName())).append("\\n<<")
                        .append(m.getType()).append(">>\" as ")
                        .append(alias(m.getName())).append(' ')
                        .append(colorFor(m.getCategory())).append('\n');
            }
            sb.append("}\n");
        }

        // 外部参照 (依存名でしか出てこないモジュール) を集める
        Set<String> external = new LinkedHashSet<>();
        for (AndroidBpModule m : modules) {
            for (String dep : m.getDeps()) {
                if (!byName.containsKey(dep)) external.add(dep);
            }
        }
        if (!external.isEmpty()) {
            sb.append("package \"external\" #DDDDDD {\n");
            for (String name : external) {
                sb.append("  component \"").append(escape(name))
                        .append("\" as ").append(alias(name)).append(" #EEEEEE\n");
            }
            sb.append("}\n");
        }

        // edges (重複排除)
        Set<String> emitted = new LinkedHashSet<>();
        Map<String, Integer> edgeCount = new HashMap<>();
        for (AndroidBpModule m : modules) {
            for (String dep : m.getDeps()) {
                String key = m.getName() + "->" + dep;
                edgeCount.merge(key, 1, Integer::sum);
            }
        }
        for (AndroidBpModule m : modules) {
            for (String dep : m.getDeps()) {
                String key = m.getName() + "->" + dep;
                if (!emitted.add(key)) continue;
                int n = edgeCount.getOrDefault(key, 1);
                sb.append(alias(m.getName())).append(" --> ").append(alias(dep));
                if (n > 1) sb.append(" : x").append(n);
                sb.append('\n');
            }
        }
        sb.append("@enduml\n");
        return sb.toString();
    }

    private static String colorFor(String category) {
        switch (category) {
            case "cc": return "#D7F0FF";
            case "java": return "#FFE8C8";
            case "android": return "#D5F5D0";
            case "aidl": return "#FFD5E8";
            case "hidl": return "#FFE0E0";
            case "build": return "#EEEEEE";
            default: return "#FFFFFF";
        }
    }

    private static String alias(String name) {
        StringBuilder sb = new StringBuilder("m_");
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            sb.append(Character.isLetterOrDigit(c) ? c : '_');
        }
        return sb.toString();
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\"", "\\\"");
    }
}
