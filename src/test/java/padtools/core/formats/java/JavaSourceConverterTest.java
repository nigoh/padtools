package padtools.core.formats.java;

import org.junit.Test;
import padtools.core.formats.spd.ParseErrorException;
import padtools.core.formats.spd.ParseErrorReceiver;
import padtools.core.formats.spd.SPDParser;
import padtools.core.models.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.*;

/**
 * JavaSourceConverter のユニットテスト。
 */
public class JavaSourceConverterTest {

    /** SPD として正当な出力であることを SPDParser を通じて検証する補助。 */
    private PADModel parseSpd(String spd) {
        final List<String> errors = new ArrayList<>();
        PADModel m = SPDParser.parse(spd, new ParseErrorReceiver() {
            @Override
            public boolean receiveParseError(String lineStr, int lineNo, ParseErrorException err) {
                errors.add("line " + (lineNo + 1) + ": " + err.getUserMessage()
                        + " (text=" + lineStr + ")");
                return true;
            }
        });
        if (!errors.isEmpty()) {
            fail("Generated SPD has parse errors:\n  "
                    + String.join("\n  ", errors) + "\nSPD:\n" + spd);
        }
        return m;
    }

    // --- 入力バリデーション ---

    @Test(expected = IllegalArgumentException.class)
    public void testNullInput() {
        JavaSourceConverter.convert(null);
    }

    @Test
    public void testEmptyInput() {
        String spd = JavaSourceConverter.convert("");
        assertEquals("", spd);
    }

    @Test
    public void testWhitespaceOnly() {
        String spd = JavaSourceConverter.convert("   \n\t \n");
        assertEquals("", spd);
    }

    @Test
    public void testCommentsOnly() {
        String spd = JavaSourceConverter.convert(
                "// line comment\n/* block */\n/** javadoc */");
        assertEquals("", spd);
    }

    // --- 単純なメソッド ---

    @Test
    public void testSingleEmptyMethod() {
        String java = "class A { void m() {} }";
        String spd = JavaSourceConverter.convert(java);
        assertTrue("expected :terminal m()", spd.contains(":terminal A.m()"));
        assertTrue("expected END", spd.contains(":terminal END"));
        parseSpd(spd);
    }

    @Test
    public void testSimpleReturn() {
        String java = "class A { int m() { return 1; } }";
        String spd = JavaSourceConverter.convert(java);
        assertTrue(spd.contains(":terminal A.m()"));
        assertTrue(spd.contains(":terminal return 1"));
        parseSpd(spd);
    }

    @Test
    public void testVoidReturn() {
        String java = "class A { void m() { return; } }";
        String spd = JavaSourceConverter.convert(java);
        assertTrue(spd.contains(":terminal return"));
    }

    @Test
    public void testProcessStatement() {
        String java = "class A { void m() { x = 1; foo(); } }";
        String spd = JavaSourceConverter.convert(java);
        assertTrue(spd.contains("x = 1"));
        assertTrue(spd.contains("foo()"));
        parseSpd(spd);
    }

    @Test
    public void testMethodWithParameters() {
        String java = "class A { int add(int a, int b) { return a + b; } }";
        String spd = JavaSourceConverter.convert(java);
        assertTrue(spd, spd.contains("add(int a, int b)"));
        parseSpd(spd);
    }

    // --- if/else ---

    @Test
    public void testIfOnly() {
        String java = "class A { void m() { if (x > 0) { y = 1; } } }";
        String spd = JavaSourceConverter.convert(java);
        assertTrue(spd, spd.contains(":if x > 0"));
        assertTrue(spd, spd.contains("\ty = 1"));
        parseSpd(spd);
    }

    @Test
    public void testIfElse() {
        String java = "class A { void m() { if (x > 0) y = 1; else y = 2; } }";
        String spd = JavaSourceConverter.convert(java);
        assertTrue(spd, spd.contains(":if x > 0"));
        assertTrue(spd, spd.contains(":else"));
        assertTrue(spd, spd.contains("y = 1"));
        assertTrue(spd, spd.contains("y = 2"));
        parseSpd(spd);
    }

