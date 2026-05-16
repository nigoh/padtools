package padtools.core.formats.android;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * AndroidProjectAnalysis から PlantUML コンポーネント図を生成する。
 *
 * <p>Activity / Service / BroadcastReceiver / ContentProvider をそれぞれ
 * {@code component} ノードとして配置し、intent-filter のアクションを矢印として描く。
 * アプリケーションが要求するパーミッションは別パッケージにまとめる。</p>
 */
public final class PlantUmlComponentDiagram {

    /** 出力オプション。 */
    public static class Options {
        public boolean includeLegend = true;
        public boolean groupByModule = true;
        public boolean showIntentFilters = true;
        public boolean showPermissions = true;
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
        Map<String, String> aliasByFqn = new LinkedHashMap<>();
        int seq = 0;
        boolean any = false;
        for (Map.Entry<String, List<AndroidManifestInfo>> e
                : analysis.getManifestsByModule().entrySet()) {
            String moduleName = e.getKey();
            List<AndroidManifestInfo> manifests = e.getValue();
            if (o.groupByModule) {
                out.append("package \"").append(moduleName).append("\" {\n");
            }
            for (AndroidManifestInfo m : manifests) {
                for (AndroidComponentInfo c : m.allComponents()) {
                    String alias = "K" + (seq++);
                    aliasByFqn.put(c.getName(), alias);
                    String stereo = "<<" + c.getKind().label() + ">>";
                    String indent = o.groupByModule ? "  " : "";
                    out.append(indent).append("component \"").append(c.getName())
                            .append("\" as ").append(alias).append(' ')
                            .append(stereo);
                    if (Boolean.TRUE.equals(c.getExported())) {
                        out.append(" #LightYellow");
                    }
                    if (c.isLauncher()) {
                        out.append(" <<launcher>>");
                    }
                    out.append('\n');
                    any = true;
                }
            }
            if (o.groupByModule) {
                out.append("}\n");
            }
        }
        if (!any) {
            out.append("note as N1\n  (no manifest components found)\nend note\n");
        }
        if (o.showIntentFilters) {
            emitIntentFilters(out, analysis, aliasByFqn);
        }
        if (o.showPermissions) {
            emitPermissions(out, analysis);
        }
        if (o.includeLegend) {
            emitLegend(out, analysis, o);
        }
        out.append("@enduml\n");
        return out.toString();
    }

    private static void emitIntentFilters(StringBuilder out,
                                           AndroidProjectAnalysis analysis,
                                           Map<String, String> aliasByFqn) {
        // intent-filter の action を集約し、各 action を 1 つのノードにして矢印を引く
        Map<String, String> actionAlias = new HashMap<>();
        int seq = 0;
        for (AndroidManifestInfo m : analysis.allManifests()) {
            for (AndroidComponentInfo c : m.allComponents()) {
                String compAlias = aliasByFqn.get(c.getName());
                if (compAlias == null) {
                    continue;
                }
                for (AndroidIntentFilter f : c.getIntentFilters()) {
                    for (String action : f.getActions()) {
                        String aa = actionAlias.get(action);
                        if (aa == null) {
                            aa = "A" + (seq++);
                            actionAlias.put(action, aa);
                            out.append("usecase \"").append(shortAction(action))
                                    .append("\" as ").append(aa)
                                    .append(" <<action>>\n");
                        }
                        out.append(aa).append(" --> ").append(compAlias).append('\n');
                    }
                }
            }
        }
    }

    private static void emitPermissions(StringBuilder out, AndroidProjectAnalysis analysis) {
        Set<String> perms = new TreeSet<>();
        for (AndroidManifestInfo m : analysis.allManifests()) {
            for (AndroidPermissionInfo p : m.getPermissions()) {
                perms.add(p.getShortName());
            }
        }
        if (perms.isEmpty()) {
            return;
        }
        out.append("package \"permissions\" {\n");
        for (String p : perms) {
            out.append("  [").append(p).append("] <<permission>>\n");
        }
        out.append("}\n");
    }

    private static String shortAction(String a) {
        int dot = a.lastIndexOf('.');
        return dot >= 0 ? a.substring(dot + 1) : a;
    }

    private static void emitLegend(StringBuilder out, AndroidProjectAnalysis a, Options o) {
        out.append("legend right\n");
        out.append("== Android コンポーネント図 ==\n");
        out.append("component <<Activity>>          画面 (Activity)\n");
        out.append("component <<Service>>           サービス\n");
        out.append("component <<BroadcastReceiver>> ブロードキャストレシーバ\n");
        out.append("component <<ContentProvider>>   コンテンツプロバイダ\n");
        out.append("<<launcher>>                    ランチャー Activity\n");
        out.append("#LightYellow                    exported=true\n");
        if (o.showIntentFilters) {
            out.append("usecase <<action>>              intent-filter の action\n");
        }
        if (o.showPermissions) {
            out.append("[X] <<permission>>              uses-permission\n");
        }
        out.append("endlegend\n");
    }

    private PlantUmlComponentDiagram() {
    }
}
