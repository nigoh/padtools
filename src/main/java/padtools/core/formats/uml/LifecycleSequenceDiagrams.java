package padtools.core.formats.uml;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Android ライフサイクルメソッド (Activity の onCreate 等) を起点に
 * PlantUML シーケンス図を一括生成するヘルパ。
 *
 * <p>CLI ({@code --all} / {@code --sequence-diagrams}) とエディタの両方から
 * 同じ起点規則を参照できるよう、コンポーネント種別ごとの起点候補と
 * 生成ロジックをここに集約する。</p>
 */
public final class LifecycleSequenceDiagrams {

    private static final Map<String, List<String>> ENTRY_BY_TYPE;

    static {
        Map<String, List<String>> m = new LinkedHashMap<>();
        m.put("Activity", Arrays.asList(
                "onCreate", "onStart", "onResume", "onPause", "onStop", "onDestroy"));
        m.put("Service", Arrays.asList(
                "onStartCommand", "onCreate", "onBind", "onDestroy"));
        m.put("BroadcastReceiver", Collections.singletonList("onReceive"));
        m.put("ContentProvider", Arrays.asList(
                "onCreate", "query", "insert", "update", "delete"));
        ENTRY_BY_TYPE = Collections.unmodifiableMap(m);
    }

    private LifecycleSequenceDiagrams() {
    }

    /** 生成された 1 本のシーケンス図 (起点クラス・メソッドと PlantUML テキスト)。 */
    public static final class Entry {
        public final String className;
        public final String methodName;
        public final String puml;

        Entry(String className, String methodName, String puml) {
            this.className = className;
            this.methodName = methodName;
            this.puml = puml;
        }

        /** ファイル名のベース ({@code Class.method})。拡張子は付与しない。 */
        public String baseName() {
            return className + "." + methodName;
        }
    }

    /**
     * クラス情報リストから Android ライフサイクル起点のシーケンス図をすべて生成して返す。
     * @param infos プロジェクトから抽出したクラス情報
     * @param opts シーケンス図生成オプション (null なら既定)
     * @return 生成された PlantUML シーケンス図のリスト (起点が見つからなければ空)
     */
    public static List<Entry> generateAll(List<JavaClassInfo> infos,
                                          PlantUmlSequenceDiagram.Options opts) {
        List<Entry> result = new ArrayList<>();
        if (infos == null || infos.isEmpty()) {
            return result;
        }
        PlantUmlSequenceDiagram.Options o = opts != null
                ? opts : new PlantUmlSequenceDiagram.Options();
        for (JavaClassInfo c : infos) {
            String compType = c.getAndroidComponentType();
            if (compType == null) {
                continue;
            }
            List<String> methodNames = ENTRY_BY_TYPE.get(compType);
            if (methodNames == null) {
                continue;
            }
            for (String mn : methodNames) {
                JavaMethodInfo m = findMethod(c, mn);
                if (m == null || m.getStatements().isEmpty()) {
                    continue;
                }
                String puml = PlantUmlSequenceDiagram.generate(
                        infos, c.getSimpleName(), m.getName(), o);
                result.add(new Entry(c.getSimpleName(), m.getName(), puml));
            }
        }
        return result;
    }

    private static JavaMethodInfo findMethod(JavaClassInfo cls, String name) {
        for (JavaMethodInfo cand : cls.getMethods()) {
            if (name.equals(cand.getName()) && !cand.isAbstract()) {
                return cand;
            }
        }
        return null;
    }
}
