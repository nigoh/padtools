package padtools.core.formats.android;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Gradle のモジュール依存と外部ライブラリ依存を PlantUML コンポーネント図として生成する。
 */
public final class PlantUmlGradleDependencyGraph {

    /** 出力オプション。 */
    public static class Options {
        public boolean includeLegend = true;
        public boolean includeTestScopes = false;
        public boolean includeExternalLibs = true;
        public String title;
    }

    /** デフォルト Options で生成。 */
    public static String generate(AndroidProjectAnalysis analysis) {
        return generate(analysis, null);
    }

    /** オプション付き生成。 */
    public static String generate(AndroidProjectAnalysis analysis, Options opts) {
        if (analysis == null) {
            throw new IllegalArgumentException("analysis is null");
        }
        Options o = opts != null ? opts : new Options();
        StringBuilder out = new StringBuilder();
        out.append("@startuml\n");
        if (o.title != null && !o.title.isEmpty()) {
            out.append("title ").append(o.title).append('\n');
        }
        // 同梱 PlantUML の Smetana レイアウトは孤立ノード (in/out エッジを 1 本も
        // 持たないコンポーネント) を含むグラフで qsort 内 IllegalStateException を
        // 起こすことがある。集約用ルートプロジェクト (`:root` 等) や依存を持たない
        // モジュールは情報量も小さいため、出力前に除外する。
        Set<String> connectedModules = collectConnectedModules(analysis, o);
        Map<String, String> moduleAlias = new LinkedHashMap<>();
        int seq = 0;
        // モジュールノード
        for (String module : analysis.getGradleByModule().keySet()) {
            if (!connectedModules.contains(module)) {
                continue;
            }
            String alias = "M" + (seq++);
            moduleAlias.put(module, alias);
            GradleProjectInfo info = analysis.getGradleByModule().get(module);
            String stereo;
            if (info.isAndroidApplication()) {
                stereo = " <<application>>";
            } else if (info.isAndroidLibrary()) {
                stereo = " <<library>>";
            } else {
                stereo = " <<module>>";
            }
            out.append("component \"").append(module).append("\" as ")
                    .append(alias).append(stereo).append('\n');
        }
        // 外部ライブラリノード
        Map<String, String> libAlias = new LinkedHashMap<>();
        if (o.includeExternalLibs) {
            Set<String> libs = new TreeSet<>();
            for (GradleProjectInfo info : analysis.getGradleByModule().values()) {
                for (GradleDependency d : info.getDependencies()) {
                    if (d.isModuleReference()) {
                        continue;
                    }
                    if (!o.includeTestScopes && isTestScope(d.getScope())) {
                        continue;
                    }
                    if (d.getGroup() != null && d.getName() != null) {
                        libs.add(d.getGroup() + ":" + d.getName());
                    } else if (d.getName() != null) {
                        libs.add(d.getName());
                    }
                }
            }
            for (String lib : libs) {
                String alias = "L" + (seq++);
                libAlias.put(lib, alias);
                out.append("component \"").append(lib).append("\" as ").append(alias)
                        .append(" <<external>>\n");
            }
        }
        // エッジ
        for (Map.Entry<String, GradleProjectInfo> e
                : analysis.getGradleByModule().entrySet()) {
            String from = moduleAlias.get(e.getKey());
            if (from == null) {
                continue;
            }
            for (GradleDependency d : e.getValue().getDependencies()) {
                if (!o.includeTestScopes && isTestScope(d.getScope())) {
                    continue;
                }
                String arrow = arrowForScope(d.getScope());
                if (d.isModuleReference()) {
                    String to = moduleAlias.get(d.getModuleRef());
                    if (to != null) {
                        out.append(from).append(' ').append(arrow).append(' ')
                                .append(to).append(" : ").append(d.getScope()).append('\n');
                    }
                } else if (o.includeExternalLibs) {
                    String key = (d.getGroup() != null ? d.getGroup() + ":" : "")
                            + (d.getName() != null ? d.getName() : "");
                    String to = libAlias.get(key);
                    if (to != null) {
                        out.append(from).append(' ').append(arrow).append(' ')
                                .append(to).append(" : ").append(d.getScope()).append('\n');
                    }
                }
            }
        }
        if (o.includeLegend) {
            emitLegend(out, o);
        }
        out.append("@enduml\n");
        return out.toString();
    }

    /**
     * 後段の出力ロジックと同じフィルタ条件で「少なくとも 1 本のエッジに参加する」
     * モジュール集合を求める。孤立ノードを diagram から除外して Smetana の qsort
     * バグを回避するために使う。
     */
    private static Set<String> collectConnectedModules(
            AndroidProjectAnalysis analysis, Options o) {
        Set<String> connected = new HashSet<>();
        for (Map.Entry<String, GradleProjectInfo> e
                : analysis.getGradleByModule().entrySet()) {
            String from = e.getKey();
            for (GradleDependency d : e.getValue().getDependencies()) {
                if (!o.includeTestScopes && isTestScope(d.getScope())) {
                    continue;
                }
                if (d.isModuleReference()) {
                    String to = d.getModuleRef();
                    if (analysis.getGradleByModule().containsKey(to)) {
                        connected.add(from);
                        connected.add(to);
                    }
                } else if (o.includeExternalLibs) {
                    // 外部ライブラリへの依存もモジュール側の「接続あり」と見なす
                    connected.add(from);
                }
            }
        }
        return connected;
    }

    private static boolean isTestScope(String scope) {
        if (scope == null) {
            return false;
        }
        return scope.startsWith("test") || scope.startsWith("androidTest");
    }

    private static String arrowForScope(String scope) {
        if (scope == null) {
            return "-->";
        }
        if (scope.startsWith("test") || scope.startsWith("androidTest")) {
            return "..>";
        }
        if ("compileOnly".equals(scope) || "runtimeOnly".equals(scope)) {
            return "-->";
        }
        return "-->";
    }

    private static void emitLegend(StringBuilder out, Options o) {
        out.append("legend right\n");
        out.append("== Gradle 依存グラフ ==\n");
        out.append("component <<application>>  com.android.application\n");
        out.append("component <<library>>      com.android.library\n");
        out.append("component <<module>>       上記以外のモジュール\n");
        if (o.includeExternalLibs) {
            out.append("component <<external>>     外部 Maven 依存\n");
        }
        out.append("A --> B                    implementation/api 依存\n");
        if (o.includeTestScopes) {
            out.append("A ..> B                    test/androidTest 依存\n");
        }
        out.append("endlegend\n");
    }

    private PlantUmlGradleDependencyGraph() {
    }
}
