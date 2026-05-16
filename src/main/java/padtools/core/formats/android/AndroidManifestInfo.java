package padtools.core.formats.android;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AndroidManifest.xml の解析結果。
 */
public class AndroidManifestInfo {

    private String packageName = "";
    private String applicationClass;
    private String applicationLabel;
    private String applicationTheme;
    private Boolean applicationDebuggable;
    private Boolean applicationAllowBackup;

    private final List<AndroidComponentInfo> activities = new ArrayList<>();
    private final List<AndroidComponentInfo> services = new ArrayList<>();
    private final List<AndroidComponentInfo> receivers = new ArrayList<>();
    private final List<AndroidComponentInfo> providers = new ArrayList<>();
    private final List<AndroidPermissionInfo> permissions = new ArrayList<>();
    private final List<String> features = new ArrayList<>();
    private final Map<String, String> applicationMetaData = new LinkedHashMap<>();

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName == null ? "" : packageName;
    }

    public String getApplicationClass() {
        return applicationClass;
    }

    public void setApplicationClass(String applicationClass) {
        this.applicationClass = applicationClass;
    }

    public String getApplicationLabel() {
        return applicationLabel;
    }

    public void setApplicationLabel(String applicationLabel) {
        this.applicationLabel = applicationLabel;
    }

    public String getApplicationTheme() {
        return applicationTheme;
    }

    public void setApplicationTheme(String applicationTheme) {
        this.applicationTheme = applicationTheme;
    }

    public Boolean getApplicationDebuggable() {
        return applicationDebuggable;
    }

    public void setApplicationDebuggable(Boolean applicationDebuggable) {
        this.applicationDebuggable = applicationDebuggable;
    }

    public Boolean getApplicationAllowBackup() {
        return applicationAllowBackup;
    }

    public void setApplicationAllowBackup(Boolean applicationAllowBackup) {
        this.applicationAllowBackup = applicationAllowBackup;
    }

    public List<AndroidComponentInfo> getActivities() {
        return activities;
    }

    public List<AndroidComponentInfo> getServices() {
        return services;
    }

    public List<AndroidComponentInfo> getReceivers() {
        return receivers;
    }

    public List<AndroidComponentInfo> getProviders() {
        return providers;
    }

    public List<AndroidPermissionInfo> getPermissions() {
        return permissions;
    }

    public List<String> getFeatures() {
        return features;
    }

    public Map<String, String> getApplicationMetaData() {
        return applicationMetaData;
    }

    /** 全コンポーネントを 1 つのリストに集約。 */
    public List<AndroidComponentInfo> allComponents() {
        List<AndroidComponentInfo> all = new ArrayList<>();
        all.addAll(activities);
        all.addAll(services);
        all.addAll(receivers);
        all.addAll(providers);
        return all;
    }

    /**
     * 相対名 ({@code .MainActivity}) を {@code packageName} と結合して FQN に解決する。
     * 既に完全修飾の場合はそのまま返す。
     */
    public String resolveClassName(String raw) {
        if (raw == null || raw.isEmpty()) {
            return raw;
        }
        if (raw.startsWith(".")) {
            return packageName + raw;
        }
        if (!raw.contains(".") && !packageName.isEmpty()) {
            return packageName + "." + raw;
        }
        return raw;
    }
}
