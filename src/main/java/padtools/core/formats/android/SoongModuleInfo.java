package padtools.core.formats.android;

import java.util.ArrayList;
import java.util.List;

/**
 * Soong (Android.bp) のモジュール 1 つを表す情報。
 *
 * <p>{@link SoongBpParser#parse} で 1 つの Bp ファイルから複数の SoongModuleInfo が
 * 取り出される。依存グラフ ({@link PlantUmlGradleDependencyGraph}) と統合して
 * AOSP の system/vendor/product 配置を色分け表示する用途で使う。</p>
 */
public final class SoongModuleInfo {

    private String name = "";
    private String moduleType = "";
    private Partition partition = Partition.UNKNOWN;
    private final List<String> srcs = new ArrayList<>();
    private final List<String> deps = new ArrayList<>();
    private final List<String> defaults = new ArrayList<>();
    private String bpFilePath;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null ? "" : name;
    }

    /**
     * Soong のモジュール型 ({@code cc_library_shared} / {@code java_library} /
     * {@code android_app} / {@code aidl_interface} 等)。
     */
    public String getModuleType() {
        return moduleType;
    }

    public void setModuleType(String moduleType) {
        this.moduleType = moduleType == null ? "" : moduleType;
    }

    public Partition getPartition() {
        return partition;
    }

    public void setPartition(Partition partition) {
        this.partition = partition == null ? Partition.UNKNOWN : partition;
    }

    /** {@code srcs: [...]} で指定されたソースリスト。 */
    public List<String> getSrcs() {
        return srcs;
    }

    /**
     * 依存先モジュール名の集約 ({@code static_libs}, {@code shared_libs}, {@code libs} の和集合)。
     * Gradle 依存グラフと同じ抽象度で扱うため一本化している。
     */
    public List<String> getDeps() {
        return deps;
    }

    /** {@code defaults: [...]} で参照されているデフォルト名。 */
    public List<String> getDefaults() {
        return defaults;
    }

    public String getBpFilePath() {
        return bpFilePath;
    }

    public void setBpFilePath(String bpFilePath) {
        this.bpFilePath = bpFilePath;
    }

    @Override
    public String toString() {
        return moduleType + "{" + name + ", partition=" + partition
                + ", deps=" + deps + "}";
    }
}
