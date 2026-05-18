package padtools.core.formats.android;

import java.util.Locale;

/**
 * AOSP のモジュール配置 partition。
 *
 * <p>Soong (Android.bp) のモジュール属性
 * ({@code vendor: true}, {@code product_specific: true} 等) と、Bp ファイル自身の
 * パス (例: {@code vendor/}, {@code product/}, {@code system_ext/} を含む)
 * の両方から推定する。両方が指定された場合は属性側を優先する。</p>
 */
public enum Partition {

    SYSTEM,
    VENDOR,
    PRODUCT,
    ODM,
    SYSTEM_EXT,
    UNKNOWN;

    /**
     * Bp の属性から partition を判定する。属性で確定できない場合は
     * {@link #fromPath(String)} のフォールバックを呼び出す側で組み合わせて使う。
     */
    public static Partition fromAttributes(boolean vendor, boolean productSpecific,
                                            boolean systemExtSpecific, boolean odm,
                                            boolean proprietary) {
        if (vendor || proprietary) {
            return VENDOR;
        }
        if (productSpecific) {
            return PRODUCT;
        }
        if (systemExtSpecific) {
            return SYSTEM_EXT;
        }
        if (odm) {
            return ODM;
        }
        return UNKNOWN;
    }

    /**
     * Bp ファイルのフルパスから partition を推定する。
     * AOSP のディレクトリ規約 ({@code system/}, {@code vendor/}, {@code product/},
     * {@code odm/}, {@code system_ext/}) と一致する場合に対応する partition を返す。
     */
    public static Partition fromPath(String bpFilePath) {
        if (bpFilePath == null || bpFilePath.isEmpty()) {
            return UNKNOWN;
        }
        String p = bpFilePath.replace('\\', '/').toLowerCase(Locale.ROOT);
        if (containsSegment(p, "vendor")) {
            return VENDOR;
        }
        if (containsSegment(p, "product")) {
            return PRODUCT;
        }
        if (containsSegment(p, "odm")) {
            return ODM;
        }
        if (containsSegment(p, "system_ext")) {
            return SYSTEM_EXT;
        }
        if (containsSegment(p, "system")) {
            return SYSTEM;
        }
        return UNKNOWN;
    }

    private static boolean containsSegment(String fullPath, String segment) {
        return fullPath.contains("/" + segment + "/")
                || fullPath.startsWith(segment + "/")
                || fullPath.endsWith("/" + segment);
    }
}
