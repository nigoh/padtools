// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.aosp;

import java.util.ArrayList;
import java.util.List;

/**
 * Android.bp (Soong Blueprint) で宣言された 1 モジュール分の情報。
 *
 * <p>Soong は JSON 風だが厳密には Bazel/Starlark 風の構文を持つ。本パーサは
 * 全 property を保持せず、依存解析に必要な主要キー (name, srcs, *_libs/*_deps) のみ抽出する。</p>
 */
public final class AndroidBpModule {

    private final String type;
    private final String name;
    private final List<String> srcs = new ArrayList<>();
    /**
     * 結合した依存リスト。{@code shared_libs}, {@code static_libs},
     * {@code libs}, {@code java_libs}, {@code header_libs}, {@code defaults},
     * {@code required} を 1 つに集約する。
     */
    private final List<String> deps = new ArrayList<>();
    private final String file;
    private final int lineHint;

    public AndroidBpModule(String type, String name, String file, int lineHint) {
        this.type = type == null ? "" : type;
        this.name = name == null ? "" : name;
        this.file = file == null ? "" : file;
        this.lineHint = lineHint;
    }

    /** モジュール種別 ({@code cc_library}, {@code java_library}, {@code android_app} 等)。 */
    public String getType() { return type; }
    public String getName() { return name; }
    public List<String> getSrcs() { return srcs; }
    public List<String> getDeps() { return deps; }
    public String getFile() { return file; }
    public int getLineHint() { return lineHint; }

    /** モジュール種別を大分類する: cc / java / android / aidl / hidl / その他。 */
    public String getCategory() {
        if (type.startsWith("cc_") || type.startsWith("ndk_")
                || type.equals("cc_binary") || type.equals("cc_test")) {
            return "cc";
        }
        if (type.startsWith("java_") || type.equals("java_library")
                || type.equals("java_binary") || type.equals("java_defaults")) {
            return "java";
        }
        if (type.startsWith("android_") || type.equals("android_app")
                || type.equals("android_library")) {
            return "android";
        }
        if (type.contains("aidl")) {
            return "aidl";
        }
        if (type.contains("hidl")) {
            return "hidl";
        }
        if (type.startsWith("filegroup") || type.startsWith("genrule")) {
            return "build";
        }
        return "other";
    }

    @Override
    public String toString() {
        return type + " { name: \"" + name + "\" }"
                + (file.isEmpty() ? "" : " @ " + file + ":" + lineHint);
    }
}
