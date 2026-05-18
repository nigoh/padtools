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
        /**
         * AOSP モードで取り込んだ SELinux ポリシー (.te) の domain と allow ルールを
         * 同じ図に描画する。{@code AndroidProjectAnalysis.getSepolicies()} が空の
         * 場合は何も追加されない。
         */
        public boolean showSepolicyDomains = true;
        /**
         * sepolicy 関連ノードを描画する際に表示する allow ルールの最大本数 (図の爆発防止)。
         * 0 以下なら無制限。
         */
        public int maxSepolicyRules = 80;
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
            // 同一モジュール内に複数 manifest (main + debug + flavor) があると同じ
            // コンポーネントが複数回宣言されることがあるため FQN で重複排除する。
            // 別 sourceSet からの差分は <<overlay>> ステレオタイプで付加する。
            java.util.Set<String> emittedFqn = new java.util.HashSet<>();
            for (AndroidManifestInfo m : manifests) {
                for (AndroidComponentInfo c : m.allComponents()) {
                    if (c.getName() == null || c.getName().isEmpty()) {
                        continue;
                    }
                    String fqn = c.getName();
                    if (!emittedFqn.add(fqn)) {
                        continue;
                    }
                    String alias = "K" + (seq++);
                    aliasByFqn.put(fqn, alias);
                    String stereo = "<<" + c.getKind().label() + ">>";
                    String indent = o.groupByModule ? "  " : "";
                    out.append(indent).append("component \"").append(fqn)
                            .append("\" as ").append(alias).append(' ')
                            .append(stereo);
                    if (!"main".equals(m.getSourceSet())) {
                        out.append(" <<src:").append(m.getSourceSet()).append(">>");
                    }
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
        if (o.showSepolicyDomains && !analysis.getSepolicies().isEmpty()) {
            emitSepolicyDomains(out, analysis, o);
        }
        if (o.includeLegend) {
            emitLegend(out, analysis, o);
        }
        out.append("@enduml\n");
        return out.toString();
    }

    /**
     * sepolicy の domain 型をコンポーネントとして配置し、{@code allow} ルールを
     * {@code <<allow>>} 矢印で描画する。同じ src→tgt の組は集約して 1 本の矢印にする。
     */
    private static void emitSepolicyDomains(StringBuilder out,
                                              AndroidProjectAnalysis analysis,
                                              Options o) {
        Map<String, String> domainAlias = new LinkedHashMap<>();
        int seq = 0;
        out.append("package \"sepolicy\" {\n");
        for (SepolicyInfo te : analysis.getSepolicies()) {
            for (SepolicyType t : te.getTypes()) {
                if (!t.isDomain()) {
                    continue;
                }
                if (domainAlias.containsKey(t.getName())) {
                    continue;
                }
                String alias = "D" + (seq++);
                domainAlias.put(t.getName(), alias);
                out.append("  component \"").append(t.getName())
                        .append("\" as ").append(alias)
                        .append(" <<domain>>\n");
            }
        }
        out.append("}\n");
        // 集約した allow ルール (src,tgt) → 件数
        Map<String, int[]> edgeKey = new LinkedHashMap<>();
        for (SepolicyInfo te : analysis.getSepolicies()) {
            for (SepolicyRule rule : te.getAllowRules()) {
                String src = rule.getSourceType();
                String tgt = rule.getTargetType();
                if (!domainAlias.containsKey(src)) {
                    continue;
                }
                String key = src + "\t" + tgt;
                edgeKey.computeIfAbsent(key, k -> new int[]{0})[0]++;
            }
        }
        int emitted = 0;
        for (Map.Entry<String, int[]> e : edgeKey.entrySet()) {
            if (o.maxSepolicyRules > 0 && emitted >= o.maxSepolicyRules) {
                out.append("note as SNOMORE\n  (")
                        .append(edgeKey.size() - emitted)
                        .append(" more allow rules truncated)\nend note\n");
                break;
            }
            String[] parts = e.getKey().split("\t", -1);
            String src = parts[0];
            String tgt = parts[1];
            String srcAlias = domainAlias.get(src);
            String tgtAlias = domainAlias.get(tgt);
            if (tgtAlias == null) {
                // target が domain でない場合は別名コンポーネントを暗黙生成
                tgtAlias = "T" + (seq++);
                domainAlias.put(tgt, tgtAlias);
                out.append("component \"").append(tgt)
                        .append("\" as ").append(tgtAlias)
                        .append(" <<sepolicy_type>>\n");
            }
            int count = e.getValue()[0];
            out.append(srcAlias).append(" ..> ").append(tgtAlias)
                    .append(" : <<allow>>");
            if (count > 1) {
                out.append(" x").append(count);
            }
            out.append('\n');
            emitted++;
        }
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
