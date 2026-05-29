// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.aosp;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Android.bp (Soong) パーサのテスト。
 */
public class AndroidBpParserTest {

    @Test
    public void parsesSingleCcLibrary() {
        String src = "cc_library {\n"
                + "    name: \"libfoo\",\n"
                + "    srcs: [\"foo.cpp\", \"bar.cpp\"],\n"
                + "    shared_libs: [\"libbase\"],\n"
                + "    static_libs: [\"libutils\"],\n"
                + "}\n";
        List<AndroidBpModule> mods = new AndroidBpParser().parseSource(src, "Android.bp");
        assertEquals(1, mods.size());
        AndroidBpModule m = mods.get(0);
        assertEquals("cc_library", m.getType());
        assertEquals("libfoo", m.getName());
        assertEquals(2, m.getSrcs().size());
        assertTrue(m.getSrcs().contains("foo.cpp"));
        assertTrue(m.getDeps().contains("libbase"));
        assertTrue(m.getDeps().contains("libutils"));
        assertEquals("cc", m.getCategory());
    }

    @Test
    public void parsesMultipleModules() {
        String src = "java_library {\n"
                + "    name: \"MyLib\",\n"
                + "    srcs: [\"src/main/java/**/*.java\"],\n"
                + "    libs: [\"androidx\"],\n"
                + "}\n"
                + "\n"
                + "android_app {\n"
                + "    name: \"MyApp\",\n"
                + "    static_libs: [\"MyLib\"],\n"
                + "}\n";
        List<AndroidBpModule> mods = new AndroidBpParser().parseSource(src, "Android.bp");
        assertEquals(2, mods.size());
        assertEquals("java", mods.get(0).getCategory());
        assertEquals("android", mods.get(1).getCategory());
        assertTrue(mods.get(1).getDeps().contains("MyLib"));
    }

    @Test
    public void ignoresComments() {
        String src = "// top-level comment\n"
                + "cc_library {\n"
                + "    name: \"libfoo\",\n"
                + "    /* block comment\n"
                + "       multi line */\n"
                + "    srcs: [\"foo.cpp\"], // trailing\n"
                + "}\n";
        List<AndroidBpModule> mods = new AndroidBpParser().parseSource(src, "Android.bp");
        assertEquals(1, mods.size());
        assertEquals("libfoo", mods.get(0).getName());
        assertEquals(1, mods.get(0).getSrcs().size());
    }

    @Test
    public void handlesNestedBlocks() {
        String src = "cc_library {\n"
                + "    name: \"libfoo\",\n"
                + "    target: {\n"
                + "        android: {\n"
                + "            srcs: [\"android_only.cpp\"],\n"
                + "        },\n"
                + "    },\n"
                + "    srcs: [\"common.cpp\"],\n"
                + "}\n";
        List<AndroidBpModule> mods = new AndroidBpParser().parseSource(src, "Android.bp");
        assertEquals(1, mods.size());
        // 全 srcs プロパティを集めるので両方拾える
        assertTrue(mods.get(0).getSrcs().contains("common.cpp"));
        assertTrue(mods.get(0).getSrcs().contains("android_only.cpp"));
    }

    @Test
    public void diagramOutputContainsModuleAndEdge() {
        String src = "cc_library { name: \"libfoo\", shared_libs: [\"libbar\"] }\n"
                + "cc_library { name: \"libbar\" }\n";
        List<AndroidBpModule> mods = new AndroidBpParser().parseSource(src, "Android.bp");
        String puml = PlantUmlSoongDependencyDiagram.render(mods);
        assertTrue(puml.startsWith("@startuml"));
        assertTrue(puml.contains("@enduml"));
        assertTrue(puml.contains("libfoo"));
        assertTrue(puml.contains("libbar"));
        // edge: libfoo --> libbar
        assertTrue(puml.contains("m_libfoo --> m_libbar"));
    }

    @Test
    public void externalDepGoesIntoExternalGroup() {
        String src = "cc_library { name: \"libfoo\", shared_libs: [\"libsystemexternal\"] }\n";
        List<AndroidBpModule> mods = new AndroidBpParser().parseSource(src, "Android.bp");
        String puml = PlantUmlSoongDependencyDiagram.render(mods);
        assertTrue(puml.contains("package \"external\""));
        assertTrue(puml.contains("libsystemexternal"));
    }

    @Test
    public void markdownReportSummarizesByCategory() {
        String src = "cc_library { name: \"libfoo\" }\n"
                + "java_library { name: \"BarLib\", libs: [\"libfoo\"] }\n";
        List<AndroidBpModule> mods = new AndroidBpParser().parseSource(src, "Android.bp");
        String md = MarkdownSoongReport.render(mods);
        assertTrue(md.contains("Soong"));
        assertTrue(md.contains("cc"));
        assertTrue(md.contains("java"));
        assertTrue(md.contains("libfoo"));
        assertTrue(md.contains("BarLib"));
    }

    @Test
    public void emptyProjectRendersPlaceholder() {
        String md = MarkdownSoongReport.render(new java.util.ArrayList<>());
        assertNotNull(md);
        assertTrue(md.contains("no Android.bp"));
    }

    @Test
    public void stripCommentsPreservesLength() {
        String src = "abc /* xxx */ def // tail\nghi";
        String stripped = AndroidBpParser.stripComments(src);
        assertEquals(src.length(), stripped.length());
        // 元のオフセット位置で行情報が壊れていないこと
        assertEquals('\n', stripped.charAt(src.indexOf('\n')));
    }
}