    @Test
    public void testIfElseIfElse() {
        String java = "class A { void m() { "
                + "if (x > 0) y = 1; else if (x < 0) y = -1; else y = 0; } }";
        String spd = JavaSourceConverter.convert(java);
        // else if は :else に :if をネスト
        parseSpd(spd);
        // 3 つの分岐が現れること
        assertTrue(spd, spd.contains("y = 1"));
        assertTrue(spd, spd.contains("y = -1"));
        assertTrue(spd, spd.contains("y = 0"));
        assertTrue(spd, spd.contains(":else"));
    }

    @Test
    public void testIfWithBlock() {
        String java = "class A { void m() {"
                + " if (x > 0) { a(); b(); c(); } else { d(); } } }";
        String spd = JavaSourceConverter.convert(java);
        parseSpd(spd);
        assertTrue(spd, spd.contains("a()"));
        assertTrue(spd, spd.contains("b()"));
        assertTrue(spd, spd.contains("c()"));
        assertTrue(spd, spd.contains("d()"));
    }

    // --- while / do-while ---

    @Test
    public void testWhileLoop() {
        String java = "class A { void m() { while (i < 10) { i++; } } }";
        String spd = JavaSourceConverter.convert(java);
        assertTrue(spd, spd.contains(":while i < 10"));
        assertTrue(spd, spd.contains("i++"));
        parseSpd(spd);
    }

    @Test
    public void testDoWhileLoop() {
        String java = "class A { void m() { do { i++; } while (i < 10); } }";
        String spd = JavaSourceConverter.convert(java);
        assertTrue("expected :dowhile i < 10", spd.contains(":dowhile i < 10"));
        assertTrue(spd, spd.contains("i++"));
        // :dowhile の方が i++ より前に出力されているはず
        int dwIdx = spd.indexOf(":dowhile");
        int incIdx = spd.indexOf("i++");
        assertTrue("dowhile should come before its body", dwIdx >= 0 && incIdx > dwIdx);
        parseSpd(spd);
    }

    // --- for ---

    @Test
    public void testClassicForLoop() {
        String java = "class A { void m() { for (int i = 0; i < n; i++) { foo(i); } } }";
        String spd = JavaSourceConverter.convert(java);
        // init は process として独立、while で cond、update は本体末尾
        assertTrue(spd, spd.contains("int i = 0"));
        assertTrue(spd, spd.contains(":while i < n"));
        assertTrue(spd, spd.contains("foo(i)"));
        assertTrue(spd, spd.contains("i++"));
        // update が body の後に来ていること
        int bodyIdx = spd.indexOf("foo(i)");
        int updIdx = spd.indexOf("i++");
        assertTrue(bodyIdx >= 0 && updIdx > bodyIdx);
        parseSpd(spd);
    }

    @Test
    public void testEnhancedForLoop() {
        String java = "class A { void m() { for (String s : list) { print(s); } } }";
        String spd = JavaSourceConverter.convert(java);
        assertTrue(spd, spd.contains(":while list に要素がある間"));
        assertTrue(spd, spd.contains("String s = next"));
        assertTrue(spd, spd.contains("print(s)"));
        parseSpd(spd);
    }

    @Test
    public void testForWithEmptyParts() {
        String java = "class A { void m() { for (;;) { break; } } }";
        String spd = JavaSourceConverter.convert(java);
        assertTrue(spd, spd.contains(":while true"));
        assertTrue(spd, spd.contains("break"));
        parseSpd(spd);
    }

    @Test
    public void testForUnrollDisabled() {
        JavaSourceConverter.Options o = new JavaSourceConverter.Options();
        o.unrollFor = false;
        String java = "class A { void m() { for (int i = 0; i < n; i++) foo(); } }";
        String spd = JavaSourceConverter.convert(java, o);
        assertTrue(spd, spd.contains(":while for(int i = 0; i < n; i++)"));
        parseSpd(spd);
    }

    // --- switch ---

    @Test
    public void testSimpleSwitch() {
        String java = "class A { void m(int x) { "
                + "switch (x) { case 1: a(); break; case 2: b(); break; default: c(); } } }";
        String spd = JavaSourceConverter.convert(java);
        assertTrue(spd, spd.contains(":switch x"));
        assertTrue(spd, spd.contains(":case 1"));
        assertTrue(spd, spd.contains(":case 2"));
        assertTrue(spd, spd.contains(":case default"));
        parseSpd(spd);
    }

