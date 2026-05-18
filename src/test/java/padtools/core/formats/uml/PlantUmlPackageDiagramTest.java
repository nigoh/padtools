package padtools.core.formats.uml;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * PlantUmlPackageDiagram のユニットテスト。
 */
public class PlantUmlPackageDiagramTest {

    @Test(expected = IllegalArgumentException.class)
    public void testNullClasses() {
        PlantUmlPackageDiagram.generate(null);
    }

    @Test
    public void testEmptyClasses() {
        String puml = PlantUmlPackageDiagram.generate(java.util.Collections.emptyList());
        assertTrue(puml, puml.contains("@startuml"));
        assertTrue(puml, puml.contains("@enduml"));
    }

    @Test
    public void testSinglePackageEmit() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "package com.x; class Foo {} class Bar {}");
        String puml = PlantUmlPackageDiagram.generate(infos);
        assertTrue(puml, puml.contains("package \"com.x"));
        assertTrue(puml, puml.contains("2 classes"));
    }

    @Test
    public void testDependencyArrowFromFieldType() {
        // Foo (com.a) が Bar (com.b) を保持 → com.a → com.b の矢印
        List<JavaClassInfo> infos = new java.util.ArrayList<>();
        infos.addAll(JavaStructureExtractor.extract(
                "package com.a; class Foo { com.b.Bar bar; }"));
        infos.addAll(JavaStructureExtractor.extract(
                "package com.b; class Bar {}"));
        String puml = PlantUmlPackageDiagram.generate(infos);
        // com.a と com.b のパッケージノードが出ること
        assertTrue(puml, puml.contains("package \"com.a"));
        assertTrue(puml, puml.contains("package \"com.b"));
        // パッケージ間の矢印 (--> ) が 1 本以上含まれること
        // 具体的なエイリアスは生成順に依存するので、--> の存在のみ検証
        assertTrue(puml, puml.contains(" --> "));
    }

    @Test
    public void testDependencyArrowFromInheritance() {
        List<JavaClassInfo> infos = new java.util.ArrayList<>();
        infos.addAll(JavaStructureExtractor.extract(
                "package com.a; class Foo extends com.b.Bar {}"));
        infos.addAll(JavaStructureExtractor.extract(
                "package com.b; class Bar {}"));
        String puml = PlantUmlPackageDiagram.generate(infos);
        assertTrue(puml, puml.contains(" --> "));
    }

    @Test
    public void testSelfLoopSuppressed() {
        // 同一パッケージ内のクラス参照は自己ループとして抑止される
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "package com.a; class Foo { Bar b; } class Bar {}");
        String puml = PlantUmlPackageDiagram.generate(infos);
        // 同一パッケージのみで矢印が出ないこと
        assertFalse("self loop should be suppressed", puml.contains(" --> "));
    }

    @Test
    public void testUnknownTypeIgnored() {
        // 既知のクラス集合に含まれない型 (java.util.List 等) は無視される
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "package com.a; import java.util.List; class Foo { List<String> xs; }");
        String puml = PlantUmlPackageDiagram.generate(infos);
        assertFalse("external library types should not produce arrows",
                puml.contains(" --> "));
    }

    @Test
    public void testIncludeLegend() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "package x; class Foo {}");
        PlantUmlPackageDiagram.Options opts = new PlantUmlPackageDiagram.Options();
        opts.includeLegend = true;
        String puml = PlantUmlPackageDiagram.generate(infos, opts);
        assertTrue(puml, puml.contains("legend right"));
        assertTrue(puml, puml.contains("end legend"));
    }

    @Test
    public void testSuppressLegend() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "package x; class Foo {}");
        PlantUmlPackageDiagram.Options opts = new PlantUmlPackageDiagram.Options();
        opts.includeLegend = false;
        String puml = PlantUmlPackageDiagram.generate(infos, opts);
        assertFalse(puml, puml.contains("legend right"));
    }

    @Test
    public void testTitle() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "package x; class Foo {}");
        PlantUmlPackageDiagram.Options opts = new PlantUmlPackageDiagram.Options();
        opts.title = "My Project";
        String puml = PlantUmlPackageDiagram.generate(infos, opts);
        assertTrue(puml, puml.contains("title My Project"));
    }

    @Test
    public void testDefaultPackageLabel() {
        // パッケージ宣言なし → "(default)" ラベルで出力
        List<JavaClassInfo> infos = JavaStructureExtractor.extract("class Foo {}");
        String puml = PlantUmlPackageDiagram.generate(infos);
        assertTrue(puml, puml.contains("(default)"));
    }
}
