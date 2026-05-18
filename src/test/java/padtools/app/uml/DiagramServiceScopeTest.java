package padtools.app.uml;

import org.junit.Test;
import padtools.core.formats.uml.JavaClassInfo;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * DiagramService に DiagramScope を適用したときに生成 PlantUML がスコープを尊重するテスト。
 */
public class DiagramServiceScopeTest {

    private static JavaClassInfo cls(String pkg, String name) {
        JavaClassInfo c = new JavaClassInfo();
        c.setPackageName(pkg);
        c.setSimpleName(name);
        c.setKind(JavaClassInfo.Kind.CLASS);
        return c;
    }

    private List<JavaClassInfo> sample() {
        List<JavaClassInfo> l = new ArrayList<>();
        l.add(cls("com.car", "CarManager"));
        l.add(cls("com.car", "CarHelper"));
        l.add(cls("com.other", "OtherClass"));
        return l;
    }

    @Test
    public void testScopeFiltersClassDiagram() {
        DiagramScope scope = DiagramScope.builder()
                .includePackage("com.car").build();
        DiagramRequest req = new DiagramRequest(DiagramKind.CLASS, null, null, false, scope);
        String puml = DiagramService.generatePuml(req, null, sample(), null);
        assertTrue("@startuml で始まる", puml.startsWith("@startuml"));
        assertTrue("CarManager は含まれる", puml.contains("CarManager"));
        assertTrue("CarHelper は含まれる", puml.contains("CarHelper"));
        assertFalse("OtherClass はスコープ外", puml.contains("OtherClass"));
    }

    @Test
    public void testScopeRegexLimits() {
        DiagramScope scope = DiagramScope.builder()
                .classNameRegex(".*Manager$").build();
        DiagramRequest req = new DiagramRequest(DiagramKind.CLASS, null, null, false, scope);
        String puml = DiagramService.generatePuml(req, null, sample(), null);
        assertTrue(puml.contains("CarManager"));
        assertFalse(puml.contains("CarHelper"));
        assertFalse(puml.contains("OtherClass"));
    }

    @Test
    public void testFooterWarningWhenScopeShrinks() {
        DiagramScope scope = DiagramScope.builder()
                .includePackage("com.car").build();
        DiagramRequest req = new DiagramRequest(DiagramKind.CLASS, null, null, false, scope);
        String puml = DiagramService.generatePuml(req, null, sample(), null);
        assertTrue("スコープで件数が減ったら footer に表示", puml.contains("footer "));
        assertTrue(puml.contains("scope filter"));
    }

    @Test
    public void testMaxClassesAddsTruncationFooter() {
        DiagramScope scope = DiagramScope.builder().maxClasses(2).build();
        DiagramRequest req = new DiagramRequest(DiagramKind.CLASS, null, null, false, scope);
        String puml = DiagramService.generatePuml(req, null, sample(), null);
        // maxClasses 2 < 3 件なので切り詰め + warning
        assertTrue(puml.contains("showing 2 of 3"));
    }

    @Test
    public void testNoScopeAllowsAll() {
        DiagramRequest req = new DiagramRequest(DiagramKind.CLASS, null, null, false);
        String puml = DiagramService.generatePuml(req, null, sample(), null);
        assertTrue(puml.contains("CarManager"));
        assertTrue(puml.contains("CarHelper"));
        assertTrue(puml.contains("OtherClass"));
        assertFalse("scope 警告なし", puml.contains("scope filter"));
    }
}