    @Test
    public void testSwitchFallThroughLabels() {
        String java = "class A { void m(int x) { "
                + "switch (x) { case 1: case 2: a(); break; default: c(); } } }";
        String spd = JavaSourceConverter.convert(java);
        // 連続する case ラベルは / で結合される
        assertTrue(spd, spd.contains(":case 1 / 2"));
        parseSpd(spd);
    }

    // --- try-catch / finally ---

    @Test
    public void testTryCatch() {
        String java = "class A { void m() { try { a(); } catch (Exception e) { b(); } } }";
        String spd = JavaSourceConverter.convert(java);
        assertTrue(spd, spd.contains(":switch try-catch"));
        assertTrue(spd, spd.contains(":case 正常"));
        assertTrue(spd, spd.contains(":case catch Exception e"));
        parseSpd(spd);
    }

    @Test
    public void testTryCatchFinally() {
        String java = "class A { void m() { try { a(); } catch (E1 e) { b(); }"
                + " catch (E2 e) { c(); } finally { d(); } } }";
        String spd = JavaSourceConverter.convert(java);
        assertTrue(spd, spd.contains(":case catch E1 e"));
        assertTrue(spd, spd.contains(":case catch E2 e"));
        assertTrue(spd, spd.contains(":case finally"));
        parseSpd(spd);
    }

    @Test
    public void testTryWithResources() {
        String java = "class A { void m() {"
                + " try (Reader r = open()) { use(r); } catch (IOException e) { fail(); } } }";
        String spd = JavaSourceConverter.convert(java);
        assertTrue(spd, spd.contains("try-with-resources:"));
        assertTrue(spd, spd.contains(":switch try-catch"));
        parseSpd(spd);
    }

    // --- break / continue / throw ---

    @Test
    public void testBreakContinue() {
        String java = "class A { void m() {"
                + " while (true) { if (x) break; if (y) continue; foo(); } } }";
        String spd = JavaSourceConverter.convert(java);
        assertTrue(spd, spd.contains("break"));
        assertTrue(spd, spd.contains("continue"));
        parseSpd(spd);
    }

    @Test
    public void testThrow() {
        String java = "class A { void m() { throw new RuntimeException(\"bad\"); } }";
        String spd = JavaSourceConverter.convert(java);
        assertTrue(spd, spd.contains(":terminal throw new RuntimeException"));
        parseSpd(spd);
    }

    @Test
    public void testLabeledBreak() {
        String java = "class A { void m() {"
                + " outer: while (true) { while (x) break outer; } } }";
        String spd = JavaSourceConverter.convert(java);
        assertTrue(spd, spd.contains("break outer"));
        parseSpd(spd);
    }

    @Test
    public void testSynchronizedBlock() {
        String java = "class A { void m() { synchronized (lock) { critical(); } } }";
        String spd = JavaSourceConverter.convert(java);
        assertTrue(spd, spd.contains("synchronized(lock)"));
        assertTrue(spd, spd.contains("critical()"));
        parseSpd(spd);
    }

    // --- ネスト ---

    @Test
    public void testNestedIfInWhile() {
        String java = "class A { void m() {"
                + " while (i < 10) { if (i % 2 == 0) even(); else odd(); i++; } } }";
        String spd = JavaSourceConverter.convert(java);
        parseSpd(spd);
        // ネストの妥当性は SPD パーサが保証する
        PADModel m = parseSpd(spd);
        assertNotNull(m);
        assertNotNull(m.getTopNode());
    }

    @Test
    public void testDeepNesting() {
        String java = "class A { void m() {"
                + " while (a) { while (b) { if (c) { while (d) { foo(); } } } } } }";
        String spd = JavaSourceConverter.convert(java);
        parseSpd(spd);
    }

    // --- 複数メソッド / クラス ---

    @Test
    public void testMultipleMethods() {
        String java = "class A { void m1() { a(); } void m2() { b(); } }";
        String spd = JavaSourceConverter.convert(java);
        assertTrue(spd, spd.contains(":terminal A.m1()"));
        assertTrue(spd, spd.contains(":terminal A.m2()"));
        parseSpd(spd);
    }

