package padtools.core.formats.uml;

import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertTrue;

public class MethodUsageReportTest {

    @Test
    public void render_listsClassesMethodsAndSignatures() {
        List<JavaClassInfo> classes = JavaStructureExtractor.extract(
                "package x; public class Foo { public int add(int a, int b) { return a + b; } }");
        String md = MethodUsageReport.render(classes, null, Collections.emptyList());
        assertTrue(md, md.contains("x.Foo"));
        assertTrue(md, md.contains("add(a: int, b: int): int"));
        // refIndex 無しなら利用側なし・直接呼び出し
        assertTrue(md, md.contains("利用側"));
        assertTrue(md, md.contains("実行条件"));
    }

    @Test
    public void render_includesClickListenerHandlersAsListeners() {
        List<JavaClassInfo> classes = JavaStructureExtractor.extract(
                "class Screen { void setup() {"
                + " button.setOnClickListener(v -> submit()); } void submit() {} }");
        String md = MethodUsageReport.render(classes, null, Collections.emptyList());
        assertTrue(md, md.contains("[listener]"));
        // setOnClickListener のラムダは SAM 解決で onClick 名になる
        assertTrue(md, md.contains("onClick"));
        assertTrue(md, md.contains("クリック"));
    }
}
