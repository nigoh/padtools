package padtools.core.formats.uml;

/**
 * AAOS で頻出する {@code Car<Topic>Manager} ↔ {@code ICar<Topic>}/{@code ICar<Topic>Service} ↔
 * {@code Car<Topic>Service} の三者関係を表す。
 *
 * <p>{@link AaosBindingLinker} がクラス命名規約 (CarPropertyManager → ICarProperty →
 * CarPropertyService 等) と型参照 (Manager のフィールドが AIDL 型を持つ、Service が AIDL
 * インタフェースを実装している) を組み合わせて推定する。</p>
 *
 * <p>{@code confidence} は推定の根拠の数 (1 = 命名一致のみ、2 = 命名 + 型参照一致 など) で、
 * 0 〜 3 の範囲を取る。クラス図はこれが 1 以上の binding だけを {@code <<binds>>} 線として
 * 描画する。</p>
 */
public final class AaosBinding {

    private final String managerFqn;
    private final String aidlFqn;
    private final String serviceFqn;
    private final String topic;
    private final int confidence;

    public AaosBinding(String managerFqn, String aidlFqn, String serviceFqn,
                       String topic, int confidence) {
        this.managerFqn = managerFqn;
        this.aidlFqn = aidlFqn;
        this.serviceFqn = serviceFqn;
        this.topic = topic;
        this.confidence = confidence;
    }

    public String getManagerFqn() {
        return managerFqn;
    }

    public String getAidlFqn() {
        return aidlFqn;
    }

    public String getServiceFqn() {
        return serviceFqn;
    }

    /** {@code Property}, {@code Audio} 等、命名から抽出した topic。 */
    public String getTopic() {
        return topic;
    }

    public int getConfidence() {
        return confidence;
    }

    @Override
    public String toString() {
        return "AaosBinding{topic=" + topic
                + ", manager=" + managerFqn
                + ", aidl=" + aidlFqn
                + ", service=" + serviceFqn
                + ", conf=" + confidence + "}";
    }
}