    @Test
    public void testNestedClass() {
        String java = "class Outer { void o() { o(); }"
                + " class Inner { void i() { i(); } } }";
        String spd = JavaSourceConverter.convert(java);
        assertTrue(spd, spd.contains(":terminal Outer.o()"));
        assertTrue(spd, spd.contains(":terminal Outer.Inner.i()"));
        parseSpd(spd);
    }

    @Test
    public void testFieldsAreIgnored() {
        String java = "class A { int x = 1; String s = \"x;y;z\"; void m() { f(); } }";
        String spd = JavaSourceConverter.convert(java);
        // フィールドは出力されない
        assertFalse(spd, spd.contains("int x = 1"));
        assertFalse(spd, spd.contains("String s"));
        assertTrue(spd, spd.contains("f()"));
        parseSpd(spd);
    }

    @Test
    public void testAbstractMethodIgnored() {
        String java = "abstract class A { abstract void m1();"
                + " void m2() { x(); } }";
        String spd = JavaSourceConverter.convert(java);
        assertFalse(spd, spd.contains("A.m1"));
        assertTrue(spd, spd.contains("A.m2"));
        parseSpd(spd);
    }

    @Test
    public void testInterface() {
        String java = "interface I { void m1();"
                + " default void m2() { call(); } }";
        String spd = JavaSourceConverter.convert(java);
        // m1 は本体なし → スキップ。m2 はデフォルトメソッドなので変換される
        assertFalse(spd, spd.contains("I.m1"));
        assertTrue(spd, spd.contains("I.m2"));
        parseSpd(spd);
    }

    @Test
    public void testEnum() {
        String java = "enum E { A, B; int x() { return 1; } }";
        String spd = JavaSourceConverter.convert(java);
        // enum も class と同様にメソッドが抽出される
        assertTrue(spd, spd.contains("E.x()"));
        parseSpd(spd);
    }

    @Test
    public void testGenericMethod() {
        String java = "class A { <T> T m(T x) { return x; } }";
        String spd = JavaSourceConverter.convert(java);
        assertTrue(spd, spd.contains(":terminal A.m(T x)"));
        assertTrue(spd, spd.contains(":terminal return x"));
        parseSpd(spd);
    }

    @Test
    public void testGenericNestedTypes() {
        String java = "class A { Map<String, List<Integer>> map; void m() { foo(); } }";
        String spd = JavaSourceConverter.convert(java);
        assertTrue(spd, spd.contains("foo()"));
        parseSpd(spd);
    }

    @Test
    public void testGenericWithRightShift() {
        // `>>` がジェネリクスのネスト終端としても、ビットシフト演算子としても出現
        String java = "class A { List<List<Integer>> field; "
                + "void m(int x) { int y = x >> 2; foo(); } }";
        String spd = JavaSourceConverter.convert(java);
        assertTrue(spd, spd.contains("int y = x >> 2"));
        assertTrue(spd, spd.contains("foo()"));
        parseSpd(spd);
    }

    @Test
    public void testAnnotation() {
        String java = "class A { @Override public String toString() { return \"a\"; } }";
        String spd = JavaSourceConverter.convert(java);
        assertTrue(spd, spd.contains(":terminal A.toString()"));
        parseSpd(spd);
    }

    @Test
    public void testAnnotationWithArgs() {
        String java = "class A { "
                + "@SuppressWarnings({\"a\", \"b\"}) "
                + "@Target(ElementType.METHOD) "
                + "void m() { foo(); } }";
        String spd = JavaSourceConverter.convert(java);
        assertTrue(spd, spd.contains(":terminal A.m()"));
        assertTrue(spd, spd.contains("foo()"));
        parseSpd(spd);
    }

    @Test
    public void testAnnotationTypeDecl() {
        String java = "public @interface MyAnno { String value() default \"\"; }";
        // @interface 自体は変換されない、内部のメソッドは抽出される
        String spd = JavaSourceConverter.convert(java);
        parseSpd(spd);
    }

    // --- 文字列とエスケープ ---

    @Test
    public void testStringWithBraces() {
        String java = "class A { void m() { String s = \"{nested}\"; foo(s); } }";
        String spd = JavaSourceConverter.convert(java);
        assertTrue(spd, spd.contains("foo(s)"));
        parseSpd(spd);
    }

