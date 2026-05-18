package padtools.core.formats.android;

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
        /**
         * Soong (Android.bp) で取り込まれたモジュールも依存グラフに含める。
         * AOSP モードで {@code AndroidProjectAnalysis.getSoongModules()} が非空のときに有効。
         * partition (system/vendor/product/odm/system_ext) でステレオタイプ色分け。
         */
        public boolean includeSoongModules = true;
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
        boolean withSoong = o.includeSoongModules
                && !analysis.getSoongModules().isEmpty();
        if (withSoong) {
            // partition ステレオタイプ用の skinparam (色分け)
            out.append("skinparam component<<system>> { BackgroundColor #E0EFFF }\n");
            out.append("skinparam component<<vendor>> { BackgroundColor #FFE4B5 }\n");
            out.append("skinparam component<<product>> { BackgroundColor #E0FFE0 }\n");
            out.append("skinparam component<<odm>> { BackgroundColor #FFD6E0 }\n");
            out.append("skinparam component<<system_ext>> { BackgroundColor #F0E0FF }\n");
        }
        Map<String, String> moduleAlias = new LinkedHashMap<>();
        int seq = 0;
        // モジュールノード
        for (String module : analysis.getGradleByModule().keySet()) {
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
        // Soong モジュールノード
        Map<String, String> soongAlias = new LinkedHashMap<>();
        if (withSoong) {
            for (SoongModuleInfo m : analysis.getSoongModules()) {
                if (m.getName() == null || m.getName().isEmpty()) {
                    continue;
                }
                if (soongAlias.containsKey(m.getName())) {
                    continue;
                }
                String alias = "S" + (seq++);
                soongAlias.put(m.getName(), alias);
                String stereo = " <<" + partitionStereotype(m.getPartition()) + ">>";
                out.append("component \"").append(m.getName())
                        .append("\\n[").append(m.getModuleType()).append("]\" as ")
                        .append(alias).append(stereo).append('\n');
            }
            // Soong エッジ
            for (SoongModuleInfo m : analysis.getSoongModules()) {
                String from = soongAlias.get(m.getName());
                if (from == null) {
                    continue;
                }
                for (String dep : m.getDeps()) {
                    String to = soongAlias.get(dep);
                    if (to != null) {
                        out.append(from).append(" --> ").append(to).append('\n');
                    }
                }
            }
        }
        if (o.includeLegend) {
            emitLegend(out, o, withSoong);
        }
        out.append("@enduml\n");
        return out.toString();
    }

    /** Partition → PlantUML ステレオタイプ名。 */
    private static String partitionStereotype(Partition p) {
        switch (p) {
            case SYSTEM: return "system";
            case VENDOR: return "vendor";
            case PRODUCT: return "product";
            case ODM: return "odm";
            case SYSTEM_EXT: return "system_ext";
            default: return "module";
        }
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

    private static void emitLegend(StringBuilder out, Options o, boolean withSoong) {
        out.append("legend right\n");
        out.append("== Gradle 依存グラフ ==\n");
        out.append("component <<application>>  com.android.application\n");
        out.append("component <<library>>      com.android.library\n");
        out.append("component <<module>>       上記以外のモジュール\n");
        if (o.includeExternalLibs) {
            out.append("component <<external>>     外部 Maven 依存\n");
        }
        if (withSoong) {
            out.append("component <<system>>       AOSP system partition\n");
            out.append("component <<vendor>>       AOSP vendor partition\n");
            out.append("component <<product>>      AOSP product partition\n");
            out.append("component <<odm>>          AOSP odm partition\n");
            out.append("component <<system_ext>>   AOSP system_ext partition\n");
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
