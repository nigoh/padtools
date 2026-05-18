package padtools.core.formats.uml;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * PlantUmlSequenceDiagram のユニットテスト。
 */
public class PlantUmlSequenceDiagramTest {

    @Test(expected = IllegalArgumentException.class)
    public void testNullClasses() {
        PlantUmlSequenceDiagram.generate(null, "X", "m", null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullEntry() {
        PlantUmlSequenceDiagram.generate(
                JavaStructureExtractor.extract("class A {}"), null, null, null);
    }

    @Test
    public void testNonexistentClass() {
        String puml = PlantUmlSequenceDiagram.generate(
                JavaStructureExtractor.extract("class A {}"),
                "Unknown", "m", null);
        assertTrue(puml, puml.contains("Class not found"));
    }

    @Test
    public void testNonexistentMethod() {
        String puml = PlantUmlSequenceDiagram.generate(
                JavaStructureExtractor.extract("class A {}"),
                "A", "nope", null);
        assertTrue(puml, puml.contains("Method not found"));
    }

    @Test
    public void testBasicSequence() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { void run() { foo.bar(); } }");
        String puml = PlantUmlSequenceDiagram.generate(infos, "A", "run", null);
        assertTrue(puml, puml.contains("@startuml"));
        assertTrue(puml, puml.contains("@enduml"));
        assertTrue(puml, puml.contains("participant \"Caller\""));
        assertTrue(puml, puml.contains("participant \"A\""));
        assertTrue(puml, puml.contains("Caller -> A: run()"));
        assertTrue(puml, puml.contains("A -> foo: bar()"));
        assertTrue(puml, puml.contains("activate A"));
        assertTrue(puml, puml.contains("deactivate A"));
    }

    @Test
    public void testSelfCall() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { void run() { helper(); } void helper() {} }");
        String puml = PlantUmlSequenceDiagram.generate(infos, "A", "run", null);
        assertTrue(puml, puml.contains("A -> A: helper()"));
    }