    @Test
    public void testStringWithControlChars() {
        String java = "class A { void m() { String s = \"if (x) {\"; bar(s); } }";
        String spd = JavaSourceConverter.convert(java);
        assertTrue(spd, spd.contains("bar(s)"));
        parseSpd(spd);
    }

    @Test
    public void testCharLiteral() {
        String java = "class A { void m() { char c = '{'; foo(c); } }";
        String spd = JavaSourceConverter.convert(java);
        assertTrue(spd, spd.contains("foo(c)"));
        parseSpd(spd);
    }

    // --- イニシャライザブロック ---

    @Test
    public void testStaticInitializer() {
        String java = "class A { static { init(); } void m() { go(); } }";
        String spd = JavaSourceConverter.convert(java);
        assertTrue(spd, spd.contains("init()"));
        assertTrue(spd, spd.contains("go()"));
        parseSpd(spd);
    }

    @Test
    public void testInstanceInitializer() {
        String java = "class A { { x = 1; } void m() {} }";
        String spd = JavaSourceConverter.convert(java);
        assertTrue(spd, spd.contains("x = 1"));
        parseSpd(spd);
    }

    // --- コンストラクタ ---

    @Test
    public void testConstructor() {
        String java = "class A { A(int x) { this.x = x; } }";
        String spd = JavaSourceConverter.convert(java);
        assertTrue(spd, spd.contains(":terminal A.A(int x)"));
        assertTrue(spd, spd.contains("this.x = x"));
        parseSpd(spd);
    }

    // --- オプション ---

    @Test
    public void testOptionIncludeTerminalsFalse() {
        JavaSourceConverter.Options o = new JavaSourceConverter.Options();
        o.includeTerminals = false;
        String java = "class A { void m() { x = 1; } }";
        String spd = JavaSourceConverter.convert(java, o);
        assertFalse(spd, spd.contains(":terminal"));
        assertTrue(spd, spd.contains(":comment A.m()"));
        assertTrue(spd, spd.contains("x = 1"));
        parseSpd(spd);
    }

    @Test
    public void testOptionReturnAsProcess() {
        JavaSourceConverter.Options o = new JavaSourceConverter.Options();
        o.returnAsTerminal = false;
        String java = "class A { int m() { return 1; } }";
        String spd = JavaSourceConverter.convert(java, o);
        // メソッドの開始/終了の :terminal は出るが return は process node
        assertTrue(spd, spd.contains(":terminal A.m()"));
        // body 中の return が terminal でなく process として出力される
        // 行を抽出して確認
        boolean foundProcess = false;
        for (String line : spd.split("\n")) {
            if (line.trim().equals("return 1")) {
                foundProcess = true;
                break;
            }
        }
        assertTrue("expected return 1 as process line", foundProcess);
        parseSpd(spd);
    }

    @Test
    public void testOptionClassSeparator() {
        JavaSourceConverter.Options o = new JavaSourceConverter.Options();
        o.classSeparator = "::";
        String java = "class A { void m() {} }";
        String spd = JavaSourceConverter.convert(java, o);
        assertTrue(spd, spd.contains("A::m()"));
        parseSpd(spd);
    }

    @Test
    public void testOptionMethodFilter() {
        JavaSourceConverter.Options o = new JavaSourceConverter.Options();
        o.methodFilter = new HashSet<>(Arrays.asList("keepMe"));
        String java = "class A { void keepMe() { a(); } void skipMe() { b(); } }";
        String spd = JavaSourceConverter.convert(java, o);
        assertTrue(spd, spd.contains("keepMe"));
        assertFalse(spd, spd.contains("skipMe"));
        assertTrue(spd, spd.contains("a()"));
        assertFalse(spd, spd.contains("b()"));
        parseSpd(spd);
    }

    // --- 包括的なシナリオ ---

