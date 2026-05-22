package padtools.core.formats.uml;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ClassMemberReportTest {

    @Test
    public void render_csvHeaderAndSimpleClassNameRows() {
        List<JavaClassInfo> classes = JavaStructureExtractor.extract(
                "package com.example.app;"
                + " public class Foo {"
                + " private final int count = 0;"
                + " public int add(int a, int b) { return a + b; } }");
        String csv = ClassMemberReport.render(classes);

        assertTrue(csv, csv.startsWith(
                "class,package,kind,member,visibility,name,type,params,modifiers\n"));
        // クラスは単純名カラム + パッケージ別カラム (FQN/URI は使わない)
        assertTrue(csv, csv.contains("Foo,com.example.app,class,field,"));
        assertFalse(csv, csv.contains("com.example.app.Foo"));
        // フィールド行
        assertTrue(csv, csv.contains("field,private,count,int,,final"));
        // メソッド行 (params にカンマを含むためクォートされる)
        assertTrue(csv, csv.contains("method,public,add,int,\"a: int, b: int\","));
    }

    @Test
    public void render_enumConstantsAsRows() {
        List<JavaClassInfo> classes = JavaStructureExtractor.extract(
                "package x; public enum Color { RED, GREEN, BLUE }");
        String csv = ClassMemberReport.render(classes);
        assertTrue(csv, csv.contains("Color,x,enum,enum-constant,public,RED,,,"));
        assertTrue(csv, csv.contains("enum-constant,public,GREEN"));
        assertTrue(csv, csv.contains("enum-constant,public,BLUE"));
    }

    @Test
    public void render_emptyInput_emitsHeaderOnly() {
        String csv = ClassMemberReport.render(java.util.Collections.emptyList());
        assertTrue(csv, csv.startsWith(
                "class,package,kind,member,visibility,name,type,params,modifiers"));
        // データ行なし
        assertTrue(csv, csv.trim().lines().count() == 1);
    }
}
