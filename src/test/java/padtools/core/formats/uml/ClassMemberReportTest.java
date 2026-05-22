package padtools.core.formats.uml;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ClassMemberReportTest {

    @Test
    public void render_listsSimpleClassNameFieldsAndMethods() {
        List<JavaClassInfo> classes = JavaStructureExtractor.extract(
                "package com.example.app;"
                + " public class Foo {"
                + " private final int count = 0;"
                + " public int add(int a, int b) { return a + b; } }");
        String text = ClassMemberReport.render(classes);

        // 単純名を見出しにする (FQN/URI は使わない)
        assertTrue(text, text.contains("Foo  [class]"));
        assertFalse(text, text.contains("com.example.app.Foo"));
        // パッケージは補足として残す
        assertTrue(text, text.contains("(com.example.app)"));

        // フィールド・メソッドをそれぞれ列挙する
        assertTrue(text, text.contains("フィールド"));
        assertTrue(text, text.contains("- count: int {final}"));
        assertTrue(text, text.contains("メソッド"));
        assertTrue(text, text.contains("+ add(a: int, b: int): int"));
    }

    @Test
    public void render_includesEnumConstants() {
        List<JavaClassInfo> classes = JavaStructureExtractor.extract(
                "package x; public enum Color { RED, GREEN, BLUE }");
        String text = ClassMemberReport.render(classes);
        assertTrue(text, text.contains("Color  [enum]"));
        assertTrue(text, text.contains("列挙定数"));
        assertTrue(text, text.contains("RED, GREEN, BLUE"));
    }

    @Test
    public void render_emptyInput_emitsHeaderWithZeroClasses() {
        String text = ClassMemberReport.render(java.util.Collections.emptyList());
        assertTrue(text, text.contains("全クラスのメンバー一覧"));
        assertTrue(text, text.contains("クラス数: 0"));
    }
}