    @Test
    public void testCompleteAndroidLikeMethod() {
        String java =
                "package com.example;\n"
                        + "import android.app.Activity;\n"
                        + "public class MyActivity extends Activity {\n"
                        + "    private static final int LIMIT = 10;\n"
                        + "    private int counter = 0;\n"
                        + "    @Override\n"
                        + "    public void onCreate() {\n"
                        + "        super.onCreate();\n"
                        + "        for (int i = 0; i < LIMIT; i++) {\n"
                        + "            if (i % 2 == 0) {\n"
                        + "                handleEven(i);\n"
                        + "            } else {\n"
                        + "                handleOdd(i);\n"
                        + "            }\n"
                        + "        }\n"
                        + "        try {\n"
                        + "            doSomething();\n"
                        + "        } catch (Exception e) {\n"
                        + "            log(e);\n"
                        + "        } finally {\n"
                        + "            cleanup();\n"
                        + "        }\n"
                        + "        return;\n"
                        + "    }\n"
                        + "    private void handleEven(int n) { counter += n; }\n"
                        + "    private void handleOdd(int n) { counter -= n; }\n"
                        + "}";
        String spd = JavaSourceConverter.convert(java);
        PADModel m = parseSpd(spd);
        assertNotNull(m);
        assertNotNull(m.getTopNode());
        assertTrue(spd, spd.contains("MyActivity.onCreate()"));
        assertTrue(spd, spd.contains("MyActivity.handleEven(int n)"));
        assertTrue(spd, spd.contains("MyActivity.handleOdd(int n)"));
        assertTrue(spd, spd.contains(":switch try-catch"));
    }

    @Test
    public void testStructureProducesIfNode() {
        String java = "class A { void m() { if (x) a(); else b(); } }";
        String spd = JavaSourceConverter.convert(java);
        PADModel m = parseSpd(spd);
        assertNotNull(m);
        // PAD 内に IfNode が出現することを確認
        assertTrue("expected IfNode in model", containsType(m.getTopNode(), IfNode.class));
    }

    @Test
    public void testStructureProducesLoopNode() {
        String java = "class A { void m() { while (x) a(); } }";
        String spd = JavaSourceConverter.convert(java);
        PADModel m = parseSpd(spd);
        assertTrue(containsType(m.getTopNode(), LoopNode.class));
    }

    @Test
    public void testStructureProducesSwitchNode() {
        String java = "class A { void m() { switch (x) { case 1: a(); break; default: b(); } } }";
        String spd = JavaSourceConverter.convert(java);
        PADModel m = parseSpd(spd);
        assertTrue(containsType(m.getTopNode(), SwitchNode.class));
    }

    @Test
    public void testStructureProducesTerminalNode() {
        String java = "class A { void m() {} }";
        String spd = JavaSourceConverter.convert(java);
        PADModel m = parseSpd(spd);
        assertTrue(containsType(m.getTopNode(), TerminalNode.class));
    }

    // --- エッジケース ---

    @Test
    public void testLambdaInMethodBody() {
        String java = "class A { void m() {"
                + " run(() -> doThis()); list.forEach(x -> handle(x)); } }";
        String spd = JavaSourceConverter.convert(java);
        parseSpd(spd);
        assertTrue(spd, spd.contains("run("));
        assertTrue(spd, spd.contains("list.forEach("));
    }

    @Test
    public void testMethodReference() {
        String java = "class A { void m() { list.forEach(System.out::println); } }";
        String spd = JavaSourceConverter.convert(java);
        parseSpd(spd);
        assertTrue(spd, spd.contains("System.out::println"));
    }

    @Test
    public void testTryWithMultipleResources() {
        String java = "class A { void m() {"
                + " try (R a = open(); R b = open()) { use(a, b); }"
                + " catch (IOException e) { fail(); } } }";
        String spd = JavaSourceConverter.convert(java);
        parseSpd(spd);
        assertTrue(spd, spd.contains("try-with-resources:"));
    }

    @Test
    public void testVarArgs() {
        String java = "class A { void m(String... args) { for (String a : args) p(a); } }";
        String spd = JavaSourceConverter.convert(java);
        parseSpd(spd);
        assertTrue(spd, spd.contains("A.m(String... args)"));
    }

    @Test
    public void testMultipleClassesInFile() {
        String java = "class A { void a() { x(); } }\n"
                + "class B { void b() { y(); } }";
        String spd = JavaSourceConverter.convert(java);
        parseSpd(spd);
        assertTrue(spd, spd.contains("A.a()"));
        assertTrue(spd, spd.contains("B.b()"));
    }

    @Test
    public void testStaticMethodSkipped() {
        // static メソッドも処理対象
        String java = "class A { public static int main() { return 0; } }";
        String spd = JavaSourceConverter.convert(java);
        parseSpd(spd);
        assertTrue(spd, spd.contains("A.main()"));
        assertTrue(spd, spd.contains("return 0"));
    }

