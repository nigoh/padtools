package padtools.core.formats.uml;

import org.junit.Test;

import java.util.ArrayList;
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
    public void testInteractiveLinksDisabledByDefault() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "package x; class Foo { void m() {} }");
        String puml = PlantUmlClassDiagram.generate(infos);
        assertFalse("[[padtools://...]] should not appear by default: " + puml,
                puml.contains("[[padtools://"));
    }

    @Test
    public void testInteractiveLinksEmitsUrlPerClass() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "package x; class Foo { void m() {} } class Bar { void n() {} }");
        PlantUmlClassDiagram.Options opts = new PlantUmlClassDiagram.Options();
        opts.interactiveLinks = true;
        String puml = PlantUmlClassDiagram.generate(infos, opts);
        assertTrue(puml, puml.contains("[[padtools://class/x.Foo]]"));
        assertTrue(puml, puml.contains("[[padtools://class/x.Bar]]"));
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
    public void testAaosBindingEmitsBindsAndImplements() {
        // Manager → AIDL → Service の三者揃いでクラス図に <<binds>> と <<implements>> が出る
        List<JavaClassInfo> infos = new ArrayList<>();
        infos.addAll(JavaStructureExtractor.extract(
                "package android.car.audio;\n"
                        + "public class CarAudioManager { ICarAudio mService; }\n"));
        infos.addAll(AidlParser.parse(
                "package android.car.audio;\n"
                        + "interface ICarAudio { void play(); }\n"));
        infos.addAll(JavaStructureExtractor.extract(
                "package com.android.car;\n"
                        + "public class CarAudioService extends ICarAudio.Stub { public void play() {} }\n"));
        String puml = PlantUmlClassDiagram.generate(infos);
        assertTrue(puml, puml.contains("<<binds>>"));
        assertTrue(puml, puml.contains("<<implements>>"));
    }

    @Test
    public void testAaosBindingCanBeDisabled() {
        List<JavaClassInfo> infos = new ArrayList<>();
        infos.addAll(JavaStructureExtractor.extract(
                "package android.car.audio;\n"
                        + "public class CarAudioManager { ICarAudio mService; }\n"));
        infos.addAll(AidlParser.parse(
                "package android.car.audio;\n"
                        + "interface ICarAudio { void play(); }\n"));
        PlantUmlClassDiagram.Options o = new PlantUmlClassDiagram.Options();
        o.showAaosBindings = false;
        String puml = PlantUmlClassDiagram.generate(infos, o);
        assertFalse(puml, puml.contains("<<binds>>"));
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

    // --- コメント表示 ---

    @Test
    public void testJavadocOnClassEmitsInlineByDefault() {
        String src = "/** ユーザを表すクラス */\nclass User { int id; }";
        String puml = PlantUmlClassDiagram.generate(
                JavaStructureExtractor.extract(src));
        // INLINE モードがデフォルト。デフォルト色 (#008800) でラップされる
        assertTrue(puml, puml.contains(".. <color:#008800>ユーザを表すクラス</color> .."));
    }

    @Test
    public void testJavadocOnFieldAndMethodEmitsInline() {
        String src = "class C {\n"
                + "  /** ユーザID */\n"
                + "  int id;\n"
                + "  /** 表示名を返す */\n"
                + "  String name() { return null; }\n"
                + "}";
        String puml = PlantUmlClassDiagram.generate(
                JavaStructureExtractor.extract(src));
        assertTrue(puml, puml.contains(".. <color:#008800>ユーザID</color> .."));
        assertTrue(puml, puml.contains(".. <color:#008800>表示名を返す</color> .."));
    }

    @Test
    public void testLineCommentMergedAndAttached() {
        String src = "class C {\n"
                + "  // 1 行目\n"
                + "  // 2 行目\n"
                + "  int x;\n"
                + "}";
        String puml = PlantUmlClassDiagram.generate(
                JavaStructureExtractor.extract(src));
        // INLINE では先頭行のみ出す
        assertTrue(puml, puml.contains(".. <color:#008800>1 行目</color> .."));
    }

    @Test
    public void testCommentDisabled() {
        PlantUmlClassDiagram.Options o = new PlantUmlClassDiagram.Options();
        o.showComments = false;
        String src = "/** doc */ class C {}";
        String puml = PlantUmlClassDiagram.generate(
                JavaStructureExtractor.extract(src), o);
        assertFalse(puml, puml.contains("doc"));
    }

    @Test
    public void testCommentNoteStyle() {
        PlantUmlClassDiagram.Options o = new PlantUmlClassDiagram.Options();
        o.commentStyle = PlantUmlClassDiagram.CommentStyle.NOTE;
        String src = "/** クラスの説明 */\nclass C {\n"
                + "  /** field の説明 */ int x;\n"
                + "}";
        String puml = PlantUmlClassDiagram.generate(
                JavaStructureExtractor.extract(src), o);
        // INLINE 形式 (..) は出ない
        assertFalse(puml, puml.contains(".. クラスの説明 .."));
        // クラスレベル note
        assertTrue(puml, puml.contains("note top of"));
        assertTrue(puml, puml.contains("クラスの説明"));
        // メンバーレベル note
        assertTrue(puml, puml.contains("note right of"));
        assertTrue(puml, puml.contains("::x"));
        assertTrue(puml, puml.contains("field の説明"));
        assertTrue(puml, puml.contains("end note"));
    }

    @Test
    public void testCommentJavadocStripsLeadingAsterisksAndTags() {
        String src = "/**\n"
                + " * 概要 1 行目。\n"
                + " * 詳細 2 行目。\n"
                + " * @param x ignored\n"
                + " */\n"
                + "class C { void m() {} }";
        String puml = PlantUmlClassDiagram.generate(
                JavaStructureExtractor.extract(src));
        // INLINE モードでは先頭 1 行のみ
        assertTrue(puml, puml.contains(".. <color:#008800>概要 1 行目。</color> .."));
        // @param 行は表示されない
        assertFalse(puml, puml.contains("@param"));
    }

    @Test
    public void testCommentInlineLengthLimited() {
        PlantUmlClassDiagram.Options o = new PlantUmlClassDiagram.Options();
        o.commentMaxLength = 10;
        String src = "/** 非常に非常に非常に長いコメントです */ class C {}";
        String puml = PlantUmlClassDiagram.generate(
                JavaStructureExtractor.extract(src), o);
        assertTrue("expected truncated marker '…'", puml.contains("…"));
    }

    @Test
    public void testCommentNotAttachedAcrossDecls() {
        // /** doc */ は foo の直前にあり、bar の直前にはない
        String src = "class C {\n"
                + "  /** doc */\n"
                + "  int foo;\n"
                + "  int bar;\n"
                + "}";
        String puml = PlantUmlClassDiagram.generate(
                JavaStructureExtractor.extract(src));
        // foo にだけ付く想定。bar に "doc" が出ないこと
        int count = puml.split("\\.\\. <color:#008800>doc</color> \\.\\.", -1).length - 1;
        assertEquals("doc コメントが 1 箇所のみ出ること", 1, count);
    }

    @Test
    public void testInlineCommentWrappedInColorTag() {
        // デフォルト色 (#008800) がインラインコメントを <color:...> で囲む
        String src = "/** クラス説明 */ class C {}";
        String puml = PlantUmlClassDiagram.generate(
                JavaStructureExtractor.extract(src));
        assertTrue(puml, puml.contains("<color:#008800>クラス説明</color>"));
    }

    @Test
    public void testInlineCommentColorCustomizable() {
        // commentColor を任意の値に差し替えられる
        PlantUmlClassDiagram.Options o = new PlantUmlClassDiagram.Options();
        o.commentColor = "#FF00AA";
        String src = "/** クラス説明 */ class C {}";
        String puml = PlantUmlClassDiagram.generate(
                JavaStructureExtractor.extract(src), o);
        assertTrue(puml, puml.contains(".. <color:#FF00AA>クラス説明</color> .."));
    }

    @Test
    public void testInlineCommentColorDisabledWhenEmpty() {
        // commentColor が空なら従来通り色タグなしで出力する
        PlantUmlClassDiagram.Options o = new PlantUmlClassDiagram.Options();
        o.commentColor = "";
        String src = "/** クラス説明 */ class C {}";
        String puml = PlantUmlClassDiagram.generate(
                JavaStructureExtractor.extract(src), o);
        assertTrue(puml, puml.contains(".. クラス説明 .."));
        assertFalse(puml, puml.contains("<color:"));
    }

    @Test
    public void testNoteStyleEmitsSkinparamForCommentColor() {
        // NOTE モード時はコメント色を skinparam noteBorderColor / noteFontColor に流す
        PlantUmlClassDiagram.Options o = new PlantUmlClassDiagram.Options();
        o.commentStyle = PlantUmlClassDiagram.CommentStyle.NOTE;
        String src = "/** クラス説明 */ class C {}";
        String puml = PlantUmlClassDiagram.generate(
                JavaStructureExtractor.extract(src), o);
        assertTrue(puml, puml.contains("skinparam noteBorderColor #008800"));
        assertTrue(puml, puml.contains("skinparam noteFontColor #008800"));
    }

    // --- アノテーション表示 ---

    @Test
    public void testAnnotationsOnMembersEmittedByDefault() {
        String src = "class C {\n"
                + "  @Nullable\n"
                + "  String name;\n"
                + "  @Deprecated\n"
                + "  void m() {}\n"
                + "}";
        String puml = PlantUmlClassDiagram.generate(
                JavaStructureExtractor.extract(src));
        assertTrue(puml, puml.contains("@Nullable"));
        assertTrue(puml, puml.contains("@Deprecated"));
    }

    @Test
    public void testAnnotationsHiddenByDefaultForOverride() {
        String src = "class C { @Override public String toString() { return null; } }";
        String puml = PlantUmlClassDiagram.generate(
                JavaStructureExtractor.extract(src));
        // @Override は既定で非表示
        assertFalse(puml, puml.contains("@Override"));
    }

    @Test
    public void testAnnotationsDisabled() {
        PlantUmlClassDiagram.Options o = new PlantUmlClassDiagram.Options();
        o.showAnnotations = false;
        String src = "class C { @Nullable String name; }";
        String puml = PlantUmlClassDiagram.generate(
                JavaStructureExtractor.extract(src), o);
        assertFalse(puml, puml.contains("@Nullable"));
    }

    // --- enum 定数 ---

    @Test
    public void testEnumConstantsEmittedByDefault() {
        String src = "enum Color { RED, GREEN, BLUE }";
        String puml = PlantUmlClassDiagram.generate(
                JavaStructureExtractor.extract(src));
        assertTrue(puml, puml.contains("RED"));
        assertTrue(puml, puml.contains("GREEN"));
        assertTrue(puml, puml.contains("BLUE"));
    }

    @Test
    public void testEnumConstantsWithMembersSeparated() {
        String src = "enum E { A, B; int x() { return 1; } }";
        String puml = PlantUmlClassDiagram.generate(
                JavaStructureExtractor.extract(src));
        assertTrue(puml, puml.contains("A"));
        assertTrue(puml, puml.contains("B"));
        // PlantUML の区切り '--' が定数とメンバーの間に入る
        assertTrue(puml, puml.contains("--"));
        assertTrue(puml, puml.contains("x(): int"));
    }

    @Test
    public void testEnumConstantsDisabled() {
        PlantUmlClassDiagram.Options o = new PlantUmlClassDiagram.Options();
        o.showEnumConstants = false;
        String src = "enum Color { RED, GREEN }";
        String puml = PlantUmlClassDiagram.generate(
                JavaStructureExtractor.extract(src), o);
        // クラス本体内に RED が出ないこと (class エイリアスとは別に)
        assertFalse(puml, puml.contains("\n  RED\n"));
        assertFalse(puml, puml.contains("\n  GREEN\n"));
    }

    // --- final マーカー ---

    @Test
    public void testFinalMarkerEmittedByDefault() {
        String src = "class C { public final int N = 1; }";
        String puml = PlantUmlClassDiagram.generate(
                JavaStructureExtractor.extract(src));
        assertTrue(puml, puml.contains("{final}"));
    }

    @Test
    public void testFinalMarkerDisabled() {
        PlantUmlClassDiagram.Options o = new PlantUmlClassDiagram.Options();
        o.showFinal = false;
        String src = "class C { public final int N = 1; }";
        String puml = PlantUmlClassDiagram.generate(
                JavaStructureExtractor.extract(src), o);
        assertFalse(puml, puml.contains("{final}"));
    }

    @Test
    public void testJetpackStereotypeOffByDefault() {
        // ソース直接抽出経路 (JavaStructureExtractor.extract) では Jetpack 分類が走らないので、
        // 明示的にステレオタイプを設定して --jetpack の Options ゲートだけを検証する。
        JavaClassInfo c = new JavaClassInfo();
        c.setPackageName("com.x");
        c.setSimpleName("Home");
        c.setKind(JavaClassInfo.Kind.CLASS);
        c.getJetpackStereotypes().add("Fragment");
        String puml = PlantUmlClassDiagram.generate(java.util.Collections.singletonList(c));
        assertFalse(puml, puml.contains("<<Fragment>>"));
        assertFalse(puml, puml.contains("Jetpack ステレオタイプ"));
    }

    @Test
    public void testJetpackStereotypeWhenEnabled() {
        JavaClassInfo c = new JavaClassInfo();
        c.setPackageName("com.x");
        c.setSimpleName("Home");
        c.setKind(JavaClassInfo.Kind.CLASS);
        c.getJetpackStereotypes().add("Fragment");
        PlantUmlClassDiagram.Options o = new PlantUmlClassDiagram.Options();
        o.jetpack.enabled = true;
        String puml = PlantUmlClassDiagram.generate(
                java.util.Collections.singletonList(c), o);
        assertTrue(puml, puml.contains("<<Fragment>>"));
        assertTrue(puml, puml.contains("Jetpack ステレオタイプ"));
    }
}
