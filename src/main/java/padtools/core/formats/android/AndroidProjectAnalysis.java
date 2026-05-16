package padtools.core.formats.android;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link AndroidProjectAnalyzer} が返すプロジェクト解析結果。
 *
 * <p>モジュールごとの {@link GradleProjectInfo} と {@link AndroidManifestInfo} を保持する。
 * 同モジュール内に複数 manifest (flavor 上書きなど) がある場合はリストで保持する。</p>
 */
public class AndroidProjectAnalysis {

    private GradleProjectInfo rootSettings;
    private final Map<String, GradleProjectInfo> gradleByModule = new LinkedHashMap<>();
    private final Map<String, List<AndroidManifestInfo>> manifestsByModule = new LinkedHashMap<>();

    public GradleProjectInfo getRootSettings() {
        return rootSettings;
    }

    public void setRootSettings(GradleProjectInfo rootSettings) {
        this.rootSettings = rootSettings;
    }

    public Map<String, GradleProjectInfo> getGradleByModule() {
        return gradleByModule;
    }

    public Map<String, List<AndroidManifestInfo>> getManifestsByModule() {
        return manifestsByModule;
    }

    /** 全モジュールのマニフェストを 1 つのリストに連結。 */
    public List<AndroidManifestInfo> allManifests() {
        List<AndroidManifestInfo> all = new ArrayList<>();
        for (List<AndroidManifestInfo> list : manifestsByModule.values()) {
            all.addAll(list);
        }
        return all;
    }

    /** 全モジュールの全コンポーネントを 1 つのリストに連結。 */
    public List<AndroidComponentInfo> allComponents() {
        List<AndroidComponentInfo> all = new ArrayList<>();
        for (AndroidManifestInfo m : allManifests()) {
            all.addAll(m.allComponents());
        }
        return all;
    }

    /** FQN でコンポーネントを検索。 */
    public AndroidComponentInfo findComponentByFqn(String fqn) {
        if (fqn == null) {
            return null;
        }
        for (AndroidComponentInfo c : allComponents()) {
            if (fqn.equals(c.getName())) {
                return c;
            }
        }
        return null;
    }
}
