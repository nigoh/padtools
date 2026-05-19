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
    private final Map<String, List<AndroidLayoutInfo>> layoutsByModule = new LinkedHashMap<>();
    private final Map<String, List<AndroidNavigationGraphInfo>> navigationsByModule
            = new LinkedHashMap<>();

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

    /**
     * モジュール名 → そのモジュールに含まれる layout XML のリスト。
     * {@link AndroidLayoutParser} 経由でパース済みの結果。空 Map で初期化されるので、
     * layout を解析していない場合も null にはならない。
     */
    public Map<String, List<AndroidLayoutInfo>> getLayoutsByModule() {
        return layoutsByModule;
    }

    /** 全モジュールの layout を 1 つのリストに連結。 */
    public List<AndroidLayoutInfo> allLayouts() {
        List<AndroidLayoutInfo> all = new ArrayList<>();
        for (List<AndroidLayoutInfo> list : layoutsByModule.values()) {
            all.addAll(list);
        }
        return all;
    }

    /**
     * モジュール名 → そのモジュールに含まれる Navigation グラフ XML のリスト。
     * {@link AndroidNavigationGraphParser} 経由でパース済みの結果。
     */
    public Map<String, List<AndroidNavigationGraphInfo>> getNavigationsByModule() {
        return navigationsByModule;
    }

    /** 全モジュールの Navigation グラフを 1 つのリストに連結。 */
    public List<AndroidNavigationGraphInfo> allNavigationGraphs() {
        List<AndroidNavigationGraphInfo> all = new ArrayList<>();
        for (List<AndroidNavigationGraphInfo> list : navigationsByModule.values()) {
            all.addAll(list);
        }
        return all;
    }

    /** {@link AndroidNavigationGraphInfo#getKey()} で Navigation グラフを検索。見つからなければ null。 */
    public AndroidNavigationGraphInfo findNavigationByKey(String key) {
        if (key == null) {
            return null;
        }
        for (AndroidNavigationGraphInfo info : allNavigationGraphs()) {
            if (key.equals(info.getKey())) {
                return info;
            }
        }
        return null;
    }

    /** {@link AndroidLayoutInfo#getKey()} で layout を検索。見つからなければ null。 */
    public AndroidLayoutInfo findLayoutByKey(String key) {
        if (key == null) {
            return null;
        }
        for (AndroidLayoutInfo info : allLayouts()) {
            if (key.equals(info.getKey())) {
                return info;
            }
        }
        return null;
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
