package padtools.core.funcdiff;

import org.junit.Test;
import padtools.core.formats.uml.JavaClassInfo;
import padtools.core.formats.uml.JavaMethodInfo;
import padtools.core.formats.uml.JavaStructureExtractor;

import java.util.List;

import static org.junit.Assert.*;

public class MethodDiffAnalyzerTest {

    // -------------------------------------------------------------------------
    // ヘルパー
    // -------------------------------------------------------------------------

    private static JavaMethodInfo extractMethod(String src, String className,
                                                 String methodName) {
        List<JavaClassInfo> classes = JavaStructureExtractor.extract(src);
        MethodDiffAnalyzer.MethodSpec spec =
                new MethodDiffAnalyzer.MethodSpec("dummy.java", className, methodName);
        return MethodDiffAnalyzer.findMethod(classes, spec);
    }

    private static MethodDiffAnalyzer.DiffResult diff(JavaMethodInfo mA, String nameA,
                                                       JavaMethodInfo mB, String nameB) {
        return MethodDiffAnalyzer.analyze(
                mA, new MethodDiffAnalyzer.MethodSpec("A.java", null, nameA),
                mB, new MethodDiffAnalyzer.MethodSpec("B.java", null, nameB));
    }

    // -------------------------------------------------------------------------
    // parseSpec テスト
    // -------------------------------------------------------------------------

    @Test
    public void parseSpecWithClassAndMethod() {
        MethodDiffAnalyzer.MethodSpec s =
                MethodDiffAnalyzer.parseSpec("path/to/Foo.java::Bar.baz");
        assertEquals("path/to/Foo.java", s.filePath);
        assertEquals("Bar", s.className);
        assertEquals("baz", s.methodName);
    }

    @Test
    public void parseSpecMethodOnly() {
        MethodDiffAnalyzer.MethodSpec s =
                MethodDiffAnalyzer.parseSpec("Foo.java::baz");
        assertEquals("Foo.java", s.filePath);
        assertNull(s.className);
        assertEquals("baz", s.methodName);
    }

    @Test(expected = IllegalArgumentException.class)
    public void parseSpecMissingSeparatorThrows() {
        MethodDiffAnalyzer.parseSpec("Foo.java");
    }

    @Test(expected = IllegalArgumentException.class)
    public void parseSpecEmptyThrows() {
        MethodDiffAnalyzer.parseSpec("");
    }

    // -------------------------------------------------------------------------
    // 機能テスト: 精度評価ケース①「完全一致」
    // -------------------------------------------------------------------------

    @Test
    public void identicalMethods_allMetricsAreOne() {
        String src = "class A { X x; void run() { x.init(); x.connect(); x.close(); } }";
        JavaMethodInfo m = extractMethod(src, "A", "run");
        assertNotNull("run() メソッドが見つからない", m);
        MethodDiffAnalyzer.DiffResult r = diff(m, "run", m, "run");

        assertEquals(1.0, r.metrics.lcsSimilarity, 0.001);
        assertEquals(0, r.metrics.editDistance);
        assertEquals(1.0, r.metrics.normalizedEditSimilarity, 0.001);
        assertEquals(1.0, r.metrics.jaccard, 0.001);
        assertEquals(3, r.matchCount);
        assertEquals(0, r.onlyACount);
        assertEquals(0, r.onlyBCount);
        assertEquals(1.0, r.avgConfidence, 0.001);
    }

    // -------------------------------------------------------------------------
    // 精度評価ケース②「1操作だけ変えたペア」: edit_distance=1
    // -------------------------------------------------------------------------

    @Test
    public void oneExtraCallInB_editDistanceIsOne() {
        String srcA = "class A { X x; void go() { x.init(); x.start(); } }";
        String srcB = "class B { X x; void go() { x.init(); x.start(); x.stop(); } }";
        JavaMethodInfo mA = extractMethod(srcA, "A", "go");
        JavaMethodInfo mB = extractMethod(srcB, "B", "go");
        assertNotNull(mA);
        assertNotNull(mB);

        MethodDiffAnalyzer.DiffResult r = diff(mA, "go", mB, "go");

        // Levenshtein: A に stop() を1回挿入 = 編集距離1
        assertEquals(1, r.metrics.editDistance);
        assertEquals(1, r.onlyBCount);
        assertEquals(0, r.onlyACount);
    }

