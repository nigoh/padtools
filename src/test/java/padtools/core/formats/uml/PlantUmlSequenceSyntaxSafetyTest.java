package padtools.core.formats.uml;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * シーケンス図生成が「構文的に妥当な」PlantUML を出すことを保証する回帰テスト。
 *
 * <p>participant 宣言は常に引用符付き ({@code participant "Name"}) なのに、矢印や
 * activate/deactivate 行で同じ名前を素のまま参照すると、{@code $} や空白を含む名前
 * (内部クラス・匿名コールバック等) で PlantUML が構文エラーを起こしていた。{@link
 * PlantUmlSequenceDiagram#idRef(String)} で参照側も特殊文字時に引用符で囲むことを検証する。</p>
 */
public class PlantUmlSequenceSyntaxSafetyTest {

    @Test
    public void idRefLeavesSimpleNamesBare() {
        assertEquals("A", PlantUmlSequenceDiagram.idRef("A"));
        assertEquals("foo", PlantUmlSequenceDiagram.idRef("foo"));
        assertEquals("IAudio", PlantUmlSequenceDiagram.idRef("IAudio"));
        assertEquals("my_Class1", PlantUmlSequenceDiagram.idRef("my_Class1"));
    }

    @Test
    public void idRefQuotesNamesNeedingEscaping() {
        assertEquals("\"Outer$Inner\"", PlantUmlSequenceDiagram.idRef("Outer$Inner"));
        assertEquals("\"a b\"", PlantUmlSequenceDiagram.idRef("a b"));
        assertEquals("\"List<String>\"", PlantUmlSequenceDiagram.idRef("List<String>"));
        assertEquals("\"a.b.C\"", PlantUmlSequenceDiagram.idRef("a.b.C"));
    }

    @Test
    public void escapeLabelCollapsesNewlines() {
        assertEquals("a b c", PlantUmlSequenceDiagram.escapeLabel("a\nb\r\nc"));
        assertEquals("foo bar", PlantUmlSequenceDiagram.escapeLabel("foo    bar"));
    }

    @Test
    public void inlineCallbackDiagramRendersWithoutSyntaxError() throws IOException {
        // ラムダ/匿名クラスのコールバックは participant 名に "$" を含む ("A$run" など)。
        // 宣言・参照とも引用符で囲まれていないと PlantUML が構文エラーになる。
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "import java.util.List;\n"
                + "class A {\n"
                + "  List<String> items;\n"
                + "  void run() { items.forEach(s -> handle(s)); }\n"
                + "  void handle(String s) {}\n"
                + "}\n");
        String puml = PlantUmlSequenceDiagram.generate(infos, "A", "run", null);
        // "$" を含む participant が出力されていること (前提条件の確認)
        assertTrue("expected an inline ($) participant in: " + puml, puml.contains("$"));
        assertNoPlantUmlSyntaxError(puml);
    }

    @Test
    public void emptyDiagramRendersWithoutSyntaxError() throws IOException {
        // 起点メソッドが存在しないケース (emptyDiagram 経路) も妥当な PlantUML であること。
        String puml = PlantUmlSequenceDiagram.generate(
                JavaStructureExtractor.extract("class A {}"), "A", "missing", null);
        assertNoPlantUmlSyntaxError(puml);
    }

    /** 生成 PlantUML を実レンダリングし、PlantUML の構文エラー画像にならないことを確認する。 */
    private static void assertNoPlantUmlSyntaxError(String puml) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PlantUmlRenderer.setRendererImplForTest(null);
        PlantUmlRenderer.renderSvg(puml, out);
        String svg = new String(out.toByteArray(), StandardCharsets.UTF_8);
        assertFalse("PlantUML reported a syntax error for:\n" + puml,
                svg.contains("Syntax Error"));
    }
}
