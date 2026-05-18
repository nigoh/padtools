package padtools.core.formats.uml;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * JavaStructureExtractor のユニットテスト。
 */
public class JavaStructureExtractorTest {

    @Test(expected = IllegalArgumentException.class)
    public void testNullInput() {
        JavaStructureExtractor.extract(null);
    }

    @Test
    public void testEmptyInput() {
        assertTrue(JavaStructureExtractor.extract("").isEmpty());
    }

    @Test
    public void testSimpleClass() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "package com.x; class Foo { int a; void m() {} }");
        assertEquals(1, cs.size());
        JavaClassInfo c = cs.get(0);
        assertEquals("com.x", c.getPackageName());
        assertEquals("Foo", c.getSimpleName());
        assertEquals(JavaClassInfo.Kind.CLASS, c.getKind());
        assertEquals(1, c.getFields().size());
        assertEquals("a", c.getFields().get(0).getName());
        assertEquals("int", c.getFields().get(0).getType());
        assertEquals(1, c.getMethods().size());
        assertEquals("m", c.getMethods().get(0).getName());
        assertEquals("void", c.getMethods().get(0).getReturnType());
    }

    @Test
    public void testInterface() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "interface I { void foo(); int bar(); }");
        assertEquals(1, cs.size());
        assertEquals(JavaClassInfo.Kind.INTERFACE, cs.get(0).getKind());
        assertEquals(2, cs.get(0).getMethods().size());
        assertTrue(cs.get(0).getMethods().get(0).isAbstract());
    }

    @Test
    public void testEnum() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "enum E { A, B, C; int x() { return 1; } }");
        assertEquals(JavaClassInfo.Kind.ENUM, cs.get(0).getKind());
        assertEquals(1, cs.get(0).getMethods().size());
        assertEquals("x", cs.get(0).getMethods().get(0).getName());
    }

    @Test
    public void testAnnotationDecl() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "public @interface MyAnno { String value() default \"\"; }");
        assertEquals(1, cs.size());
        assertEquals(JavaClassInfo.Kind.ANNOTATION, cs.get(0).getKind());
        assertEquals("MyAnno", cs.get(0).getSimpleName());
    }

    @Test
    public void testExtendsAndImplements() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "class Foo extends Bar implements I1, I2 { }");
        JavaClassInfo c = cs.get(0);
        assertEquals("Bar", c.getSuperClass());
        assertEquals(2, c.getInterfaces().size());
        assertTrue(c.getInterfaces().contains("I1"));
        assertTrue(c.getInterfaces().contains("I2"));
    }

    @Test
    public void testGenericClass() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "class Foo<T extends Comparable<T>> extends Bar<T> { T item; }");
        JavaClassInfo c = cs.get(0);
        assertEquals("Foo", c.getSimpleName());
        assertEquals("Bar<T>", c.getSuperClass());
        assertEquals("T", c.getFields().get(0).getType());
    }

    @Test
    public void testNestedClass() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "class Outer { void o() {} class Inner { void i() {} } }");
        assertEquals(2, cs.size());
        // 内側が先に処理 (parseClassBody 内で再帰されるため) されるので、Outer/Inner どちらも見つかる
        boolean foundOuter = false;
        boolean foundInner = false;
        for (JavaClassInfo c : cs) {
            if ("Outer".equals(c.getSimpleName())) {
                foundOuter = true;
            }
            if ("Inner".equals(c.getSimpleName())) {
                foundInner = true;
                assertEquals("Outer", c.getEnclosingClass());
                assertEquals("Outer.Inner", c.getQualifiedName());
            }
        }
        assertTrue(foundOuter);
        assertTrue(foundInner);
    }

    @Test
    public void testFieldVisibility() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "class C { public int a; private String b; protected long c; int d; }");
        List<JavaFieldInfo> fs = cs.get(0).getFields();
        assertEquals(Visibility.PUBLIC, fs.get(0).getVisibility());
        assertEquals(Visibility.PRIVATE, fs.get(1).getVisibility());
        assertEquals(Visibility.PROTECTED, fs.get(2).getVisibility());
        assertEquals(Visibility.PACKAGE, fs.get(3).getVisibility());
    }

    @Test
    public void testFieldModifiers() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "class C { public static final int X = 1; }");
        JavaFieldInfo f = cs.get(0).getFields().get(0);
        assertTrue(f.isStatic());
        assertTrue(f.isFinal());
        assertEquals(Visibility.PUBLIC, f.getVisibility());
    }

    @Test
    public void testMethodWithMultipleParams() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "class C { int add(int a, int b, String s) { return a + b; } }");
        JavaMethodInfo m = cs.get(0).getMethods().get(0);
        assertEquals(3, m.getParameterTypes().size());
        assertEquals("int", m.getParameterTypes().get(0));
        assertEquals("a", m.getParameterNames().get(0));
        assertEquals("String", m.getParameterTypes().get(2));
        assertEquals("s", m.getParameterNames().get(2));
    }

    @Test
    public void testGenericParameterTypes() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "class C { void m(Map<String, List<Integer>> map, int x) {} }");
        JavaMethodInfo m = cs.get(0).getMethods().get(0);
        assertEquals(2, m.getParameterTypes().size());
        assertEquals("Map<String, List<Integer>>", m.getParameterTypes().get(0));
        assertEquals("int", m.getParameterTypes().get(1));
    }

    @Test
    public void testConstructor() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "class Foo { public Foo(int x) { this.x = x; } private Foo() {} }");
        List<JavaMethodInfo> ms = cs.get(0).getMethods();
        assertEquals(2, ms.size());
        assertTrue(ms.get(0).isConstructor());
        assertTrue(ms.get(1).isConstructor());
        assertEquals(Visibility.PUBLIC, ms.get(0).getVisibility());
        assertEquals(Visibility.PRIVATE, ms.get(1).getVisibility());
    }

    @Test
    public void testAbstractMethod() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "abstract class C { abstract void m(); void n() {} }");
        List<JavaMethodInfo> ms = cs.get(0).getMethods();
        assertEquals(2, ms.size());
        assertTrue(ms.get(0).isAbstract());
        assertFalse(ms.get(1).isAbstract());
    }

    @Test
    public void testStaticMethod() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "class C { public static int sq(int n) { return n * n; } }");
        assertTrue(cs.get(0).getMethods().get(0).isStatic());
    }

    @Test
    public void testAnnotations() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "@Foo @Bar(value=1) class C {\n"
                        + "  @AddedIn(majorVersion=33) public void m() {}\n"
                        + "}");
        JavaClassInfo c = cs.get(0);
        assertEquals(2, c.getAnnotations().size());
        assertTrue(c.getAnnotations().contains("Foo"));
        assertTrue(c.getMethods().get(0).getAnnotations().stream()
                .anyMatch(a -> a.startsWith("AddedIn")));
    }

    @Test
    public void testCallExtraction() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "class A { void m() { x(); foo.bar(); a.b.c(); } }");
        JavaMethodInfo m = cs.get(0).getMethods().get(0);
        assertEquals(3, m.getCalls().size());
        assertEquals("", m.getCalls().get(0).getReceiver());
        assertEquals("x", m.getCalls().get(0).getMethodName());
        assertEquals("foo", m.getCalls().get(1).getReceiver());
        assertEquals("bar", m.getCalls().get(1).getMethodName());
        assertEquals("a.b", m.getCalls().get(2).getReceiver());
        assertEquals("c", m.getCalls().get(2).getMethodName());
    }

    @Test
    public void testCallExtractionIgnoresControlKeywords() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "class A { void m() { if (x) a(); while (y) b(); } }");
        JavaMethodInfo m = cs.get(0).getMethods().get(0);
        // if/while は除外、a() と b() のみ
        assertEquals(2, m.getCalls().size());
    }

    @Test
    public void testCallExtractionIgnoresNew() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "class A { void m() { Object o = new Object(); foo(); } }");
        JavaMethodInfo m = cs.get(0).getMethods().get(0);
        // new Object() は除外、foo() のみ
        assertEquals(1, m.getCalls().size());
        assertEquals("foo", m.getCalls().get(0).getMethodName());
    }

    @Test
    public void testMultipleClassesInFile() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "class A { void a() {} } class B { void b() {} }");
        assertEquals(2, cs.size());
    }

    @Test
    public void testCommentsAreIgnored() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "// header\n/** javadoc */\nclass A {\n"
                        + "  /* block */ int x; // line\n"
                        + "  void m() {}\n}");
        assertEquals(1, cs.size());
        assertEquals(1, cs.get(0).getFields().size());
        assertEquals(1, cs.get(0).getMethods().size());
    }

    @Test
    public void testJavadocAttachedToClass() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "/** クラスの説明 */\nclass A {}");
        assertEquals("クラスの説明", cs.get(0).getComment());
    }

    @Test
    public void testJavadocAttachedToFieldAndMethod() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "class A {\n"
                        + "  /** 数値フィールド */\n"
                        + "  int x;\n"
                        + "  /** 計算する */\n"
                        + "  int calc() { return 0; }\n"
                        + "}");
        JavaClassInfo c = cs.get(0);
        assertEquals("数値フィールド", c.getFields().get(0).getComment());
        assertEquals("計算する", c.getMethods().get(0).getComment());
    }

    @Test
    public void testLineCommentsMergedAndAttachedToMember() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "class A {\n"
                        + "  // 1 行目\n"
                        + "  // 2 行目\n"
                        + "  int x;\n"
                        + "}");
        // 連続行コメントが改行で結合されて取れる
        assertEquals("1 行目\n2 行目", cs.get(0).getFields().get(0).getComment());
    }

    @Test
    public void testCommentNotAttachedAcrossOtherDeclarations() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "class A {\n"
                        + "  /** doc for foo */\n"
                        + "  int foo;\n"
                        + "  int bar;\n"
                        + "}");
        assertEquals("doc for foo", cs.get(0).getFields().get(0).getComment());
        // bar には付かない
        assertNull(cs.get(0).getFields().get(1).getComment());
    }

    @Test
    public void testJavadocStripsAtTagLines() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "class A {\n"
                        + "  /**\n"
                        + "   * 引数を 2 倍にする。\n"
                        + "   * @param x 入力値\n"
                        + "   * @return 2 倍\n"
                        + "   */\n"
                        + "  int twice(int x) { return x * 2; }\n"
                        + "}");
        String c = cs.get(0).getMethods().get(0).getComment();
        assertNotNull(c);
        assertTrue(c, c.contains("引数を 2 倍にする。"));
        assertFalse(c, c.contains("@param"));
        assertFalse(c, c.contains("@return"));
    }

    @Test
    public void testEnumConstantsCaptured() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "enum Color { RED, GREEN, BLUE }");
        assertEquals(3, cs.get(0).getEnumConstants().size());
        assertEquals("RED", cs.get(0).getEnumConstants().get(0));
        assertEquals("GREEN", cs.get(0).getEnumConstants().get(1));
        assertEquals("BLUE", cs.get(0).getEnumConstants().get(2));
    }

    @Test
    public void testEnumConstantsWithArgsAndBody() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "enum Op {\n"
                        + "  ADD(\"+\"),\n"
                        + "  SUB(\"-\") { int apply(int a, int b) { return a - b; } };\n"
                        + "  Op(String s) {}\n"
                        + "}");
        // 定数 2 つが取れる (引数や無名サブクラス body は読み飛ばす)
        assertEquals(2, cs.get(0).getEnumConstants().size());
        assertEquals("ADD", cs.get(0).getEnumConstants().get(0));
        assertEquals("SUB", cs.get(0).getEnumConstants().get(1));
        // 通常メンバー (コンストラクタ) も拾える
        assertFalse(cs.get(0).getMethods().isEmpty());
    }

    @Test
    public void testStringWithBraces() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "class A { String s = \"{foo}\"; void m() {} }");
        assertEquals(1, cs.get(0).getFields().size());
        assertEquals(1, cs.get(0).getMethods().size());
    }

    @Test
    public void testQualifiedName() {
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(
                "package p.q; class C {}");
        assertEquals("p.q.C", cs.get(0).getQualifiedName());
    }
}
