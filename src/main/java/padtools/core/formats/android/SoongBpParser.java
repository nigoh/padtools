package padtools.core.formats.android;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Soong Blueprint ({@code Android.bp}) の最小サブセットパーサ。
 *
 * <p>AOSP のビルドシステム Soong は Blueprint 言語で記述される。完全な構文解析は
 * しないが、AOSP/AAOS の partition 配置 (system / vendor / product / odm / system_ext)
 * とモジュール間依存を抽出するのに十分なレベルの解析を提供する。</p>
 *
 * <p>対応するモジュール宣言の例:</p>
 * <pre>
 * cc_library_shared {
 *     name: "libfoo",
 *     srcs: ["foo.cpp"],
 *     vendor: true,
 *     shared_libs: ["liblog"],
 * }
 * java_library {
 *     name: "android.car",
 *     srcs: ["src/&#42;&#42;/&#42;.java"],
 *     defaults: ["car-defaults"],
 * }
 * </pre>
 *
 * <p>サポート外の構文 ({@code += } による追記, 演算子の混入, バッククォート,
 * 関数呼び出し) は黙ってスキップする (パース失敗で全体を捨てるよりも
 * 一部だけ取れる方が有用なため)。</p>
 */
public final class SoongBpParser {

    /** Soong で頻出のモジュール型 (これ以外も name + body は同じ手順で拾える)。 */
    public static final Set<String> KNOWN_MODULE_TYPES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    "cc_library", "cc_library_static", "cc_library_shared", "cc_library_host_shared",
                    "cc_library_host_static", "cc_binary", "cc_binary_host", "cc_test",
                    "java_library", "java_library_static", "java_binary", "java_binary_host",
                    "java_defaults", "java_test", "java_test_host", "java_sdk_library",
                    "android_app", "android_library", "android_test", "android_app_import",
                    "aidl_interface", "hidl_interface", "hidl_package_root",
                    "prebuilt_etc", "prebuilt_root", "prebuilt_apex", "apex", "apex_defaults",
                    "filegroup", "genrule", "soong_namespace")));

    /** {@code identifier {} の前置キーワード抽出。 */
    private static final Pattern MODULE_HEADER = Pattern.compile(
            "(?m)(^|\\n)\\s*([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\{");

    private SoongBpParser() {
    }

    /**
     * Bp ソースをパースして含まれる SoongModuleInfo のリストを返す。
     * {@code filePath} は partition 推定のフォールバックに使われる (null 可)。
     */
    public static List<SoongModuleInfo> parse(String source, String filePath) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        String src = stripComments(source);
        List<SoongModuleInfo> result = new ArrayList<>();
        Matcher headerMatcher = MODULE_HEADER.matcher(src);
        while (headerMatcher.find()) {
            String moduleType = headerMatcher.group(2);
            int bodyOpen = headerMatcher.end() - 1; // {
            int bodyClose = findMatchingBrace(src, bodyOpen);
            if (bodyClose < 0) {
                break;
            }
            String body = src.substring(bodyOpen + 1, bodyClose);
            SoongModuleInfo info = parseModuleBody(moduleType, body, filePath);
            if (info != null) {
                result.add(info);
            }
            // 次の検索開始位置を bodyClose 以降に移動
            headerMatcher.region(bodyClose + 1, src.length());
        }
        return result;
    }

    /** 1 つのモジュールの中身 (波カッコの中) をパース。 */
    private static SoongModuleInfo parseModuleBody(String moduleType, String body, String filePath) {
        // 既知のモジュール型でないものはスキップ (variable assignment や soong_config_module_type 等)
        if (!KNOWN_MODULE_TYPES.contains(moduleType)) {
            return null;
        }
        SoongModuleInfo info = new SoongModuleInfo();
        info.setModuleType(moduleType);
        info.setBpFilePath(filePath);
        boolean vendor = false;
        boolean productSpecific = false;
        boolean systemExtSpecific = false;
        boolean odm = false;
        boolean proprietary = false;
        List<KeyValue> entries = scanKeyValues(body);
        Set<String> depSet = new LinkedHashSet<>();
        for (KeyValue kv : entries) {
            switch (kv.key) {
                case "name":
                    info.setName(stripQuotes(kv.value));
                    break;
                case "srcs":
                    info.getSrcs().addAll(parseStringArray(kv.value));
                    break;
                case "static_libs":
                case "shared_libs":
                case "libs":
                case "header_libs":
                case "whole_static_libs":
                    depSet.addAll(parseStringArray(kv.value));
                    break;
                case "defaults":
                    info.getDefaults().addAll(parseStringArray(kv.value));
                    break;
                case "vendor":
                    vendor = parseBool(kv.value);
                    break;
                case "product_specific":
                    productSpecific = parseBool(kv.value);
                    break;
                case "system_ext_specific":
                    systemExtSpecific = parseBool(kv.value);
                    break;
                case "device_specific":
                    // device_specific=true は ODM 配置の典型 (より厳密には ODM だが)
                    odm = parseBool(kv.value);
                    break;
                case "proprietary":
                    proprietary = parseBool(kv.value);
                    break;
                default:
                    break;
            }
        }
        info.getDeps().addAll(depSet);
        Partition fromAttr = Partition.fromAttributes(vendor, productSpecific,
                systemExtSpecific, odm, proprietary);
        if (fromAttr != Partition.UNKNOWN) {
            info.setPartition(fromAttr);
        } else {
            info.setPartition(Partition.fromPath(filePath));
        }
        return info;
    }

    /**
     * モジュール本体から {@code key: value,} 形式の単純なエントリを取り出す。
     * ネストしたオブジェクト (例: {@code arch: { arm: { ... } }}) の中までは入らない
     * (本パーサのスコープ外)。
     */
    private static List<KeyValue> scanKeyValues(String body) {
        List<KeyValue> out = new ArrayList<>();
        int i = 0;
        int n = body.length();
        while (i < n) {
            // 識別子 + コロンを探す
            while (i < n && Character.isWhitespace(body.charAt(i))) {
                i++;
            }
            int keyStart = i;
            while (i < n && (Character.isLetterOrDigit(body.charAt(i)) || body.charAt(i) == '_')) {
                i++;
            }
            if (i == keyStart) {
                i++; // skip non-identifier char
                continue;
            }
            String key = body.substring(keyStart, i);
            while (i < n && Character.isWhitespace(body.charAt(i))) {
                i++;
            }
            if (i >= n || body.charAt(i) != ':') {
                continue;
            }
            i++; // consume :
            while (i < n && Character.isWhitespace(body.charAt(i))) {
                i++;
            }
            int valStart = i;
            int valEnd = findValueEnd(body, valStart);
            String value = body.substring(valStart, valEnd).trim();
            // 末尾のカンマを除去
            if (value.endsWith(",")) {
                value = value.substring(0, value.length() - 1).trim();
            }
            out.add(new KeyValue(key, value));
            i = valEnd;
            // 終端カンマも消費
            while (i < n && (body.charAt(i) == ',' || Character.isWhitespace(body.charAt(i)))) {
                i++;
            }
        }
        return out;
    }

    /**
     * value の終了位置を探す。文字列・括弧・ブレースの中の カンマ/改行 は無視する。
     */
    private static int findValueEnd(String body, int from) {
        int paren = 0, brack = 0, brace = 0;
        int i = from;
        int n = body.length();
        while (i < n) {
            char c = body.charAt(i);
            if (c == '"' || c == '\'') {
                i = skipString(body, i, c);
                continue;
            }
            if (c == '(') paren++;
            else if (c == ')' && paren > 0) paren--;
            else if (c == '[') brack++;
            else if (c == ']' && brack > 0) brack--;
            else if (c == '{') brace++;
            else if (c == '}' && brace > 0) brace--;
            else if (paren == 0 && brack == 0 && brace == 0 && c == ',') {
                return i;
            }
            i++;
        }
        return n;
    }

    private static int skipString(String s, int start, char quote) {
        int i = start + 1;
        int n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < n) {
                i += 2;
                continue;
            }
            if (c == quote) {
                return i + 1;
            }
            i++;
        }
        return n;
    }

    /**
     * src を行/ブロックコメントなしの形に書き換える (オリジナルの文字位置は維持しないが
     * パース用途では問題ない)。
     */
    private static String stripComments(String src) {
        StringBuilder sb = new StringBuilder(src.length());
        int i = 0;
        int n = src.length();
        while (i < n) {
            char c = src.charAt(i);
            if (c == '/' && i + 1 < n && src.charAt(i + 1) == '/') {
                while (i < n && src.charAt(i) != '\n') {
                    i++;
                }
            } else if (c == '/' && i + 1 < n && src.charAt(i + 1) == '*') {
                i += 2;
                while (i + 1 < n && !(src.charAt(i) == '*' && src.charAt(i + 1) == '/')) {
                    i++;
                }
                i = Math.min(n, i + 2);
            } else if (c == '"' || c == '\'') {
                int end = skipString(src, i, c);
                sb.append(src, i, end);
                i = end;
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }

    /** ブレース対応 (start が開きブレースの位置を指している前提)。 */
    private static int findMatchingBrace(String src, int start) {
        int depth = 1;
        int i = start + 1;
        int n = src.length();
        while (i < n) {
            char c = src.charAt(i);
            if (c == '"' || c == '\'') {
                i = skipString(src, i, c);
                continue;
            }
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
            i++;
        }
        return -1;
    }

    /** {@code ["a", "b"]} 形式の文字列を List<String> に変換。改行/コメントは事前に除去済み前提。 */
    private static List<String> parseStringArray(String value) {
        List<String> result = new ArrayList<>();
        if (value == null) {
            return result;
        }
        String trimmed = value.trim();
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) {
            return result;
        }
        String inner = trimmed.substring(1, trimmed.length() - 1);
        int i = 0;
        int n = inner.length();
        while (i < n) {
            while (i < n && (Character.isWhitespace(inner.charAt(i)) || inner.charAt(i) == ',')) {
                i++;
            }
            if (i >= n) {
                break;
            }
            char c = inner.charAt(i);
            if (c == '"' || c == '\'') {
                int end = skipString(inner, i, c);
                String s = inner.substring(i + 1, end - 1);
                result.add(s);
                i = end;
            } else {
                i++;
            }
        }
        return result;
    }

    private static String stripQuotes(String s) {
        if (s == null) {
            return "";
        }
        String t = s.trim();
        if ((t.startsWith("\"") && t.endsWith("\"") && t.length() >= 2)
                || (t.startsWith("'") && t.endsWith("'") && t.length() >= 2)) {
            return t.substring(1, t.length() - 1);
        }
        return t;
    }

    private static boolean parseBool(String value) {
        if (value == null) {
            return false;
        }
        return value.trim().equalsIgnoreCase("true");
    }

    private static final class KeyValue {
        final String key;
        final String value;

        KeyValue(String key, String value) {
            this.key = key;
            this.value = value;
        }
    }
}
