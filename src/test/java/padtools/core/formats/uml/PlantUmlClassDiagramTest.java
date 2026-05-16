package padtools.core.formats.uml;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * PlantUmlClassDiagram のユニットテスト。
 */
public class PlantUmlClassDiagramTest {

    @Test(expected = IllegalArgumentException.class)
    public void testNullClasses() {
        PlantUmlClassDiagram.generate(null);
    }

    @Test
    public void testEmptyClasses() {
        String puml = PlantUmlClassDiagram.generate(java.util.Collections.emptyList());
        assertTrue(puml, puml.contains("@startuml"));
        assertTrue(puml, puml.contains("@enduml"));
    }

    @Test
    public void testSimpleClassEmit() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "package x; class Foo { int a; void m() {} }");
        String puml = PlantUmlClassDiagram.generate(infos);
        assertTrue(puml, puml.contains("@startuml"));
        assertTrue(puml, puml.contains("package \"x\""));
        assertTrue(puml, puml.contains("class \"x.Foo\""));
        assertTrue(puml, puml.contains("a: int"));
        assertTrue(puml, puml.contains("m(): void"));
    }

    @Test
    public void testInterfaceEmit() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "interface I { void m(); }");
        String puml = PlantUmlClassDiagram.generate(infos);
        assertTrue(puml, puml.contains("interface \"I\""));
        assertTrue(puml, puml.contains("{abstract}"));
    }

    @Test
    public void testEnumEmit() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "enum E { A; int x() { return 1; } }");
        String puml = PlantUmlClassDiagram.generate(infos);
        assertTrue(puml, puml.contains("enum \"E\""));
    }

    @Test
    public void testAnnotationEmit() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "@interface A { String value(); }");
        String puml = PlantUmlClassDiagram.generate(infos);
        assertTrue(puml, puml.contains("annotation \"A\""));
    }

    @Test
    public void testAbstractClassEmit() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "abstract class A { abstract void m(); }");
        String puml = PlantUmlClassDiagram.generate(infos);
        assertTrue(puml, puml.contains("abstract class \"A\""));
    }

    @Test
    public void testVisibilityMarks() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class C { public int a; private int b; protected int c; int d; }");
        String puml = PlantUmlClassDiagram.generate(infos);
        assertTrue(puml, puml.contains("+a: int"));
        assertTrue(puml, puml.contains("-b: int"));
        assertTrue(puml, puml.contains("#c: int"));
        assertTrue(puml, puml.contains("~d: int"));
    }

    @Test
    public void testInheritanceEmit() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class B {} class A extends B implements I1, I2 {}");
        String puml = PlantUmlClassDiagram.generate(infos);
        // 継承の表記
        assertTrue(puml, puml.contains("<|--"));
        // 実装の表記
        assertTrue(puml, puml.contains("<|.."));
    }

    @Test
    public void testUsageRelationsEmit() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class B {} class A { B b; }");
        String puml = PlantUmlClassDiagram.generate(infos);
        assertTrue("expected --> usage", puml.contains("-->"));
    }

    @Test
    public void testUsageRelationsIgnorePrimitives() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { int a; String s; long l; }");
        String puml = PlantUmlClassDiagram.generate(infos);
        // ユーザ定義型が無いので --> が無いことを確認
        assertFalse(puml, puml.contains("-->"));
    }

    @Test
    public void testAaosCategoryStereotype() {
        JavaClassInfo c = new JavaClassInfo();
        c.setPackageName("android.car.audio");
        c.setSimpleName("CarAudioManager");
        c.setKind(JavaClassInfo.Kind.CLASS);
        String puml = PlantUmlClassDiagram.generate(Arrays.asList(c));
        assertTrue(puml, puml.contains("<<CarManager>>"));
    }

    @Test
    public void testOptionDisableVisibility() {
        PlantUmlClassDiagram.Options o = new PlantUmlClassDiagram.Options();
        o.showVisibility = false;
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class C { public int a; }");
        String puml = PlantUmlClassDiagram.generate(infos, o);
        assertFalse(puml, puml.contains("+a"));
        assertTrue(puml, puml.contains("a: int"));
    }

    @Test
    public void testOptionDisableInheritance() {
        PlantUmlClassDiagram.Options o = new PlantUmlClassDiagram.Options();
        o.showInheritance = false;
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class B {} class A extends B {}");
        String puml = PlantUmlClassDiagram.generate(infos, o);
        assertFalse(puml, puml.contains("<|--"));
    }

    @Test
    public void testOptionDisableUsage() {
        PlantUmlClassDiagram.Options o = new PlantUmlClassDiagram.Options();
        o.showUsageRelations = false;
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class B {} class A { B b; }");
        String puml = PlantUmlClassDiagram.generate(infos, o);
        assertFalse(puml, puml.contains("-->"));
    }

    @Test
    public void testOptionTitle() {
        PlantUmlClassDiagram.Options o = new PlantUmlClassDiagram.Options();
        o.title = "MyProject";
        String puml = PlantUmlClassDiagram.generate(
                JavaStructureExtractor.extract("class C {}"), o);
        assertTrue(puml, puml.contains("title MyProject"));
    }

    @Test
    public void testStaticMember() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class C { public static final int N = 1; public static void m() {} }");
        String puml = PlantUmlClassDiagram.generate(infos);
        assertTrue(puml, puml.contains("{static}"));
    }

    @Test
    public void testConstructorEmit() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class Foo { public Foo(int x) {} }");
        String puml = PlantUmlClassDiagram.generate(infos);
        assertTrue(puml, puml.contains("Foo(x: int)"));
        // コンストラクタは戻り型を表示しない
        assertFalse(puml, puml.contains("Foo(x: int): "));
    }

    @Test
    public void testGroupByPackage() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "package x; class A {}");
        String puml = PlantUmlClassDiagram.generate(infos);
        assertTrue(puml, puml.contains("package \"x\""));
        assertTrue(puml, puml.contains("class \"x.A\""));
    }

    @Test
    public void testAidlInterfaceMarked() {
        String aidl = "package c; interface ICar { int v(); }";
        List<JavaClassInfo> infos = AidlParser.parse(aidl);
        for (JavaClassInfo c : infos) {
            c.setAaosCategory(AaosPattern.categorize(c));
        }
        String puml = PlantUmlClassDiagram.generate(infos);
        assertTrue(puml, puml.contains("<<AIDL>>"));
        assertTrue(puml, puml.contains("interface \"c.ICar\""));
    }

    // --- 凡例 ---

    @Test
    public void testLegendIncludedByDefault() {
        String puml = PlantUmlClassDiagram.generate(
                JavaStructureExtractor.extract("class A {}"));
        assertTrue(puml, puml.contains("legend right"));
        assertTrue(puml, puml.contains("endlegend"));
    }

    @Test
    public void testLegendDisabled() {
        PlantUmlClassDiagram.Options o = new PlantUmlClassDiagram.Options();
        o.includeLegend = false;
        String puml = PlantUmlClassDiagram.generate(
                JavaStructureExtractor.extract("class A {}"), o);
        assertFalse(puml, puml.contains("legend right"));
    }

    @Test
    public void testLegendShowsVisibilitySection() {
        String puml = PlantUmlClassDiagram.generate(
                JavaStructureExtractor.extract("class A { public int x; }"));
        assertTrue(puml, puml.contains("== 可視性 =="));
        assertTrue(puml, puml.contains("+ public"));
    }

    @Test
    public void testLegendShowsStereotypeOnlyWhenUsed() {
        // AAOS パターンを使わないクラスでは <<CarManager>> が凡例に出ない
        String puml = PlantUmlClassDiagram.generate(
                JavaStructureExtractor.extract("class Foo { void m() {} }"));
        assertFalse(puml, puml.contains("<<CarManager>>"));
        // AAOS Manager パターンのクラスを含む場合は出る
        JavaClassInfo c = new JavaClassInfo();
        c.setPackageName("android.car.audio");
        c.setSimpleName("CarAudioManager");
        c.setKind(JavaClassInfo.Kind.CLASS);
        String puml2 = PlantUmlClassDiagram.generate(java.util.Arrays.asList(c));
        assertTrue(puml2, puml2.contains("<<CarManager>>"));
    }

    @Test
    public void testLegendOmitsUsageSectionWhenNoRelations() {
        // すべて primitive 型 → --> セクションは凡例にも出ない
        String puml = PlantUmlClassDiagram.generate(
                JavaStructureExtractor.extract("class A { int a; String s; }"));
        assertFalse(puml, puml.contains("A --> B"));
    }

    @Test
    public void testLegendIncludesInheritanceWhenPresent() {
        String puml = PlantUmlClassDiagram.generate(
                JavaStructureExtractor.extract("class B {} class A extends B {}"));
        assertTrue(puml, puml.contains("A <|-- B"));
    }

    @Test
    public void testAndroidComponentStereotype() {
        JavaClassInfo c = new JavaClassInfo();
        c.setPackageName("com.x");
        c.setSimpleName("MainActivity");
        c.setKind(JavaClassInfo.Kind.CLASS);
        c.setAndroidComponentType("Activity");
        String puml = PlantUmlClassDiagram.generate(java.util.Arrays.asList(c));
        assertTrue(puml, puml.contains("<<Activity>>"));
        // 凡例セクションに「Android コンポーネント」も出る
        assertTrue(puml, puml.contains("== Android コンポーネント =="));
    }

    @Test
    public void testNoAndroidSectionWhenAbsent() {
        // androidComponentType が一切無いクラスでは Android セクションは凡例にも出ない
        String puml = PlantUmlClassDiagram.generate(
                JavaStructureExtractor.extract("class A {}"));
        assertFalse(puml, puml.contains("== Android コンポーネント =="));
    }
}
