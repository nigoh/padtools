package padtools.core.formats.uml;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * AAOS (Android Automotive OS) 固有のパターンを認識するユーティリティ。
 *
 * <p>クラスのカテゴリ (CarManager / CarService / AAOS Annotation / 他) や、
 * AAOS 固有のアノテーション ({@code @AddedIn}, {@code @AddedInOrBefore},
 * {@code @ApiRequirements}) を検出する。</p>
 */
public final class AaosPattern {

    /** AAOS API バージョニング・要件アノテーション。 */
    public static final Set<String> API_ANNOTATIONS;
    static {
        Set<String> s = new HashSet<>(Arrays.asList(
                "AddedIn", "AddedInOrBefore", "ApiRequirements",
                "RequiresPermission", "PermissionResult",
                "MinimumPlatformSdkVersion", "MinimumCarVersion"));
        API_ANNOTATIONS = Collections.unmodifiableSet(s);
    }

    /** AAOS の主要パッケージプレフィックス。 */
    public static final List<String> AAOS_PACKAGE_PREFIXES = Collections.unmodifiableList(
            Arrays.asList(
                    "android.car",
                    "com.android.car",
                    "com.google.android.car",
                    "android.automotive",
                    "com.android.internal.car"));

    private static final Pattern MANAGER_PATTERN = Pattern.compile("^Car[A-Z].*Manager$");
    private static final Pattern SERVICE_PATTERN = Pattern.compile("^Car[A-Z].*Service$");
    private static final Pattern AIDL_PATTERN = Pattern.compile("^ICar[A-Z0-9].*$");
    /**
     * AIDL 生成された binder stub の継承パターン。{@code ICarFoo.Stub} や、
     * 多重ネストの {@code Outer.Inner.Stub} (任意のジェネリクス後置を含む) にも
     * マッチさせるため、最終セグメントが {@code Stub} で終わる修飾名を対象にする。
     */
    private static final Pattern STUB_SUPERCLASS_PATTERN =
            Pattern.compile("^.+\\.Stub(?:<[^>]*>)?$");
    /**
     * クラスの JavaDoc 内で hidden API を示すマーカー。{@code @hide} もしくは
     * {@code {@hide}} の前後に単語境界 (改行・空白・記号) があることを要求し、
     * 識別子内の {@code @hide} 等での誤検出を避ける。
     */
    private static final Pattern HIDE_JAVADOC_PATTERN =
            Pattern.compile("(?:^|[\\s*{(])@hide(?:$|[\\s*})])");

    /** Android プラットフォーム API 可視性マーカーの annotation 短名。 */
    private static final Set<String> SYSTEM_API_ANNOTATIONS;
    private static final Set<String> TEST_API_ANNOTATIONS;
    static {
        SYSTEM_API_ANNOTATIONS = Collections.unmodifiableSet(new HashSet<>(
                Arrays.asList("SystemApi")));
        TEST_API_ANNOTATIONS = Collections.unmodifiableSet(new HashSet<>(
                Arrays.asList("TestApi")));
    }

    /** クラスのカテゴリを推定する (見つからなければ null)。 */
    public static String categorize(JavaClassInfo info) {
        if (info == null || info.getSimpleName() == null) {
            return null;
        }
        String name = info.getSimpleName();
        boolean inAaosPkg = isInAaosPackage(info.getPackageName());
        if (info.getKind() == JavaClassInfo.Kind.AIDL_INTERFACE) {
            return "AIDL";
        }
        if (MANAGER_PATTERN.matcher(name).matches() && inAaosPkg) {
            return "CarManager";
        }
        if (SERVICE_PATTERN.matcher(name).matches() && inAaosPkg) {
            return "CarService";
        }
        if (AIDL_PATTERN.matcher(name).matches()
                && info.getKind() == JavaClassInfo.Kind.INTERFACE) {
            return "ICarInterface";
        }
        if (inAaosPkg && hasAaosApiAnnotation(info)) {
            return "AaosApi";
        }
        return null;
    }

    /** パッケージ名が AAOS 関連プレフィックスにマッチするか。 */
    public static boolean isInAaosPackage(String pkg) {
        if (pkg == null || pkg.isEmpty()) {
            return false;
        }
        for (String p : AAOS_PACKAGE_PREFIXES) {
            if (pkg.equals(p) || pkg.startsWith(p + ".")) {
                return true;
            }
        }
        return false;
    }

