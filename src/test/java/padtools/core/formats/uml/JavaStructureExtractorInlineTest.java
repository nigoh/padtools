package padtools.core.formats.uml;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link JavaStructureExtractor} のフィールド初期化子 (匿名クラス / ラムダ) 抽出機能の
 * ユニットテスト。
 *
 * <p>{@link JavaFieldInfo#getInlineMethods()} に正しくメソッド本体が取り込まれ、
 * シーケンス図がフィールド経由のリスナー呼び出しを展開できることを担保する。</p>
 */
public class JavaStructureExtractorInlineTest {

    @Test
    public void testAnonymousClassListenerFieldCapturesBody() {
        String src = ""
                + "package com.x;\n"
                + "class Foo {\n"
                + "  private OnClickListener listener = new OnClickListener() {\n"
                + "    public void onClick(View v) { mService.start(); log.d(); }\n"
                + "  };\n"
                + "  private Object mService;\n"
                + "  private Object log;\n"
                + "}";
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(src);
        assertEquals(1, cs.size());
        JavaClassInfo c = cs.get(0);
        // 匿名クラスの内側を別 ClassInfo として results に混入させないこと
        JavaFieldInfo listenerField = findField(c, "listener");
        assertNotNull("listener field should be captured", listenerField);
        assertEquals(1, listenerField.getInlineMethods().size());
        JavaMethodInfo onClick = listenerField.getInlineMethods().get(0);
        assertEquals("onClick", onClick.getName());
        // 本体内に 2 つの呼びだしがあること (mService.start, log.d)
        List<JavaMethodInfo.Call> calls = onClick.getCalls();
        assertEquals(2, calls.size());
        assertEquals("start", calls.get(0).getMethodName());
        assertEquals("mService", calls.get(0).getReceiver());
        assertEquals("d", calls.get(1).getMethodName());
    }

    @Test
    public void testLambdaRunnableResolvesToRun() {
        String src = ""
                + "class A {\n"
                + "  Runnable r = () -> { doX(); };\n"
                + "}";
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(src);
        JavaFieldInfo r = findField(cs.get(0), "r");
        assertNotNull(r);
        assertEquals(1, r.getInlineMethods().size());
        assertEquals("run", r.getInlineMethods().get(0).getName());
        assertEquals("doX", r.getInlineMethods().get(0).getCalls().get(0).getMethodName());
    }

    @Test
    public void testLambdaOnClickListenerResolvesToOnClick() {
        // フィールド型は `View.OnClickListener` のような dot 形式でも解決できること
        String src = ""
                + "class A {\n"
                + "  View.OnClickListener l = v -> log.d(\"x\");\n"
                + "}";
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(src);
        JavaFieldInfo l = findField(cs.get(0), "l");
        assertNotNull(l);
        assertEquals(1, l.getInlineMethods().size());
        assertEquals("onClick", l.getInlineMethods().get(0).getName());
        assertEquals("d", l.getInlineMethods().get(0).getCalls().get(0).getMethodName());
    }

    @Test
    public void testLambdaExpressionBodyDoesNotEatFieldTerminator() {
        // expression-bodied lambda の後に別フィールドが続くケースで、後続フィールドが
        // 正しくパースされること (`;` の読み損ねが起きないこと)。
        String src = ""
                + "class A {\n"
                + "  Runnable r = () -> doX();\n"
                + "  int next;\n"
                + "}";
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(src);
        JavaClassInfo c = cs.get(0);
        // フィールドが 2 つともパースされていること
        assertEquals(2, c.getFields().size());
        JavaFieldInfo r = findField(c, "r");
        JavaFieldInfo next = findField(c, "next");
        assertNotNull(r);
        assertNotNull(next);
        assertEquals("int", next.getType());
    }

    @Test
    public void testLambdaBlockBodyDoesNotLeakStatements() {
        // 通常のフィールド (int x = 1;) は inlineMethods を持たないこと
        String src = ""
                + "class A {\n"
                + "  int x = 1;\n"
                + "  String s = \"hello\";\n"
                + "}";
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(src);
        JavaClassInfo c = cs.get(0);
        for (JavaFieldInfo f : c.getFields()) {
            assertTrue("plain field should not have inlineMethods: " + f.getName(),
                    f.getInlineMethods().isEmpty());
        }
    }

    @Test
    public void testUnknownSamFallbackToInlineMarker() {
        // 既知 SAM マップにない型のラムダは `<inline>` で fallback すること
        String src = ""
                + "class A {\n"
                + "  MyCustomFunctional fn = () -> compute();\n"
                + "}";
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(src);
        JavaFieldInfo fn = findField(cs.get(0), "fn");
        assertNotNull(fn);
        assertEquals(1, fn.getInlineMethods().size());
        assertEquals("<inline>", fn.getInlineMethods().get(0).getName());
    }

    @Test
    public void testAnonymousClassWithMultipleMethods() {
        String src = ""
                + "class A {\n"
                + "  Adapter ad = new Adapter() {\n"
                + "    public void onCreate() { setupA(); }\n"
                + "    public void onDestroy() { teardownB(); }\n"
                + "  };\n"
                + "}";
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(src);
        JavaFieldInfo ad = findField(cs.get(0), "ad");
        assertNotNull(ad);
        assertEquals(2, ad.getInlineMethods().size());
        assertEquals("onCreate", ad.getInlineMethods().get(0).getName());
        assertEquals("onDestroy", ad.getInlineMethods().get(1).getName());
    }

    @Test
    public void testPlainArrayInitializerDoesNotMisfire() {
        // 配列初期化子は inline 化しないこと
        String src = "class A { int[] xs = new int[]{1, 2, 3}; }";
        List<JavaClassInfo> cs = JavaStructureExtractor.extract(src);
        JavaFieldInfo xs = findField(cs.get(0), "xs");
        assertNotNull(xs);
        assertTrue(xs.getInlineMethods().isEmpty());
        assertFalse(cs.get(0).getFields().isEmpty());
    }

    private static JavaFieldInfo findField(JavaClassInfo c, String name) {
        for (JavaFieldInfo f : c.getFields()) {
            if (name.equals(f.getName())) {
                return f;
            }
        }
        return null;
    }
}
