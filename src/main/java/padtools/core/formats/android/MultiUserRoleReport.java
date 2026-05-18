package padtools.core.formats.android;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * MultiUser ロール分離レポートを Markdown で生成する。
 *
 * <p>AAOS では運転手 (driver) / 同乗者 (passenger) / システムユーザの 3 ロールが分離される。
 * permission ({@code android.car.permission.*}) と SELinux ルールの組み合わせで
 * 「どのロールがどの権限を使えるか」「どの domain がどのオブジェクトを触れるか」を
 * 集約して可視化する。</p>
 *
 * <p>本レポートは静的解析のみに基づくため確定的な権限判定ではなく、レビュー時の
 * 参照資料として活用する想定。</p>
 */
public final class MultiUserRoleReport {

    private static final String CAR_PERMISSION_PREFIX = "android.car.permission.";

    private MultiUserRoleReport() {
    }

    /** プロジェクト解析と sepolicy 解析結果から Markdown を生成。 */
    public static String generateMarkdown(AndroidProjectAnalysis analysis) {
        if (analysis == null) {
            return "(no analysis)\n";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("# AAOS MultiUser ロール分離レポート\n\n");

        emitPermissionSection(sb, analysis);
        emitSepolicySection(sb, analysis);
        emitTransitionSection(sb, analysis);

        return sb.toString();
    }

    private static void emitPermissionSection(StringBuilder sb,
                                                AndroidProjectAnalysis analysis) {
        sb.append("## Manifest 由来の Car permission 集計\n\n");
        Map<String, Set<String>> permToComponents = new TreeMap<>();
        for (AndroidManifestInfo manifest : analysis.allManifests()) {
            for (AndroidPermissionInfo permInfo : manifest.getPermissions()) {
                String perm = permInfo == null ? null : permInfo.getName();
                if (perm == null || !perm.startsWith(CAR_PERMISSION_PREFIX)) {
                    continue;
                }
                String pkg = manifest.getPackageName() != null
                        ? manifest.getPackageName() : "(unknown)";
                permToComponents
                        .computeIfAbsent(perm, k -> new LinkedHashSet<>())
                        .add(pkg);
            }
        }
        if (permToComponents.isEmpty()) {
            sb.append("(Car permission を要求している manifest は見つかりませんでした)\n\n");
            return;
        }
        sb.append("| Car permission | 要求している package |\n");
        sb.append("|---|---|\n");
        for (Map.Entry<String, Set<String>> e : permToComponents.entrySet()) {
            sb.append("| `").append(e.getKey()).append("` | ")
                    .append(String.join(", ", e.getValue()))
                    .append(" |\n");
        }
        sb.append('\n');
    }

    private static void emitSepolicySection(StringBuilder sb,
                                              AndroidProjectAnalysis analysis) {
        sb.append("## SELinux allow ルール (carservice / vehicle 関連)\n\n");
        List<SepolicyRule> hits = new ArrayList<>();
        for (SepolicyInfo te : analysis.getSepolicies()) {
            for (SepolicyRule rule : te.getAllowRules()) {
                if (isCarRelated(rule.getSourceType()) || isCarRelated(rule.getTargetType())) {
                    hits.add(rule);
                }
            }
        }
        if (hits.isEmpty()) {
            sb.append("(car/vehicle 関連の allow ルールは見つかりませんでした)\n\n");
            return;
        }
        sb.append("| source domain | target type | class | 許可 permission |\n");
        sb.append("|---|---|---|---|\n");
        for (SepolicyRule r : hits) {
            sb.append("| `").append(r.getSourceType()).append("` | `")
                    .append(r.getTargetType()).append("` | `")
                    .append(r.getObjectClass()).append("` | ")
                    .append(String.join(", ", r.getPermissions()))
                    .append(" |\n");
        }
        sb.append('\n');
    }

    private static void emitTransitionSection(StringBuilder sb,
                                                AndroidProjectAnalysis analysis) {
        Map<String, Set<String>> transitionMap = new LinkedHashMap<>();
        for (SepolicyInfo te : analysis.getSepolicies()) {
            for (SepolicyTransition t : te.getTransitions()) {
                if (!"process".equals(t.getObjectClass())) {
                    continue;
                }
                transitionMap
                        .computeIfAbsent(t.getSourceType(), k -> new LinkedHashSet<>())
                        .add(t.getNewType());
            }
        }
        if (transitionMap.isEmpty()) {
            return;
        }
        sb.append("## ドメイン遷移 (type_transition / process)\n\n");
        sb.append("| 起点 domain | 遷移先 domain |\n");
        sb.append("|---|---|\n");
        for (Map.Entry<String, Set<String>> e : transitionMap.entrySet()) {
            sb.append("| `").append(e.getKey()).append("` | ")
                    .append(String.join(", ", e.getValue())).append(" |\n");
        }
        sb.append('\n');
    }

    private static boolean isCarRelated(String type) {
        if (type == null || type.isEmpty()) {
            return false;
        }
        String t = type.toLowerCase();
        return t.contains("carservice") || t.contains("car_") || t.contains("vehicle")
                || t.contains("automotive");
    }
}
