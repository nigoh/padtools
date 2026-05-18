package padtools.core.formats.android;

import org.junit.Test;
import padtools.core.formats.uml.AidlParser;
import padtools.core.formats.uml.JavaClassInfo;
import padtools.core.formats.uml.JavaStructureExtractor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * VehiclePropertyIndex のユニットテスト。
 * Java 版 VehiclePropertyIds と AIDL 版 VehicleProperty の両方を扱えることを確認する。
 */
public class VehiclePropertyIndexTest {

    @Test
    public void testEmptyInput() {
        VehiclePropertyIndex idx = VehiclePropertyIndex.build(new ArrayList<>());
        assertTrue(idx.isEmpty());
        assertEquals(0, idx.size());
        assertEquals(Optional.empty(), idx.lookup(123));
    }

    @Test
    public void testNullInput() {
        VehiclePropertyIndex idx = VehiclePropertyIndex.build(null);
        assertTrue(idx.isEmpty());
    }

    @Test
    public void testJavaVehiclePropertyIds() {
        String src =
                "package android.car;\n"
                        + "public final class VehiclePropertyIds {\n"
                        + "    public static final int PERF_VEHICLE_SPEED = 291504647;\n"
                        + "    public static final int FUEL_LEVEL = 0x11600307;\n"
                        + "    public static final int IGNITION_STATE = 289408009;\n"
                        + "    private static final String TAG = \"VehiclePropertyIds\";\n"
                        + "    private VehiclePropertyIds() {}\n"
                        + "}\n";
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(src);
        VehiclePropertyIndex idx = VehiclePropertyIndex.build(infos);
        assertEquals(3, idx.size());
        assertEquals(Optional.of("PERF_VEHICLE_SPEED"), idx.lookup(291504647));
        assertEquals(Optional.of("FUEL_LEVEL"), idx.lookup(0x11600307));
        assertEquals(Optional.of("IGNITION_STATE"), idx.lookup(289408009));
    }

    @Test
    public void testAidlVehicleProperty() {
        String aidl =
                "package android.hardware.automotive.vehicle;\n"
                        + "interface VehicleProperty {\n"
                        + "    const int PERF_VEHICLE_SPEED = 291504647;\n"
                        + "    const int FUEL_LEVEL = 0x11600307;\n"
                        + "}\n";
        List<JavaClassInfo> infos = AidlParser.parse(aidl);
        VehiclePropertyIndex idx = VehiclePropertyIndex.build(infos);
        assertEquals(Optional.of("PERF_VEHICLE_SPEED"), idx.lookup(291504647));
        assertEquals(Optional.of("FUEL_LEVEL"), idx.lookup(0x11600307));
    }

    @Test
    public void testIgnoresNonRecognizedClasses() {
        String src =
                "package com.example;\n"
                        + "public final class Random {\n"
                        + "    public static final int FOO = 291504647;\n"
                        + "}\n";
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(src);
        VehiclePropertyIndex idx = VehiclePropertyIndex.build(infos);
        assertTrue(idx.isEmpty());
    }

    @Test
    public void testIgnoresExpressionInitializers() {
        // 単純な数値リテラルだけを受け付ける (式や定数参照は無視)
        String src =
                "package android.car;\n"
                        + "public final class VehiclePropertyIds {\n"
                        + "    public static final int A = 1 + 2;\n"
                        + "    public static final int B = OTHER_CONST;\n"
                        + "    public static final int C = 12345;\n"
                        + "}\n";
        VehiclePropertyIndex idx = VehiclePropertyIndex.build(JavaStructureExtractor.extract(src));
        assertEquals(1, idx.size());
        assertEquals(Optional.of("C"), idx.lookup(12345));
    }

    @Test
    public void testIgnoresNonIntFields() {
        String src =
                "package android.car;\n"
                        + "public final class VehiclePropertyIds {\n"
                        + "    public static final String TAG = \"x\";\n"
                        + "    public static final double D = 1.0;\n"
                        + "    public static final int OK = 99;\n"
                        + "}\n";
        VehiclePropertyIndex idx = VehiclePropertyIndex.build(JavaStructureExtractor.extract(src));
        assertEquals(1, idx.size());
        assertEquals(Optional.of("OK"), idx.lookup(99));
    }

    @Test
    public void testIgnoresLowerCaseAndMixedCaseNames() {
        String src =
                "package android.car;\n"
                        + "public final class VehiclePropertyIds {\n"
                        + "    public static final int notUpperSnake = 1;\n"
                        + "    public static final int Capital = 2;\n"
                        + "    public static final int UPPER_OK = 3;\n"
                        + "}\n";
        VehiclePropertyIndex idx = VehiclePropertyIndex.build(JavaStructureExtractor.extract(src));
        assertEquals(1, idx.size());
        assertEquals(Optional.of("UPPER_OK"), idx.lookup(3));
    }

    @Test
    public void testFormatArgInsertsComment() {
        String src =
                "package android.car;\n"
                        + "public final class VehiclePropertyIds {\n"
                        + "    public static final int PERF_VEHICLE_SPEED = 291504647;\n"
                        + "}\n";
        VehiclePropertyIndex idx = VehiclePropertyIndex.build(JavaStructureExtractor.extract(src));
        assertEquals("291504647 /* PERF_VEHICLE_SPEED */", idx.formatArg("291504647"));
        assertEquals("291504647 /* PERF_VEHICLE_SPEED */, listener",
                idx.formatArg("291504647, listener"));
    }

    @Test
    public void testFormatArgWithUnknownLiteralIsUnchanged() {
        String src =
                "package android.car;\n"
                        + "public final class VehiclePropertyIds {\n"
                        + "    public static final int PERF_VEHICLE_SPEED = 291504647;\n"
                        + "}\n";
        VehiclePropertyIndex idx = VehiclePropertyIndex.build(JavaStructureExtractor.extract(src));
        // 7777 は登録されていないので変化なし
        assertEquals("7777", idx.formatArg("7777"));
        // 短すぎる数値 (3 桁以下) はそもそも置換候補から外す
        assertEquals("getProperty(1)", "getProperty(" + idx.formatArg("1") + ")");
    }

    @Test
    public void testFormatArgEmptyInput() {
        VehiclePropertyIndex idx = VehiclePropertyIndex.build(new ArrayList<>());
        assertEquals("", idx.formatArg(""));
        assertEquals("", idx.formatArg(null));
        // インデックス自体が空なら何も置換しない
        assertEquals("291504647", idx.formatArg("291504647"));
    }

    @Test
    public void testParseIntAcceptsHexAndUnderscore() {
        assertEquals(Integer.valueOf(0x11600307), VehiclePropertyIndex.parseInt("0x11600307"));
        assertEquals(Integer.valueOf(0x11600307), VehiclePropertyIndex.parseInt("0X11_600_307"));
        assertEquals(Integer.valueOf(291504647), VehiclePropertyIndex.parseInt("291504647"));
        assertEquals(Integer.valueOf(291504647), VehiclePropertyIndex.parseInt("291_504_647"));
        assertEquals(Integer.valueOf(-1), VehiclePropertyIndex.parseInt("-1"));
        assertNull(VehiclePropertyIndex.parseInt("1 + 2"));
        assertNull(VehiclePropertyIndex.parseInt("FOO"));
        assertNull(VehiclePropertyIndex.parseInt(null));
        assertNull(VehiclePropertyIndex.parseInt(""));
    }
}