    // -------------------------------------------------------------------------
    // 精度評価ケース③「完全異なるペア」: 全指標=0
    // -------------------------------------------------------------------------

    @Test
    public void completelyDifferentMethods_allMetricsAreZero() {
        String srcA = "class A { X x; void foo() { x.alpha(); x.beta(); } }";
        String srcB = "class B { Y y; void bar() { y.gamma(); y.delta(); } }";
        JavaMethodInfo mA = extractMethod(srcA, "A", "foo");
        JavaMethodInfo mB = extractMethod(srcB, "B", "bar");
        assertNotNull(mA);
        assertNotNull(mB);

        MethodDiffAnalyzer.DiffResult r = diff(mA, "foo", mB, "bar");

        assertEquals(0, r.metrics.lcsLen);
        assertEquals(0.0, r.metrics.lcsSimilarity, 0.001);
        assertEquals(0.0, r.metrics.jaccard, 0.001);
        // edit_distance = max(2,2) = 2（全置換または削除+挿入）
        assertTrue(r.metrics.editDistance > 0);
        assertEquals(0, r.matchCount);
    }

    // -------------------------------------------------------------------------
    // 精度評価ケース④「順序入れ替えのみ」: Jaccard=1.0, LCS<1.0
    // -------------------------------------------------------------------------

    @Test
    public void reversedOrder_jaccardOneButLcsLessThanOne() {
        String srcA = "class A { X x; void go() { x.a(); x.b(); x.c(); } }";
        String srcB = "class B { X x; void go() { x.c(); x.b(); x.a(); } }";
        JavaMethodInfo mA = extractMethod(srcA, "A", "go");
        JavaMethodInfo mB = extractMethod(srcB, "B", "go");
        assertNotNull(mA);
        assertNotNull(mB);

        MethodDiffAnalyzer.DiffResult r = diff(mA, "go", mB, "go");

        // Jaccard: {a,b,c} vs {c,b,a} → 集合同一なので 1.0
        assertEquals(1.0, r.metrics.jaccard, 0.001);
        // LCS: [a,b,c] vs [c,b,a] → LCS は "b" のみ(長さ1) または "a" または "c"
        // どれでも LCS_length < 3
        assertTrue(r.metrics.lcsSimilarity < 1.0);
    }

    // -------------------------------------------------------------------------
    // 精度評価ケース⑤「追加呼び出しのみ」: Levenshtein = 追加数
    // -------------------------------------------------------------------------

    @Test
    public void multipleExtraCallsInA_levenshteinEqualsExtras() {
        String srcA = "class A { X x; void go() {"
                    + " x.init(); x.extra1(); x.extra2(); x.extra3(); } }";
        String srcB = "class B { X x; void go() { x.init(); } }";
        JavaMethodInfo mA = extractMethod(srcA, "A", "go");
        JavaMethodInfo mB = extractMethod(srcB, "B", "go");
        assertNotNull(mA);
        assertNotNull(mB);

        MethodDiffAnalyzer.DiffResult r = diff(mA, "go", mB, "go");

        // init()がマッチ、extra1/extra2/extra3 は削除 → 編集距離3
        assertEquals(3, r.metrics.editDistance);
        assertEquals(3, r.onlyACount);
    }

    // -------------------------------------------------------------------------
    // receiver が異なる場合: PARTIAL 判定
    // -------------------------------------------------------------------------

    @Test
    public void differentReceiver_isPartial() {
        String srcA = "class A { Foo f; void go() { f.save(); } }";
        String srcB = "class B { Bar b; void go() { b.save(); } }";
        JavaMethodInfo mA = extractMethod(srcA, "A", "go");
        JavaMethodInfo mB = extractMethod(srcB, "B", "go");
        assertNotNull(mA);
        assertNotNull(mB);

        MethodDiffAnalyzer.DiffResult r = diff(mA, "go", mB, "go");

        assertEquals(1, r.partialCount);
        assertEquals(MethodDiffAnalyzer.MatchKind.PARTIAL, r.rows.get(0).kind);
        assertTrue(r.rows.get(0).confidence < 1.0);
    }

    // -------------------------------------------------------------------------
    // メソッド未検出時: 空の DiffResult を返す
    // -------------------------------------------------------------------------

