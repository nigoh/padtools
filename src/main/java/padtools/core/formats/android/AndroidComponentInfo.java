package padtools.core.formats.android;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AndroidManifest.xml の Activity / Service / Receiver / Provider 宣言。
 */
public class AndroidComponentInfo {

    /** コンポーネント種別。 */
    public enum Kind {
        ACTIVITY("Activity"),
        SERVICE("Service"),
        RECEIVER("BroadcastReceiver"),
        PROVIDER("ContentProvider");

        private final String label;

        Kind(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private final Kind kind;
    private String name;
    private Boolean exported;
    private Boolean enabled;
    private String taskAffinity;
    private String process;
    private String permission;
    private String authorities;
    private final List<AndroidIntentFilter> intentFilters = new ArrayList<>();
    private final Map<String, String> metaData = new LinkedHashMap<>();

    public AndroidComponentInfo(Kind kind, String name) {
        this.kind = kind;
        this.name = name == null ? "" : name;
    }

    public Kind getKind() {
        return kind;
    }

    public String getName() {
        return name;
    }

    /** FQN を後付けで補完するための setter。 */
    public void setName(String name) {
        this.name = name == null ? "" : name;
    }

    public Boolean getExported() {
        return exported;
    }

    public void setExported(Boolean exported) {
        this.exported = exported;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getTaskAffinity() {
        return taskAffinity;
    }

    public void setTaskAffinity(String taskAffinity) {
        this.taskAffinity = taskAffinity;
    }

    public String getProcess() {
        return process;
    }

    public void setProcess(String process) {
        this.process = process;
    }

    public String getPermission() {
        return permission;
    }

    public void setPermission(String permission) {
        this.permission = permission;
    }

    public String getAuthorities() {
        return authorities;
    }

    public void setAuthorities(String authorities) {
        this.authorities = authorities;
    }

    public List<AndroidIntentFilter> getIntentFilters() {
        return intentFilters;
    }

    public Map<String, String> getMetaData() {
        return metaData;
    }

    /** ランチャー Activity か判定 (Activity 限定)。 */
    public boolean isLauncher() {
        if (kind != Kind.ACTIVITY) {
            return false;
        }
        for (AndroidIntentFilter f : intentFilters) {
            if (f.isLauncher()) {
                return true;
            }
        }
        return false;
    }
}