    /** クラス/メソッド/フィールドのアノテーションリストに AAOS API アノテーションがあれば true。 */
    public static boolean hasAaosApiAnnotation(JavaClassInfo info) {
        if (info == null) {
            return false;
        }
        if (containsAaosApi(info.getAnnotations())) {
            return true;
        }
        for (JavaMethodInfo m : info.getMethods()) {
            if (containsAaosApi(m.getAnnotations())) {
                return true;
            }
        }
        for (JavaFieldInfo f : info.getFields()) {
            if (containsAaosApi(f.getAnnotations())) {
                return true;
            }
        }
        return false;
    }

    /** 単一アノテーション (例 "AddedIn(majorVersion=33)") から短い名前を取得し API マーカーか判定。 */
    public static boolean isAaosApiAnnotation(String annotation) {
        if (annotation == null || annotation.isEmpty()) {
            return false;
        }
        String name = annotation;
        int paren = name.indexOf('(');
        if (paren >= 0) {
            name = name.substring(0, paren);
        }
        int dot = name.lastIndexOf('.');
        if (dot >= 0) {
            name = name.substring(dot + 1);
        }
        return API_ANNOTATIONS.contains(name);
    }

    private static boolean containsAaosApi(List<String> annotations) {
        for (String a : annotations) {
            if (isAaosApiAnnotation(a)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Android プラットフォーム API の可視性マーカーを 1 つだけ返す
     * (見つからなければ null)。
     *
     * <p>優先順位は {@code Hidden > SystemApi > TestApi}。これは
     * 「`@SystemApi` だが `@hide` JavaDoc 付き」のような併用パターンで、
     * より制限の強い hidden を優先表示するため。</p>
     *
     * <p>判定基準:</p>
     * <ul>
     *   <li>{@code Hidden}: クラスの JavaDoc に {@code @hide} を含む</li>
     *   <li>{@code SystemApi}: クラスのアノテーションリストに
     *       {@code @SystemApi} が含まれる (FQN や引数有りも考慮)</li>
     *   <li>{@code TestApi}: 同 {@code @TestApi}</li>
     * </ul>
     *
     * <p>これらは AAOS 専用ではなく Android プラットフォーム全般のマーカー
     * だが、AAOS の Stub / CarService / 内部 SDK で多用されるため本
     * パターン集に同居させる。</p>
     */
    public static String apiVisibilityStereotype(JavaClassInfo info) {
        if (info == null) {
            return null;
        }
        if (hasHideJavadoc(info)) {
            return "Hidden";
        }
        if (hasAnnotationShortName(info.getAnnotations(), SYSTEM_API_ANNOTATIONS)) {
            return "SystemApi";
        }
        if (hasAnnotationShortName(info.getAnnotations(), TEST_API_ANNOTATIONS)) {
            return "TestApi";
        }
        return null;
    }

    /**
     * このクラスが AIDL の生成 stub を継承して binder サービスを実装しているか。
     *
     * <p>典型的なパターン: {@code class CarFooService extends ICarFoo.Stub}。
     * シーケンス図でプロセス境界 ({@code binder} hop) を可視化する判定に使う。
     * superClass 名の末尾セグメントが {@code Stub} (任意のジェネリクス後置可)
     * で、かつ前段が 1 セグメント以上ある場合に true を返す。</p>
     */
    public static boolean isAidlBinderImpl(JavaClassInfo info) {
        if (info == null) {
            return false;
        }
        String sup = info.getSuperClass();
        if (sup == null || sup.isEmpty()) {
            return false;
        }
        return STUB_SUPERCLASS_PATTERN.matcher(sup.trim()).matches();
    }

    /** JavaDoc コメントに {@code @hide} マーカーが含まれているか。 */
    private static boolean hasHideJavadoc(JavaClassInfo info) {
        String c = info.getComment();
        if (c == null || c.isEmpty()) {
            return false;
        }
        return HIDE_JAVADOC_PATTERN.matcher(c).find();
    }

    /**
     * アノテーション短名 ({@code @Foo} の {@code Foo} 部) のいずれかが
     * {@code names} に含まれているか。FQN 形式 ({@code android.annotation.SystemApi})
     * や引数有り ({@code SystemApi(client=...)}) も短名比較で吸収する。
     */
    private static boolean hasAnnotationShortName(List<String> annotations,
                                                    Set<String> names) {
        if (annotations == null) {
            return false;
        }
        for (String a : annotations) {
            if (a == null || a.isEmpty()) {
                continue;
            }
            String s = a;
            int paren = s.indexOf('(');
            if (paren >= 0) {
                s = s.substring(0, paren);
            }
            int dot = s.lastIndexOf('.');
            if (dot >= 0) {
                s = s.substring(dot + 1);
            }
            if (names.contains(s)) {
                return true;
            }
        }
        return false;
    }

    private AaosPattern() {
    }
}
