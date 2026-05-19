package padtools.core.screen;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Intent ベース画面遷移検出のテスト。
 */
public class IntentNavigationDetectorTest {

    @Test
    public void detectsStartActivityWithNewIntent() {
        String src = "package com.x;\n"
                + "public class MainActivity {\n"
                + "  void onClick() {\n"
                + "    startActivity(new Intent(this, DetailActivity.class));\n"
                + "  }\n"
                + "}\n";
        List<ScreenTransition> hits = new IntentNavigationDetector()
                .analyzeSource(src, "MainActivity.java");
        assertEquals(1, hits.size());
        ScreenTransition t = hits.get(0);
        assertEquals("com.x.MainActivity", t.getFromFqn());
        assertEquals("DetailActivity", t.getTargetSimpleName());
        assertEquals(ScreenTransition.Kind.START_ACTIVITY, t.getKind());
        assertEquals("onClick", t.getFromMethod());
    }

    @Test
    public void detectsSetClassPattern() {
        String src = "package com.x;\n"
                + "public class A {\n"
                + "  void go() {\n"
                + "    Intent i = new Intent();\n"
                + "    i.setClass(this, B.class);\n"
                + "    startActivity(i);\n"
                + "  }\n"
                + "}\n";
        List<ScreenTransition> hits = new IntentNavigationDetector()
                .analyzeSource(src, "A.java");
        boolean foundSetClass = false;
        for (ScreenTransition t : hits) {
            if (t.getKind() == ScreenTransition.Kind.SET_CLASS
                    && "B".equals(t.getTargetSimpleName())) {
                foundSetClass = true;
            }
        }
        assertTrue("Must detect setClass transition", foundSetClass);
    }

    @Test
    public void detectsSetClassNameStringForm() {
        String src = "package com.x;\n"
                + "public class A {\n"
                + "  void go() {\n"
                + "    Intent i = new Intent();\n"
                + "    i.setClassName(this, \"com.x.B\");\n"
                + "  }\n"
                + "}\n";
        List<ScreenTransition> hits = new IntentNavigationDetector()
                .analyzeSource(src, "A.java");
        assertEquals(1, hits.size());
        ScreenTransition t = hits.get(0);
        assertEquals("com.x.B", t.getTargetClassName());
        assertEquals("B", t.getTargetSimpleName());
        assertEquals(ScreenTransition.Kind.SET_CLASS, t.getKind());
    }

    @Test
    public void promotesToStartForResultWhenAdjacent() {
        String src = "package com.x;\n"
                + "public class A {\n"
                + "  void pick() {\n"
                + "    Intent intent = new Intent(this, PickerActivity.class);\n"
                + "    startActivityForResult(intent, 42);\n"
                + "  }\n"
                + "}\n";
        List<ScreenTransition> hits = new IntentNavigationDetector()
                .analyzeSource(src, "A.java");
        assertEquals(1, hits.size());
        assertEquals(ScreenTransition.Kind.START_FOR_RESULT, hits.get(0).getKind());
    }

    @Test
    public void plantUmlOutputContainsNodes() {
        String src = "package com.x;\n"
                + "public class MainActivity {\n"
                + "  void onClick() {\n"
                + "    startActivity(new Intent(this, DetailActivity.class));\n"
                + "  }\n"
                + "}\n";
        List<ScreenTransition> hits = new IntentNavigationDetector()
                .analyzeSource(src, "MainActivity.java");
        String puml = PlantUmlScreenFlowDiagram.render(hits);
        assertTrue(puml.startsWith("@startuml"));
        assertTrue(puml.contains("@enduml"));
        assertTrue(puml.contains("MainActivity"));
        assertTrue(puml.contains("DetailActivity"));
        assertTrue(puml.contains("START_ACTIVITY"));
    }

    @Test
    public void markdownOutputListsActivities() {
        String src = "package com.x;\n"
                + "public class MainActivity {\n"
                + "  void onClick() {\n"
                + "    startActivity(new Intent(this, DetailActivity.class));\n"
                + "    startActivity(new Intent(this, SettingsActivity.class));\n"
                + "  }\n"
                + "}\n";
        List<ScreenTransition> hits = new IntentNavigationDetector()
                .analyzeSource(src, "MainActivity.java");
        String md = MarkdownScreenFlowReport.render(hits);
        assertTrue(md.contains("Screen Flow Report"));
        assertTrue(md.contains("MainActivity"));
        assertTrue(md.contains("DetailActivity"));
        assertTrue(md.contains("SettingsActivity"));
        assertTrue(md.contains("Outgoing"));
    }

    @Test
    public void emptyReportRendersPlaceholder() {
        String md = MarkdownScreenFlowReport.render(new java.util.ArrayList<>());
        assertNotNull(md);
        assertTrue(md.contains("no Intent-based"));
    }

    @Test
    public void multipleTransitionsAggregatedInDiagram() {
        String src = "package com.x;\n"
                + "public class A {\n"
                + "  void go1() { startActivity(new Intent(this, B.class)); }\n"
                + "  void go2() { startActivity(new Intent(this, B.class)); }\n"
                + "}\n";
        List<ScreenTransition> hits = new IntentNavigationDetector()
                .analyzeSource(src, "A.java");
        assertEquals(2, hits.size());
        String puml = PlantUmlScreenFlowDiagram.render(hits);
        // x2 で集約されているはず
        assertTrue(puml.contains("x2"));
    }
}
