package padtools.core.formats.android;

import padtools.core.formats.uml.JavaClassInfo;
import padtools.core.formats.uml.JavaFieldInfo;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AAOS の Vehicle Property ID (整数) を定数名に解決するインデクサ。
 *
 * <p>{@code android.car.VehiclePropertyIds} や Vehicle HAL の
 * {@code VehicleProperty.aidl} に並ぶ {@code public static final int X = 12345;} /
 * {@code const int X = 12345;} のような定数宣言を抽出して
 * {@code int -> 定数名} のマップを構築する。シーケンス図の引数リテラルに
 * 名前を注釈付けする (例 {@code 291504647} →
 * {@code 291504647 /* PERF_VEHICLE_SPEED *&#47;}) ことで、VHAL フローを
 * 数値の意味を踏まえて追えるようにする。</p>
 *
 * <p>本クラスはパーサ自体を持たず、既に {@link JavaClassInfo} 列に
 * 変換済みの入力 (AIDL parser / Java 構造抽出済み) から定数フィールドを
 * 拾うだけ。AIDL の {@code const int} はパーサが {@link JavaClassInfo.Kind#AIDL_INTERFACE}
 * のフィールドとして取り込んでおり、Java 版 {@code VehiclePropertyIds} は
 * 通常のクラスとして取り込まれる前提。</p>
 */
public final class VehiclePropertyIndex {

    /** 認識する VHAL 定数定義クラスの単純名 (大小区別あり、include 前方一致)。 */
    private static final String[] RECOGNIZED_CLASS_NAMES = {
            "VehiclePropertyIds",
            "VehicleProperty",
    };

    /** 16 進 (0x...) または 10 進整数リテラル (末尾 L 可) を 1 つだけ含む initializer をマッチ。 */
    private static final Pattern INT_LITERAL_PATTERN = Pattern.compile(
            "^\\s*(0[xX][0-9a-fA-F_]+|[+-]?[0-9_]+)[lL]?\\s*$");

    /** 引数文字列中の整数リテラルを置換するためのトークナイザ用。 */
    private static final Pattern INT_TOKEN_PATTERN = Pattern.compile(
            "(?<![A-Za-z0-9_])(0[xX][0-9a-fA-F_]+|[0-9_]{4,})(?![A-Za-z0-9_])");

    private final Map<Integer, String> idToName;

    private VehiclePropertyIndex(Map<Integer, String> idToName) {
        this.idToName = idToName;
    }

    /**
     * 入力クラス列から VehicleProperty 系の定数定義を抽出してインデックスを構築する。
     * 該当クラスが無い場合は空のインデックス (lookup は常に空) を返す。
     */
    public static VehiclePropertyIndex build(List<JavaClassInfo> classes) {
        Map<Integer, String> map = new LinkedHashMap<>();
        if (classes == null) {
            return new VehiclePropertyIndex(map);
        }
        for (JavaClassInfo cls : classes) {
            if (!isRecognizedClass(cls)) {
                continue;
            }
            for (JavaFieldInfo f : cls.getFields()) {
                if (!isIntFieldType(f.getType())) {
                    continue;
                }
                if (f.getName() == null || !isUpperSnakeCase(f.getName())) {
                    continue;
                }
                Integer value = parseInt(f.getInitializer());
                if (value == null) {
                    continue;
                }
                // 後勝ちで上書き (vendor の定義と AOSP の定義が衝突するケースを許容)
                map.put(value, f.getName());
            }
        }
        return new VehiclePropertyIndex(Collections.unmodifiableMap(map));
    }

    /** 整数 ID から定数名を取得。 */
    public Optional<String> lookup(int propId) {
        return Optional.ofNullable(idToName.get(propId));
    }

    /** 抽出済み定数の総数。 */
    public int size() {
        return idToName.size();
    }

    /** インデックスが 1 件も持たないか。 */
    public boolean isEmpty() {
        return idToName.isEmpty();
    }

    /** 内部マップへの読み取り専用ビュー (テスト用)。 */
    public Map<Integer, String> asMap() {
        return idToName;
    }

    /**
     * 呼び出し引数の原文文字列を受け取り、整数リテラルを発見したら
     * 直後に {@code /* NAME *&#47;} のコメントを挿入した文字列を返す。
     * 解決できない整数リテラルや、整数を含まない引数はそのまま返す。
     */
    public String formatArg(String rawArgs) {
        if (rawArgs == null || rawArgs.isEmpty() || idToName.isEmpty()) {
            return rawArgs == null ? "" : rawArgs;
        }
        Matcher m = INT_TOKEN_PATTERN.matcher(rawArgs);
        StringBuilder out = new StringBuilder();
        int last = 0;
        while (m.find()) {
            Integer v = parseInt(m.group(1));
            String name = v == null ? null : idToName.get(v);
            out.append(rawArgs, last, m.end());
            if (name != null) {
                out.append(" /* ").append(name).append(" */");
            }
            last = m.end();
        }
        out.append(rawArgs, last, rawArgs.length());
        return out.toString();
    }

    private static boolean isRecognizedClass(JavaClassInfo cls) {
        String name = cls.getSimpleName();
        if (name == null) {
            return false;
        }
        for (String candidate : RECOGNIZED_CLASS_NAMES) {
            if (name.equals(candidate)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isIntFieldType(String type) {
        if (type == null) {
            return false;
        }
        String t = type.trim();
        return t.equals("int") || t.equals("Integer")
                || t.equals("long") || t.equals("Long");
    }

    private static boolean isUpperSnakeCase(String s) {
        if (s.isEmpty()) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean ok = (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_';
            if (!ok) {
                return false;
            }
        }
        return s.charAt(0) >= 'A' && s.charAt(0) <= 'Z';
    }

    /**
     * initializer が単一の整数リテラル (10 進 / 16 進 / アンダースコア区切り) であれば
     * その値を返す。式や複数項を含む場合は null。
     */
    static Integer parseInt(String text) {
        if (text == null) {
            return null;
        }
        Matcher m = INT_LITERAL_PATTERN.matcher(text);
        if (!m.matches()) {
            return null;
        }
        String raw = m.group(1).replace("_", "");
        try {
            if (raw.startsWith("0x") || raw.startsWith("0X")) {
                return (int) Long.parseLong(raw.substring(2), 16);
            }
            return (int) Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
