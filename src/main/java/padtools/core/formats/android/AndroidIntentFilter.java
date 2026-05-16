package padtools.core.formats.android;

import java.util.ArrayList;
import java.util.List;

/**
 * AndroidManifest.xml の {@code <intent-filter>} 要素。
 */
public class AndroidIntentFilter {

    private final List<String> actions = new ArrayList<>();
    private final List<String> categories = new ArrayList<>();
    private final List<String> dataSchemes = new ArrayList<>();
    private final List<String> dataMimeTypes = new ArrayList<>();
    private Integer priority;

    public List<String> getActions() {
        return actions;
    }

    public List<String> getCategories() {
        return categories;
    }

    public List<String> getDataSchemes() {
        return dataSchemes;
    }

    public List<String> getDataMimeTypes() {
        return dataMimeTypes;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    /** action.MAIN + category.LAUNCHER を持つランチャー filter か判定。 */
    public boolean isLauncher() {
        return actions.contains("android.intent.action.MAIN")
                && categories.contains("android.intent.category.LAUNCHER");
    }
}