    @Test
    public void methodNotFound_producesEmptyRows() {
        MethodDiffAnalyzer.DiffResult r = MethodDiffAnalyzer.analyze(
                null, new MethodDiffAnalyzer.MethodSpec("X.java", "X", "gone"),
                null, new MethodDiffAnalyzer.MethodSpec("Y.java", "Y", "gone"));
        assertNotNull(r);
        assertTrue(r.rows.isEmpty());
        assertEquals(1.0, r.metrics.lcsSimilarity, 0.001);
        assertEquals(0, r.metrics.editDistance);
        assertEquals(1.0, r.metrics.jaccard, 0.001);
    }

    // -------------------------------------------------------------------------
    // Markdown レポートの基本構造テスト
    // -------------------------------------------------------------------------

    @Test
    public void report_containsAllSections() {
        String srcA = "class A { X x; void go() { x.init(); x.extra(); } }";
        String srcB = "class B { X x; void go() { x.init(); } }";
        JavaMethodInfo mA = extractMethod(srcA, "A", "go");
        JavaMethodInfo mB = extractMethod(srcB, "B", "go");

        MethodDiffAnalyzer.DiffResult r = diff(mA, "go", mB, "go");
        String md = MarkdownMethodDiffReport.render(r);

        assertTrue(md.contains("# Function Diff Report"));
        assertTrue(md.contains("## Summary"));
        assertTrue(md.contains("## Call Comparison"));
        assertTrue(md.contains("## Diff Detail"));
        assertTrue(md.contains("LCS Similarity"));
        assertTrue(md.contains("Edit Distance"));
        assertTrue(md.contains("Jaccard"));
        assertTrue(md.contains("MATCH"));
        assertTrue(md.contains("ONLY_A"));
    }

    @Test
    public void report_perfectMatch_noDiffSection() {
        String src = "class A { X x; void go() { x.run(); } }";
        JavaMethodInfo m = extractMethod(src, "A", "go");
        MethodDiffAnalyzer.DiffResult r = diff(m, "go", m, "go");
        String md = MarkdownMethodDiffReport.render(r);
        assertTrue(md.contains("No differences found"));
    }

    // -------------------------------------------------------------------------
    // 内部ロジック単体テスト
    // -------------------------------------------------------------------------

    @Test
    public void computeLcsLen_basicCases() {
        // 空リスト
        assertEquals(0, MethodDiffAnalyzer.computeLcsLen(
                calls(), calls("a")));
        // 同一
        assertEquals(3, MethodDiffAnalyzer.computeLcsLen(
                calls("a", "b", "c"), calls("a", "b", "c")));
        // 逆順 → LCS=1
        assertEquals(1, MethodDiffAnalyzer.computeLcsLen(
                calls("a", "b", "c"), calls("c", "b", "a")));
    }

    @Test
    public void computeLevenshtein_basicCases() {
        assertEquals(0, MethodDiffAnalyzer.computeLevenshtein(calls(), calls()));
        assertEquals(3, MethodDiffAnalyzer.computeLevenshtein(
                calls(), calls("a", "b", "c")));
        assertEquals(1, MethodDiffAnalyzer.computeLevenshtein(
                calls("a", "b"), calls("a", "b", "c")));
        // 全置換: [a,b] → [c,d]
        assertEquals(2, MethodDiffAnalyzer.computeLevenshtein(
                calls("a", "b"), calls("c", "d")));
    }

    @Test
    public void computeJaccard_basicCases() {
        assertEquals(1.0, MethodDiffAnalyzer.computeJaccard(calls(), calls()), 0.001);
        assertEquals(1.0, MethodDiffAnalyzer.computeJaccard(
                calls("a"), calls("a")), 0.001);
        assertEquals(0.0, MethodDiffAnalyzer.computeJaccard(
                calls("a"), calls("b")), 0.001);
        // {a,b} ∩ {b,c} = {b} → 1/3
        assertEquals(1.0 / 3.0, MethodDiffAnalyzer.computeJaccard(
                calls("a", "b"), calls("b", "c")), 0.001);
    }

    // -------------------------------------------------------------------------
    // テスト用ヘルパー: methodName だけを持つダミー Call リストを生成
    // -------------------------------------------------------------------------

    private static java.util.List<JavaMethodInfo.Call> calls(String... names) {
        java.util.List<JavaMethodInfo.Call> list = new java.util.ArrayList<>();
        for (String name : names) {
            list.add(new JavaMethodInfo.Call(null, name));
        }
        return list;
    }
}