    @Test
    public void testReceiverResolvedFromFieldType() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { IAudio mAudio; void run() { mAudio.setVolume(5); } }");
        String puml = PlantUmlSequenceDiagram.generate(infos, "A", "run", null);
        assertTrue(puml, puml.contains("A -> IAudio: setVolume()"));
        assertTrue(puml, puml.contains("participant \"IAudio\""));
    }

    @Test
    public void testReceiverGenericFieldType() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { java.util.List<String> items; void run() { items.add(\"x\"); } }");
        String puml = PlantUmlSequenceDiagram.generate(infos, "A", "run", null);
        // List 型へのフィールド参照は List で participant 化
        assertTrue(puml, puml.contains("A -> List: add()"));
    }

    @Test
    public void testQualifiedClassName() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "package p; class A { void run() { foo(); } }");
        String puml = PlantUmlSequenceDiagram.generate(infos, "p.A", "run", null);
        assertTrue(puml, puml.contains("Caller -> A: run()"));
    }

    @Test
    public void testCustomTitle() {
        PlantUmlSequenceDiagram.Options o = new PlantUmlSequenceDiagram.Options();
        o.title = "Login Flow";
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { void run() {} }");
        String puml = PlantUmlSequenceDiagram.generate(infos, "A", "run", o);
        assertTrue(puml, puml.contains("title Login Flow"));
    }

    @Test
    public void testCustomCallerName() {
        PlantUmlSequenceDiagram.Options o = new PlantUmlSequenceDiagram.Options();
        o.callerName = "User";
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { void run() {} }");
        String puml = PlantUmlSequenceDiagram.generate(infos, "A", "run", o);
        assertTrue(puml, puml.contains("User -> A: run()"));
    }

    @Test
    public void testEmptyBodyNoCalls() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { void m() {} }");
        String puml = PlantUmlSequenceDiagram.generate(infos, "A", "m", null);
        // 呼び出しが無くてもエラーにならず、開始/終了のみ出力
        assertTrue(puml, puml.contains("@startuml"));
        assertTrue(puml, puml.contains("Caller -> A: m()"));
        assertTrue(puml, puml.contains("@enduml"));
    }

    @Test
    public void testMultipleCalls() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { Service s; void run() { s.a(); s.b(); s.c(); } }");
        String puml = PlantUmlSequenceDiagram.generate(infos, "A", "run", null);
        assertTrue(puml, puml.contains("a()"));
        assertTrue(puml, puml.contains("b()"));
        assertTrue(puml, puml.contains("c()"));
    }

    @Test
    public void testThisExplicit() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { void run() { this.helper(); } void helper() {} }");
        String puml = PlantUmlSequenceDiagram.generate(infos, "A", "run", null);
        assertTrue(puml, puml.contains("A -> A: helper()"));
    }

    @Test
    public void testLegendIncludedByDefault() {
        String puml = PlantUmlSequenceDiagram.generate(
                JavaStructureExtractor.extract("class A { void m() {} }"),
                "A", "m", null);
        assertTrue(puml, puml.contains("legend right"));
        assertTrue(puml, puml.contains("endlegend"));
    }

    @Test
    public void testLegendDisabled() {
        PlantUmlSequenceDiagram.Options o = new PlantUmlSequenceDiagram.Options();
        o.includeLegend = false;
        String puml = PlantUmlSequenceDiagram.generate(
                JavaStructureExtractor.extract("class A { void m() {} }"),
                "A", "m", o);
        assertFalse(puml, puml.contains("legend right"));
    }

    // ------------ 制御構造 ------------

    @Test
    public void testOptForSingleIf() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { Service s; void run() { if (x > 0) { s.go(); } } }");
        String puml = PlantUmlSequenceDiagram.generate(infos, "A", "run", null);
        assertTrue(puml, puml.contains("opt x > 0"));
        assertTrue(puml, puml.contains("A -> Service: go()"));
        assertTrue(puml, puml.contains("end"));
    }

    @Test
    public void testAltForIfElse() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { void run() { if (x) a(); else b(); } void a() {} void b() {} }");
        String puml = PlantUmlSequenceDiagram.generate(infos, "A", "run", null);
        assertTrue(puml, puml.contains("alt x"));
        assertTrue(puml, puml.contains("A -> A: a()"));
        // else 節
        assertTrue(puml, puml.matches("(?s).*alt x.*else.*A -> A: b\\(\\).*end.*"));
    }

    @Test
    public void testAltElseIfElse() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { void run() {"
                        + "  if (a) { x(); }"
                        + "  else if (b) { y(); }"
                        + "  else { z(); }"
                        + "} void x(){} void y(){} void z(){} }");
        String puml = PlantUmlSequenceDiagram.generate(infos, "A", "run", null);
        assertTrue(puml, puml.contains("alt a"));
        assertTrue(puml, puml.contains("else b"));
        assertTrue(puml, puml.contains("end"));
    }

    @Test
    public void testLoopForWhile() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { void run() { while (hasNext()) { next(); } } "
                        + "boolean hasNext(){return false;} void next() {} }");
        String puml = PlantUmlSequenceDiagram.generate(infos, "A", "run", null);
        assertTrue(puml, puml.contains("loop while"));
        // 条件式内の呼び出し (hasNext()) は loop の前に出る
        assertTrue(puml, puml.matches("(?s).*A -> A: hasNext\\(\\).*loop while.*"));
    }

    @Test
    public void testLoopForFor() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { void run() { for (int i = 0; i < 10; i++) { step(); } } void step() {} }");
        String puml = PlantUmlSequenceDiagram.generate(infos, "A", "run", null);
        assertTrue(puml, puml.contains("loop for"));
        assertTrue(puml, puml.contains("A -> A: step()"));
    }

    @Test
    public void testGroupForTry() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { void run() {"
                        + "  try { open(); } catch (IOException e) { log(); } finally { close(); }"
                        + "} void open(){} void log(){} void close(){} }");
        String puml = PlantUmlSequenceDiagram.generate(infos, "A", "run", null);
        assertTrue(puml, puml.contains("group try"));
        assertTrue(puml, puml.contains("else catch IOException e"));
        assertTrue(puml, puml.contains("else finally"));
        assertTrue(puml, puml.contains("A -> A: open()"));
        assertTrue(puml, puml.contains("A -> A: close()"));
    }

    @Test
    public void testCriticalForSynchronized() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { Object lock; void run() { synchronized (lock) { foo(); } } void foo() {} }");
        String puml = PlantUmlSequenceDiagram.generate(infos, "A", "run", null);
        assertTrue(puml, puml.contains("critical synchronized(lock)"));
        assertTrue(puml, puml.contains("A -> A: foo()"));
    }

    // ------------ 多段トレース ------------

    @Test
    public void testMultiHopRecursion() {
        // run() -> helper() -> inner()。すべて A クラス内。デフォルトの maxDepth=5 で展開される。
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { "
                        + "void run() { helper(); } "
                        + "void helper() { inner(); } "
                        + "void inner() { leaf(); } "
                        + "void leaf() {} }");
        String puml = PlantUmlSequenceDiagram.generate(infos, "A", "run", null);
        assertTrue(puml, puml.contains("A -> A: helper()"));
        assertTrue(puml, puml.contains("A -> A: inner()"));
        assertTrue(puml, puml.contains("A -> A: leaf()"));
    }

    @Test
    public void testMultiHopStopsAtMaxDepth() {
        PlantUmlSequenceDiagram.Options o = new PlantUmlSequenceDiagram.Options();
        o.maxDepth = 1;  // 起点のみ展開、呼び出し先には潜らない
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { void run() { helper(); } void helper() { inner(); } void inner() {} }");
        String puml = PlantUmlSequenceDiagram.generate(infos, "A", "run", o);
        assertTrue(puml, puml.contains("A -> A: helper()"));
        assertFalse(puml, puml.contains("A -> A: inner()"));
    }

    @Test
    public void testMultiHopCrossClass() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { B b; void run() { b.act(); } } "
                        + "class B { void act() { work(); } void work() {} }");
        String puml = PlantUmlSequenceDiagram.generate(infos, "A", "run", null);
        assertTrue(puml, puml.contains("A -> B: act()"));
        assertTrue(puml, puml.contains("B -> B: work()"));
        // 両方の participant が宣言されている
        assertTrue(puml, puml.contains("participant \"A\""));
        assertTrue(puml, puml.contains("participant \"B\""));
    }

    @Test
    public void testCycleDetection() {
        // run() -> run() の自己呼び出しは無限再帰せず、note で打ち切る
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { void run() { run(); } }");
        String puml = PlantUmlSequenceDiagram.generate(infos, "A", "run", null);
        assertTrue(puml, puml.contains("A -> A: run()"));
        assertTrue(puml, puml.contains("recursive call"));
    }

    @Test
    public void testMutualRecursionCycleDetection() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { void a() { b(); } void b() { a(); } }");
        String puml = PlantUmlSequenceDiagram.generate(infos, "A", "a", null);
        assertTrue(puml, puml.contains("A -> A: b()"));
        // a → b → a でサイクル検出
        assertTrue(puml, puml.contains("recursive call"));
    }

    @Test
    public void testNestedControlStructures() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { void run() {"
                        + "  for (int i = 0; i < n; i++) {"
                        + "    if (i > 0) { tick(); }"
                        + "  }"
                        + "} void tick() {} }");
        String puml = PlantUmlSequenceDiagram.generate(infos, "A", "run", null);
        assertTrue(puml, puml.contains("loop for"));
        assertTrue(puml, puml.contains("opt i > 0"));
        assertTrue(puml, puml.contains("A -> A: tick()"));
    }

    // ------------ 候補リスト ------------

    @Test
    public void testListCandidatesSortedByCallCount() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { void zero() {} void two() { a(); b(); } void one() { c(); } }");
        List<PlantUmlSequenceDiagram.Candidate> list =
                PlantUmlSequenceDiagram.listCandidates(infos);
        assertEquals(3, list.size());
        // 呼び出し数で降順 (two=2 > one=1 > zero=0)
        assertEquals("A.two", list.get(0).getEntry());
        assertEquals(2, list.get(0).callCount);
        assertEquals("A.one", list.get(1).getEntry());
        assertEquals("A.zero", list.get(2).getEntry());
    }

    // ------------ プロジェクト内クラスの色付け ------------

    @Test
    public void testProjectClassColoredByDefault() {
        // A, B はプロジェクト内クラス、Service は外部クラス
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { B b; Service s; void run() { b.act(); s.go(); } } "
                        + "class B { void act() {} }");
        String puml = PlantUmlSequenceDiagram.generate(infos, "A", "run", null);
        // 解析済みクラスは色付き
        assertTrue(puml, puml.contains("participant \"A\" #LightSkyBlue"));
        assertTrue(puml, puml.contains("participant \"B\" #LightSkyBlue"));
        // 外部クラスと Caller は色なし
        assertTrue(puml, puml.contains("participant \"Service\"\n"));
        assertTrue(puml, puml.contains("participant \"Caller\"\n"));
    }

    @Test
    public void testProjectClassColorDisabled() {
        PlantUmlSequenceDiagram.Options o = new PlantUmlSequenceDiagram.Options();
        o.highlightProjectClasses = false;
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { void run() {} }");
        String puml = PlantUmlSequenceDiagram.generate(infos, "A", "run", o);
        // 色は出さない
        assertFalse(puml, puml.contains("#LightSkyBlue"));
        assertTrue(puml, puml.contains("participant \"A\"\n"));
    }

    @Test
    public void testProjectClassColorCustom() {
        PlantUmlSequenceDiagram.Options o = new PlantUmlSequenceDiagram.Options();
        o.projectClassColor = "#FFE4B5";
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { void run() {} }");
        String puml = PlantUmlSequenceDiagram.generate(infos, "A", "run", o);
        assertTrue(puml, puml.contains("participant \"A\" #FFE4B5"));
    }

    @Test
    public void testProjectClassColorEmptyStringDisablesColoring() {
        PlantUmlSequenceDiagram.Options o = new PlantUmlSequenceDiagram.Options();
        o.projectClassColor = "";
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { void run() {} }");
        String puml = PlantUmlSequenceDiagram.generate(infos, "A", "run", o);
        assertFalse(puml, puml.contains("#LightSkyBlue"));
        assertTrue(puml, puml.contains("participant \"A\"\n"));
    }

    @Test
    public void testListCandidatesExcludesAbstract() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "interface I { void m(); } class C { void run() {} }");
        List<PlantUmlSequenceDiagram.Candidate> list =
                PlantUmlSequenceDiagram.listCandidates(infos);
        // abstract メソッド (interface 内) は除外
        assertEquals(1, list.size());
        assertEquals("C.run", list.get(0).getEntry());
    }
}
