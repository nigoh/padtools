package padtools.core.formats.android;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * AndroidProjectAnalysis を人が読みやすい Markdown レポートに整形する。
 */
public final class TextSummaryReport {

    /** Markdown サマリーを生成。 */
    public static String toMarkdown(AndroidProjectAnalysis analysis) {
        if (analysis == null) {
            throw new IllegalArgumentException("analysis is null");
        }
        StringBuilder sb = new StringBuilder();
        sb.append("# Android Project Summary\n\n");

        sb.append("## Modules\n\n");
        if (analysis.getRootSettings() != null
                && !analysis.getRootSettings().getSubprojects().isEmpty()) {
            sb.append("settings.gradle includes:\n");
            for (String m : analysis.getRootSettings().getSubprojects()) {
                sb.append("- ").append(m).append('\n');
            }
            sb.append('\n');
        }
        if (analysis.getGradleByModule().isEmpty()) {
            sb.append("(no build.gradle files parsed)\n\n");
        } else {
            for (Map.Entry<String, GradleProjectInfo> e
                    : analysis.getGradleByModule().entrySet()) {
                appendModuleSection(sb, e.getKey(), e.getValue());
            }
        }

        sb.append("## Components\n\n");
        if (analysis.allComponents().isEmpty()) {
            sb.append("(no AndroidManifest.xml components)\n\n");
        } else {
            appendComponentsSection(sb, analysis);
        }

        sb.append("## Permissions\n\n");
        appendPermissionsSection(sb, analysis);

        sb.append("## Features\n\n");
        appendFeaturesSection(sb, analysis);

        return sb.toString();
    }

    private static void appendModuleSection(StringBuilder sb, String name,
                                             GradleProjectInfo info) {
        sb.append("### `").append(name).append("`\n\n");
        if (info.isAndroidApplication()) {
            sb.append("- Type: **Android Application**\n");
        } else if (info.isAndroidLibrary()) {
            sb.append("- Type: **Android Library**\n");
        }
        appendIf(sb, "Application ID", info.getApplicationId());
        appendIf(sb, "Namespace", info.getNamespace());
        appendIf(sb, "compileSdk", str(info.getCompileSdk()));
        appendIf(sb, "minSdk", str(info.getMinSdk()));
        appendIf(sb, "targetSdk", str(info.getTargetSdk()));
        appendIf(sb, "versionCode", str(info.getVersionCode()));
        appendIf(sb, "versionName", info.getVersionName());
        if (!info.getPlugins().isEmpty()) {
            sb.append("- Plugins: ").append(String.join(", ", info.getPlugins())).append('\n');
        }
        if (!info.getFlavorDimensions().isEmpty()) {
            sb.append("- flavorDimensions: ")
                    .append(String.join(", ", info.getFlavorDimensions())).append('\n');
        }
        if (!info.getBuildTypes().isEmpty()) {
            sb.append("\n**Build Types:**\n\n");
            for (GradleBuildType bt : info.getBuildTypes().values()) {
                sb.append("- `").append(bt.getName()).append("`");
                if (bt.getMinifyEnabled() != null) {
                    sb.append(" minifyEnabled=").append(bt.getMinifyEnabled());
                }
                if (bt.getDebuggable() != null) {
                    sb.append(" debuggable=").append(bt.getDebuggable());
                }
                if (bt.getApplicationIdSuffix() != null) {
                    sb.append(" appIdSuffix=").append(bt.getApplicationIdSuffix());
                }
                sb.append('\n');
            }
        }
        if (!info.getProductFlavors().isEmpty()) {
            sb.append("\n**Product Flavors:**\n\n");
            for (GradleProductFlavor pf : info.getProductFlavors().values()) {
                sb.append("- `").append(pf.getName()).append("`");
                if (pf.getDimension() != null) {
                    sb.append(" dimension=").append(pf.getDimension());
                }
                sb.append('\n');
            }
        }
        if (!info.getSigningConfigs().isEmpty()) {
            sb.append("\n**Signing Configs (機密値は表示しない):**\n\n");
            for (GradleSigningConfig sc : info.getSigningConfigs().values()) {
                sb.append("- `").append(sc.getName()).append("`");
                if (sc.getKeyAlias() != null) {
                    sb.append(" keyAlias=").append(sc.getKeyAlias());
                }
                sb.append('\n');
            }
        }
        if (!info.getDependencies().isEmpty()) {
            sb.append("\n**Dependencies:**\n\n");
            sb.append("| Scope | Notation |\n|---|---|\n");
            for (GradleDependency d : info.getDependencies()) {
                sb.append("| ").append(d.getScope())
                        .append(" | ").append(d.getNotation()).append(" |\n");
            }
        }
        sb.append('\n');
    }

