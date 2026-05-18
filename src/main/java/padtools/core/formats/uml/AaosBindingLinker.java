package padtools.core.formats.uml;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AAOS 命名規約に従う 3 種類のクラス
 * ({@code Car<Topic>Manager} / {@code ICar<Topic>}(または {@code ICar<Topic>Service}) /
 * {@code Car<Topic>Service}) を topic 名で結びつける。
 *
 * <p>クラス図の関係線として {@code Manager .. AIDL : <<binds>>} と
 * {@code AIDL <|.. Service : <<implements>>} を描画するための入力として
 * {@link PlantUmlClassDiagram} から呼び出される。</p>
 *
 * <p>{@code confidence} の判定基準:</p>
 * <ul>
 *   <li>1: 命名一致のみ (Manager から topic を抽出し、対応する AIDL / Service が
 *        AAOS パッケージ内に同名で存在する)</li>
 *   <li>2: 命名一致 + Manager 側のフィールド型・コンストラクタ引数型に AIDL の
 *        単純名が出現する (= Manager が AIDL Stub/Proxy を保持している裏付け)</li>
 *   <li>3: 命名一致 + 上記 + Service が AIDL を {@code implements} / {@code extends}
 *        (= Service が AIDL の実装である裏付け)</li>
 * </ul>
 *
 * <p>クラス図表示時は confidence 1 以上の binding を発行する。strict モードで絞り込む
 * 場合は {@link #link(List, int)} に閾値を渡す。</p>
 */
public final class AaosBindingLinker {

    private AaosBindingLinker() {
    }

    /** {@code link(classes, 1)} の省略形 (確信度 1 以上)。 */
    public static List<AaosBinding> link(List<JavaClassInfo> classes) {
        return link(classes, 1);
    }

    /**
     * AAOS binding を抽出する。{@code minConfidence} 未満のものは結果から除外する。
     * @param classes 入力クラス列 (AaosPattern.categorize() 済みである必要は無い。
     *                内部で必要に応じて分類する)
     * @param minConfidence 1 〜 3 のいずれか。1 だと命名一致のみで採用、3 だと
     *                       Service の implements 裏付けが必要。
     */
    public static List<AaosBinding> link(List<JavaClassInfo> classes, int minConfidence) {
        if (classes == null || classes.isEmpty()) {
            return Collections.emptyList();
        }
        // categorize 未設定なら今ここで設定する (副作用なし、null 上書きは避ける)
        for (JavaClassInfo c : classes) {
            if (c.getAaosCategory() == null) {
                String cat = AaosPattern.categorize(c);
                if (cat != null) {
                    c.setAaosCategory(cat);
                }
            }
        }
        // topic -> Manager, AIDL, Service の候補表 (1 topic に複数候補がありうるが
        // 最初に出会ったものを採用)
        Map<String, JavaClassInfo> managerByTopic = new LinkedHashMap<>();
        Map<String, JavaClassInfo> aidlByTopic = new HashMap<>();
        Map<String, JavaClassInfo> serviceByTopic = new HashMap<>();
        for (JavaClassInfo c : classes) {
            String topic = topicOf(c);
            if (topic == null) {
                continue;
            }
            String cat = c.getAaosCategory();
            if ("CarManager".equals(cat)) {
                managerByTopic.putIfAbsent(topic, c);
            } else if ("AIDL".equals(cat) || "ICarInterface".equals(cat)) {
                aidlByTopic.putIfAbsent(topic, c);
            } else if ("CarService".equals(cat)) {
                serviceByTopic.putIfAbsent(topic, c);
            }
        }
        List<AaosBinding> result = new ArrayList<>();
        for (Map.Entry<String, JavaClassInfo> e : managerByTopic.entrySet()) {
            String topic = e.getKey();
            JavaClassInfo manager = e.getValue();
            JavaClassInfo aidl = aidlByTopic.get(topic);
            JavaClassInfo service = serviceByTopic.get(topic);
            if (aidl == null && service == null) {
                continue;
            }
            int confidence = 1;
            if (aidl != null && hasTypeReferenceTo(manager, aidl.getSimpleName())) {
                confidence++;
            }
            if (aidl != null && service != null
                    && implementsOrExtends(service, aidl.getSimpleName())) {
                confidence++;
            }
            if (confidence < minConfidence) {
                continue;
            }
            result.add(new AaosBinding(
                    manager.getQualifiedName(),
                    aidl != null ? aidl.getQualifiedName() : null,
                    service != null ? service.getQualifiedName() : null,
                    topic,
                    confidence));
        }
        return result;
    }

    /**
     * クラスの単純名から AAOS の topic を抽出する。
     * AAOS パッケージ内に居て、かつ規約名にマッチする場合のみ topic を返す。
     */
    static String topicOf(JavaClassInfo c) {
        if (c == null || c.getSimpleName() == null) {
            return null;
        }
        if (!AaosPattern.isInAaosPackage(c.getPackageName())) {
            return null;
        }
        String name = c.getSimpleName();
        String cat = c.getAaosCategory();
        if ("CarManager".equals(cat)) {
            // Car<Topic>Manager
            if (name.startsWith("Car") && name.endsWith("Manager")) {
                String t = name.substring("Car".length(), name.length() - "Manager".length());
                return t.isEmpty() ? null : t;
            }
        } else if ("CarService".equals(cat)) {
            // Car<Topic>Service
            if (name.startsWith("Car") && name.endsWith("Service")) {
                String t = name.substring("Car".length(), name.length() - "Service".length());
                return t.isEmpty() ? null : t;
            }
        } else if ("AIDL".equals(cat) || "ICarInterface".equals(cat)) {
            // ICar<Topic> または ICar<Topic>Service
            if (name.startsWith("ICar")) {
                String t = name.substring("ICar".length());
                if (t.endsWith("Service") && t.length() > "Service".length()) {
                    t = t.substring(0, t.length() - "Service".length());
                }
                return t.isEmpty() ? null : t;
            }
        }
        return null;
    }

    /**
     * クラスのフィールド型・コンストラクタ引数型・他のメソッド引数型に
     * 指定の simpleName が出現するか調べる。Manager → AIDL の裏付け用。
     */
    static boolean hasTypeReferenceTo(JavaClassInfo cls, String simpleName) {
        if (cls == null || simpleName == null || simpleName.isEmpty()) {
            return false;
        }
        for (JavaFieldInfo f : cls.getFields()) {
            if (typeMatches(f.getType(), simpleName)) {
                return true;
            }
        }
        for (JavaMethodInfo m : cls.getMethods()) {
            for (String t : m.getParameterTypes()) {
                if (typeMatches(t, simpleName)) {
                    return true;
                }
            }
            if (typeMatches(m.getReturnType(), simpleName)) {
                return true;
            }
        }
        return false;
    }

    /** Service が AIDL を {@code implements} / {@code extends} しているか調べる。 */
    static boolean implementsOrExtends(JavaClassInfo cls, String aidlSimpleName) {
        if (cls == null || aidlSimpleName == null) {
            return false;
        }
        // 直接継承: extends ICarFoo (まれ) または extends ICarFoo.Stub (典型)
        if (typeMatches(cls.getSuperClass(), aidlSimpleName)) {
            return true;
        }
        if (typeMatches(cls.getSuperClass(), aidlSimpleName + ".Stub")) {
            return true;
        }
        for (String iface : cls.getInterfaces()) {
            if (typeMatches(iface, aidlSimpleName)) {
                return true;
            }
            // ICar<Topic>.Stub を implements するケースも認める
            if (typeMatches(iface, aidlSimpleName + ".Stub")) {
                return true;
            }
        }
        return false;
    }

    /** 型参照の単純名末尾が target に一致するか (ジェネリクス・配列・パッケージは除去)。 */
    private static boolean typeMatches(String typeRef, String target) {
        if (typeRef == null || typeRef.isEmpty() || target == null) {
            return false;
        }
        String t = typeRef.trim();
        int lt = t.indexOf('<');
        if (lt >= 0) {
            t = t.substring(0, lt);
        }
        t = t.replaceAll("\\[\\]", "").trim();
        if (t.equals(target)) {
            return true;
        }
        int lastDot = t.lastIndexOf('.');
        // Stub のような内部参照は dot で分割した後ろも見る
        if (lastDot >= 0 && t.substring(lastDot + 1).equals(target)) {
            return true;
        }
        return t.endsWith("." + target);
    }
}
