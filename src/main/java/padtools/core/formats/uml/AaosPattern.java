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

    private AaosPattern() {
    }
}