    private static void appendComponentsSection(StringBuilder sb,
                                                  AndroidProjectAnalysis analysis) {
        for (Map.Entry<String, List<AndroidManifestInfo>> e
                : analysis.getManifestsByModule().entrySet()) {
            sb.append("### Module `").append(e.getKey()).append("`\n\n");
            for (AndroidManifestInfo m : e.getValue()) {
                sb.append("- Package: `").append(m.getPackageName()).append("`\n");
                if (m.getApplicationClass() != null) {
                    sb.append("- Application class: `")
                            .append(m.getApplicationClass()).append("`\n");
                }
                appendComponentList(sb, "Activities", m.getActivities());
                appendComponentList(sb, "Services", m.getServices());
                appendComponentList(sb, "Receivers", m.getReceivers());
                appendComponentList(sb, "Providers", m.getProviders());
            }
            sb.append('\n');
        }
    }

    private static void appendComponentList(StringBuilder sb, String label,
                                             List<AndroidComponentInfo> list) {
        if (list.isEmpty()) {
            return;
        }
        sb.append("\n**").append(label).append(":**\n\n");
        for (AndroidComponentInfo c : list) {
            sb.append("- `").append(c.getName()).append('`');
            if (Boolean.TRUE.equals(c.getExported())) {
                sb.append(" *(exported)*");
            }
            if (c.isLauncher()) {
                sb.append(" *(launcher)*");
            }
            if (!c.getIntentFilters().isEmpty()) {
                sb.append(" — intent-filters: ").append(c.getIntentFilters().size());
            }
            sb.append('\n');
            for (AndroidIntentFilter f : c.getIntentFilters()) {
                if (!f.getActions().isEmpty()) {
                    sb.append("  - actions: ").append(String.join(", ", f.getActions()))
                            .append('\n');
                }
                if (!f.getCategories().isEmpty()) {
                    sb.append("  - categories: ")
                            .append(String.join(", ", f.getCategories())).append('\n');
                }
            }
        }
    }

    private static void appendPermissionsSection(StringBuilder sb,
                                                   AndroidProjectAnalysis analysis) {
        Set<String> perms = new TreeSet<>();
        for (AndroidManifestInfo m : analysis.allManifests()) {
            for (AndroidPermissionInfo p : m.getPermissions()) {
                perms.add(p.getName());
            }
        }
        if (perms.isEmpty()) {
            sb.append("(none)\n\n");
            return;
        }
        for (String p : perms) {
            sb.append("- ").append(p).append('\n');
        }
        sb.append('\n');
    }

    private static void appendFeaturesSection(StringBuilder sb,
                                                AndroidProjectAnalysis analysis) {
        Set<String> features = new TreeSet<>();
        for (AndroidManifestInfo m : analysis.allManifests()) {
            features.addAll(m.getFeatures());
        }
        if (features.isEmpty()) {
            sb.append("(none)\n");
            return;
        }
        for (String f : features) {
            sb.append("- ").append(f).append('\n');
        }
        sb.append('\n');
    }

    private static void appendIf(StringBuilder sb, String label, String value) {
        if (value != null && !value.isEmpty()) {
            sb.append("- ").append(label).append(": `").append(value).append("`\n");
        }
    }

    private static String str(Integer v) {
        return v == null ? null : v.toString();
    }

    private TextSummaryReport() {
    }
}
