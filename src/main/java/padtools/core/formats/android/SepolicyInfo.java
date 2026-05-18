package padtools.core.formats.android;

import java.util.ArrayList;
import java.util.List;

/**
 * 1 つの {@code .te} ファイルから抽出した SELinux ポリシー情報。
 *
 * <p>{@link SepolicyTeParser} がファイル単位で生成する。
 * {@link AndroidProjectAnalysis#getSepolicies()} に集約され、
 * コンポーネント図および MultiUser ロール分離レポートで参照する。</p>
 */
public final class SepolicyInfo {

    private final String filePath;
    private final List<SepolicyType> types = new ArrayList<>();
    private final List<SepolicyRule> allowRules = new ArrayList<>();
    private final List<SepolicyRule> neverallowRules = new ArrayList<>();
    private final List<SepolicyTransition> transitions = new ArrayList<>();

    public SepolicyInfo(String filePath) {
        this.filePath = filePath == null ? "" : filePath;
    }

    public String getFilePath() {
        return filePath;
    }

    public List<SepolicyType> getTypes() {
        return types;
    }

    /** {@code allow} ルールのみ。 */
    public List<SepolicyRule> getAllowRules() {
        return allowRules;
    }

    /** {@code neverallow} ルールのみ。 */
    public List<SepolicyRule> getNeverallowRules() {
        return neverallowRules;
    }

    public List<SepolicyTransition> getTransitions() {
        return transitions;
    }
}
