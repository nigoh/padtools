package padtools.core.formats.android;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * SoongBpParser のユニットテスト。
 */
public class SoongBpParserTest {

    @Test
    public void testNullAndEmpty() {
        assertTrue(SoongBpParser.parse(null, "Android.bp").isEmpty());
        assertTrue(SoongBpParser.parse("", "Android.bp").isEmpty());
    }

    @Test
    public void testSimpleCcLibrary() {
        String bp =
                "cc_library_shared {\n"
                        + "    name: \"libfoo\",\n"
                        + "    srcs: [\"foo.cpp\"],\n"
                        + "    vendor: true,\n"
                        + "    shared_libs: [\"liblog\", \"libcutils\"],\n"
                        + "}\n";
        List<SoongModuleInfo> result = SoongBpParser.parse(bp, "/vendor/foo/Android.bp");
        assertEquals(1, result.size());
        SoongModuleInfo m = result.get(0);
        assertEquals("cc_library_shared", m.getModuleType());
        assertEquals("libfoo", m.getName());
        assertEquals(Partition.VENDOR, m.getPartition());
        assertEquals(1, m.getSrcs().size());
        assertEquals("foo.cpp", m.getSrcs().get(0));
        assertEquals(2, m.getDeps().size());
        assertTrue(m.getDeps().contains("liblog"));
        assertTrue(m.getDeps().contains("libcutils"));
    }

    @Test
    public void testJavaLibraryWithDefaults() {
        String bp =
                "java_library {\n"
                        + "    name: \"android.car\",\n"
                        + "    srcs: [\"src/**/*.java\"],\n"
                        + "    defaults: [\"car-defaults\"],\n"
                        + "    static_libs: [\"foo-lib\"],\n"
                        + "}\n";
        SoongModuleInfo m = SoongBpParser.parse(bp, "packages/services/Car/Android.bp").get(0);
        assertEquals("android.car", m.getName());
        assertEquals(1, m.getDefaults().size());
        assertEquals("car-defaults", m.getDefaults().get(0));
        assertEquals(1, m.getDeps().size());
        assertEquals("foo-lib", m.getDeps().get(0));
    }

    @Test
    public void testProductSpecificPartition() {
        String bp =
                "android_app {\n"
                        + "    name: \"MyApp\",\n"
                        + "    product_specific: true,\n"
                        + "}\n";
        SoongModuleInfo m = SoongBpParser.parse(bp, "product/myproduct/Android.bp").get(0);
        assertEquals(Partition.PRODUCT, m.getPartition());
    }

    @Test
    public void testSystemExtPartition() {
        String bp =
                "java_library {\n"
                        + "    name: \"lib1\",\n"
                        + "    system_ext_specific: true,\n"
                        + "}\n";
        SoongModuleInfo m = SoongBpParser.parse(bp, "x/Android.bp").get(0);
        assertEquals(Partition.SYSTEM_EXT, m.getPartition());
    }

    @Test
    public void testPathFallbackForPartition() {
        // 属性で確定できない場合はパスから推定
        String bp = "cc_library { name: \"libplain\", }\n";
        assertEquals(Partition.VENDOR,
                SoongBpParser.parse(bp, "/vendor/libs/Android.bp").get(0).getPartition());
        assertEquals(Partition.SYSTEM,
                SoongBpParser.parse(bp, "/system/core/Android.bp").get(0).getPartition());
        assertEquals(Partition.SYSTEM_EXT,
                SoongBpParser.parse(bp, "/system_ext/foo/Android.bp").get(0).getPartition());
        assertEquals(Partition.UNKNOWN,
                SoongBpParser.parse(bp, "/some/random/path/Android.bp").get(0).getPartition());
    }

    @Test
    public void testMultipleModulesInOneFile() {
        String bp =
                "cc_library_shared { name: \"a\", }\n"
                        + "cc_library_shared { name: \"b\", vendor: true, }\n"
                        + "java_library { name: \"c\", }\n";
        List<SoongModuleInfo> result = SoongBpParser.parse(bp, "Android.bp");
        assertEquals(3, result.size());
        assertEquals("a", result.get(0).getName());
        assertEquals("b", result.get(1).getName());
        assertEquals(Partition.VENDOR, result.get(1).getPartition());
        assertEquals("java_library", result.get(2).getModuleType());
    }

    @Test
    public void testCommentsAreIgnored() {
        String bp =
                "// outer comment\n"
                        + "cc_library {\n"
                        + "    /* block comment */\n"
                        + "    name: \"libcomment\", // inline\n"
                        + "    vendor: true, // partition marker\n"
                        + "}\n";
        SoongModuleInfo m = SoongBpParser.parse(bp, "Android.bp").get(0);
        assertEquals("libcomment", m.getName());
        assertEquals(Partition.VENDOR, m.getPartition());
    }

    @Test
    public void testUnknownModuleTypeIsSkipped() {
        // soong_config_module_type や variable assignment はスキップ (KNOWN リスト外)
        String bp =
                "soong_config_module_type {\n"
                        + "    name: \"custom_type\",\n"
                        + "}\n"
                        + "cc_library {\n"
                        + "    name: \"real_lib\",\n"
                        + "}\n";
        List<SoongModuleInfo> result = SoongBpParser.parse(bp, "Android.bp");
        // ↑ "soong_config_module_type" は KNOWN_MODULE_TYPES に soong_namespace のみ含めているので除外
        // よって result は 1 件 (cc_library のみ)
        assertEquals(1, result.size());
        assertEquals("real_lib", result.get(0).getName());
    }

    @Test
    public void testNestedBracesAreSkipped() {
        // arch.arm.cflags 等のネストブロックがあってもクラッシュせず、トップレベルのみ拾う
        String bp =
                "cc_library {\n"
                        + "    name: \"libnested\",\n"
                        + "    arch: {\n"
                        + "        arm: { cflags: [\"-DA\"], },\n"
                        + "        arm64: { cflags: [\"-DB\"], },\n"
                        + "    },\n"
                        + "    vendor: true,\n"
                        + "}\n";
        SoongModuleInfo m = SoongBpParser.parse(bp, "Android.bp").get(0);
        assertEquals("libnested", m.getName());
        assertEquals(Partition.VENDOR, m.getPartition());
    }
}
