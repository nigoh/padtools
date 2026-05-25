// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package padtools.core.aosp;

import padtools.core.formats.java.AndroidProjectScanner;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AOSP の {@code Android.bp} (Soong Blueprint) ファイルから主要なモジュール宣言を抽出する。
 *
 * <p>厳密な Soong パーサではなく、最小限のキー (name, srcs, *_libs, *_deps) だけ
 * 取り出す軽量パーサ。複雑な式 (条件付き、map、include 解決) は無視する。</p>
 *
 * <p>抽出戦略:</p>
 * <ol>
 *   <li>コメント (行 {@code //} と ブロック {@code /* &#42;/}) を除去</li>
 *   <li>トップレベルの {@code <ident> { ... }} ブロックを順次切り出す</li>
 *   <li>各ブロック内で {@code name: "..."} と {@code srcs: [...]}, 各種 deps を抽出</li>
 * </ol>
 */
public final class AndroidBpParser {

    /** {@code shared_libs} / {@code static_libs} 等、依存とみなすキー一覧。 */
    private static final Set<String> DEP_KEYS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    "shared_libs", "static_libs", "libs", "java_libs",
                    "header_libs", "runtime_libs", "whole_static_libs",
                    "required", "defaults", "system_shared_libs",
                    "export_static_lib_headers", "export_shared_lib_headers")));

    private static final Pattern NAME_PATTERN = Pattern.compile(
            "\\bname\\s*:\\s*\"([^\"]+)\"");

    /** プロジェクト全体を走査し、見つかった全 {@code Android.bp} のモジュールを返す。 */
    public List<AndroidBpModule> analyzeProject(File projectRoot) {
        if (projectRoot == null || !projectRoot.isDirectory()) {
            return Collections.emptyList();
        }
        List<File> bpFiles = new ArrayList<>();
        collectBpFiles(projectRoot, bpFiles);
        List<AndroidBpModule> all = new ArrayList<>();
        for (File bp : bpFiles) {
            try {
                String src = AndroidProjectScanner.readFile(bp);
                all.addAll(parseSource(src, bp.getPath()));
            } catch (IOException ex) {
                // skip unreadable
            }
        }
        return all;
    }

    /** 1 ファイルの Android.bp ソースをパースして含まれるモジュールリストを返す。 */
    public List<AndroidBpModule> parseSource(String src, String filePath) {
        List<AndroidBpModule> out = new ArrayList<>();
        if (src == null || src.isEmpty()) {
            return out;
        }
        String stripped = stripComments(src);
        int i = 0;
        int n = stripped.length();
        while (i < n) {
            // skip whitespace
            while (i < n && Character.isWhitespace(stripped.charAt(i))) i++;
            if (i >= n) break;
            // read identifier
            int idStart = i;
            while (i < n && (Character.isLetterOrDigit(stripped.charAt(i))
                    || stripped.charAt(i) == '_')) {
                i++;
            }
            if (i == idStart) {
                // 非識別子文字に当たった → 1 文字飛ばして続行
                i++;
                continue;
            }
            String ident = stripped.substring(idStart, i);
            // skip whitespace
            while (i < n && Character.isWhitespace(stripped.charAt(i))) i++;
            if (i >= n || stripped.charAt(i) != '{') {
                // ブロックでない: 単純な代入や Bp 専用構文の可能性。スキップ。
                continue;
            }
            // ブロック範囲を取る
            int braceStart = i;
            int braceEnd = findMatchingBrace(stripped, braceStart);
            if (braceEnd <= braceStart) break;
            String body = stripped.substring(braceStart + 1, braceEnd);
            int lineOfStart = lineOf(src, mapStrippedOffset(src, stripped, idStart));
            AndroidBpModule mod = buildModule(ident, body, filePath, lineOfStart);
            if (mod != null && !mod.getName().isEmpty()) {
                out.add(mod);
            }
            i = braceEnd + 1;
        }
        return out;
    }

    private AndroidBpModule buildModule(String type, String body, String file,
                                          int line) {
        // package { } や license { } のように name 不要のものは name 抽出時に空文字
        Matcher nm = NAME_PATTERN.matcher(body);
        String name = nm.find() ? nm.group(1) : "";
        AndroidBpModule m = new AndroidBpModule(type, name, file, line);
        // srcs
        extractStringListProperty(body, "srcs", m.getSrcs());
        // deps
        for (String key : DEP_KEYS) {
            extractStringListProperty(body, key, m.getDeps());
        }
        return m;
    }

    /**
     * {@code key: ["a", "b", ...]} 形式のプロパティを抽出して {@code into} に追加する。
     * 単一文字列 ({@code key: "single"}) もサポート。
     */
    private static void extractStringListProperty(String body, String key,
                                                    List<String> into) {
        // \\b でキー境界を取り、続く : 後の値を解釈
        Pattern pat = Pattern.compile(
                "\\b" + Pattern.quote(key) + "\\s*:\\s*"
                        + "(?:\\[([^\\]]*)\\]|\"([^\"]+)\")");
        Matcher m = pat.matcher(body);
        while (m.find()) {
            String list = m.group(1);
            String single = m.group(2);
            if (single != null) {
                into.add(single);
                continue;
            }
            if (list == null) continue;
            Matcher s = Pattern.compile("\"([^\"]+)\"").matcher(list);
            while (s.find()) {
                into.add(s.group(1));
            }
        }
    }

    /** プロジェクト下を再帰走査して Android.bp を集める。 */
    private static void collectBpFiles(File dir, List<File> out) {
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File c : children) {
            if (c.isDirectory()) {
                // .git や build ディレクトリは除外
                String name = c.getName();
                if (name.equals(".git") || name.equals(".gradle")
                        || name.equals("build") || name.equals("out")) {
                    continue;
                }
                collectBpFiles(c, out);
            } else if (c.isFile() && c.getName().equals("Android.bp")) {
                out.add(c);
            }
        }
    }

    /** {@code //} と {@code /* &#42;/} コメントを空白に置換する (長さ保持)。 */
    static String stripComments(String src) {
        StringBuilder sb = new StringBuilder(src.length());
        int i = 0;
        int n = src.length();
        boolean inString = false;
        while (i < n) {
            char c = src.charAt(i);
            if (inString) {
                if (c == '\\' && i + 1 < n) {
                    sb.append(c).append(src.charAt(i + 1));
                    i += 2;
                    continue;
                }
                if (c == '"') inString = false;
                sb.append(c);
                i++;
                continue;
            }
            if (c == '"') {
                inString = true;
                sb.append(c);
                i++;
                continue;
            }
            if (c == '/' && i + 1 < n) {
                char d = src.charAt(i + 1);
                if (d == '/') {
                    // 行末まで空白に置換
                    int j = i;
                    while (j < n && src.charAt(j) != '\n') {
                        sb.append(' ');
                        j++;
                    }
                    i = j;
                    continue;
                } else if (d == '*') {
                    // 閉じ */ まで空白 (改行は保持)
                    int j = i + 2;
                    sb.append("  ");
                    while (j < n) {
                        if (src.charAt(j) == '\n') {
                            sb.append('\n');
                            j++;
                        } else if (j + 1 < n && src.charAt(j) == '*'
                                && src.charAt(j + 1) == '/') {
                            sb.append("  ");
                            j += 2;
                            break;
                        } else {
                            sb.append(' ');
                            j++;
                        }
                    }
                    i = j;
                    continue;
                }
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    private static int findMatchingBrace(String src, int open) {
        if (open < 0 || open >= src.length() || src.charAt(open) != '{') return open;
        int depth = 1;
        boolean inString = false;
        for (int i = open + 1; i < src.length(); i++) {
            char c = src.charAt(i);
            if (inString) {
                if (c == '\\' && i + 1 < src.length()) { i++; continue; }
                if (c == '"') inString = false;
                continue;
            }
            if (c == '"') inString = true;
            else if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return src.length();
    }

    /** コメント除去後オフセット → 元ソースのオフセット (長さ保持なので等しい)。 */
    private static int mapStrippedOffset(String origSrc, String strippedSrc,
                                          int strippedOffset) {
        // stripComments は長さ保持のため、オフセットは等価
        return Math.min(strippedOffset, origSrc.length());
    }

    private static int lineOf(String src, int offset) {
        int line = 1;
        for (int i = 0; i < offset && i < src.length(); i++) {
            if (src.charAt(i) == '\n') line++;
        }
        return line;
    }
}