    @Test
    public void testEmptyMethodBody() {
        String java = "class A { void noop() {} }";
        String spd = JavaSourceConverter.convert(java);
        parseSpd(spd);
        assertTrue(spd, spd.contains(":terminal A.noop()"));
        assertTrue(spd, spd.contains(":terminal END"));
    }

    @Test
    public void testAnonymousInnerClass() {
        // メソッド本体内の new Foo() { ... } 構文は文として扱われる
        String java = "class A { void m() {"
                + " setListener(new Runnable() { public void run() { go(); } }); } }";
        String spd = JavaSourceConverter.convert(java);
        parseSpd(spd);
        // 文として 1 ノード化される (内部メソッドは抽出されない仕様)
        assertTrue(spd, spd.contains("setListener"));
    }

    @Test
    public void testTernaryExpression() {
        String java = "class A { int m(int x) { return x > 0 ? x : -x; } }";
        String spd = JavaSourceConverter.convert(java);
        parseSpd(spd);
        assertTrue(spd, spd.contains("return x > 0 ? x : -x"));
    }

    @Test
    public void testArrayDeclaration() {
        String java = "class A { void m() { int[] a = new int[10]; a[0] = 1; } }";
        String spd = JavaSourceConverter.convert(java);
        parseSpd(spd);
        assertTrue(spd, spd.contains("int[] a = new int[10]"));
        assertTrue(spd, spd.contains("a[0] = 1"));
    }

    @Test
    public void testGenericInstantiation() {
        String java = "class A { void m() {"
                + " Map<String, List<Integer>> map = new HashMap<>(); use(map); } }";
        String spd = JavaSourceConverter.convert(java);
        parseSpd(spd);
        assertTrue(spd, spd.contains("Map<String, List<Integer>> map"));
    }

    @Test
    public void testFinalParameter() {
        String java = "class A { void m(final int x, final String s) { use(x, s); } }";
        String spd = JavaSourceConverter.convert(java);
        parseSpd(spd);
        assertTrue(spd, spd.contains("A.m(final int x, final String s)"));
    }

    @Test
    public void testReturnInIfBranch() {
        String java = "class A { int m(int x) {"
                + " if (x < 0) { return -1; } return x; } }";
        String spd = JavaSourceConverter.convert(java);
        parseSpd(spd);
        // if 内の return と関数末尾の return が両方出力される
        long returnCount = spd.lines()
                .filter(l -> l.contains(":terminal return"))
                .count();
        assertTrue("expected 2 return terminals, got: " + returnCount, returnCount >= 2);
    }

    @Test
    public void testNestedSwitch() {
        String java = "class A { void m(int x, int y) {"
                + " switch (x) { case 1:"
                + "   switch (y) { case 1: a(); break; default: b(); break; }"
                + "   break;"
                + " default: c(); break; } } }";
        String spd = JavaSourceConverter.convert(java);
        parseSpd(spd);
        // switch が 2 つ出てくる
        long switchCount = spd.lines().filter(l -> l.contains(":switch")).count();
        assertTrue("expected 2 switches, got: " + switchCount, switchCount >= 2);
    }

    /** PAD ノード木に指定型が含まれるかを再帰検索する。 */
    private boolean containsType(NodeBase root, Class<?> target) {
        if (root == null) {
            return false;
        }
        if (target.isInstance(root)) {
            return true;
        }
        if (root instanceof NodeListNode) {
            for (NodeBase n : ((NodeListNode) root).getChildren()) {
                if (containsType(n, target)) {
                    return true;
                }
            }
        }
        if (root instanceof WithChildNode) {
            if (containsType(((WithChildNode) root).getChildNode(), target)) {
                return true;
            }
        }
        if (root instanceof IfNode) {
            if (containsType(((IfNode) root).getTrueNode(), target)) {
                return true;
            }
            if (containsType(((IfNode) root).getFalseNode(), target)) {
                return true;
            }
        }
        if (root instanceof SwitchNode) {
            for (NodeBase n : ((SwitchNode) root).getCases().values()) {
                if (containsType(n, target)) {
                    return true;
                }
            }
        }
        return false;
    }
}
