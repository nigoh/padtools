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
}
