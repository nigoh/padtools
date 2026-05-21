package padtools.app.uml;

import org.junit.Before;
import org.junit.Test;
import padtools.core.formats.uml.JavaClassInfo;
import padtools.core.formats.uml.JavaMethodInfo;

import javax.swing.JLabel;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JTabbedPane;
import javax.swing.JToggleButton;
import java.util.EnumMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class DiagramControllerTest {

    private DiagramState state;
    private ProjectAnalysisCache cache;
    private EnumMap<DiagramKind, JRadioButtonMenuItem> diagramItems;
    private EnumMap<DiagramKind, JToggleButton> diagramToggles;
    private AtomicInteger refreshCount;
    private AtomicReference<DiagramKind> lastKind;
    private DiagramController controller;

    @Before
    public void setUp() {
        state = new DiagramState();
        cache = new ProjectAnalysisCache();
        diagramItems = new EnumMap<>(DiagramKind.class);
        diagramToggles = new EnumMap<>(DiagramKind.class);
        for (DiagramKind k : DiagramKind.values()) {
            diagramItems.put(k, new JRadioButtonMenuItem(k.name()));
            diagramToggles.put(k, new JToggleButton(k.name()));
        }
        refreshCount = new AtomicInteger(0);
        lastKind = new AtomicReference<>(DiagramKind.CLASS);
        controller = new DiagramController(
                state, () -> cache, diagramItems, diagramToggles,
                new ProjectTreePanel(), new JTabbedPane(), null,
                new JLabel(), null,
                () -> refreshCount.incrementAndGet(),
                kind -> lastKind.set(kind));
    }

    @Test
    public void onTreeMethodSelected_setsSequenceEntryAndKind() {
        JavaClassInfo cls = new JavaClassInfo();
        cls.setSimpleName("Foo");
        JavaMethodInfo method = new JavaMethodInfo();
        method.setName("bar");
        controller.onTreeMethodSelected(new ProjectTreePanel.MethodSelection(cls, method));
        assertEquals("Foo.bar", state.sequenceEntry);
        assertEquals(DiagramKind.SEQUENCE, controller.currentKind);
        assertEquals(DiagramKind.SEQUENCE, lastKind.get());
    }

    @Test
    public void onTreeMethodSelected_triggersRefresh() {
        JavaClassInfo cls = new JavaClassInfo();
        cls.setSimpleName("Foo");
        JavaMethodInfo method = new JavaMethodInfo();
        method.setName("bar");
        controller.onTreeMethodSelected(new ProjectTreePanel.MethodSelection(cls, method));
        assertEquals(1, refreshCount.get());
    }

    @Test
    public void onTreeActivityMethodSelected_setsActivityEntryAndKind() {
        JavaClassInfo cls = new JavaClassInfo();
        cls.setSimpleName("Foo");
        JavaMethodInfo method = new JavaMethodInfo();
        method.setName("bar");
        controller.onTreeActivityMethodSelected(new ProjectTreePanel.MethodSelection(cls, method));
        assertEquals("Foo.bar", state.activityEntry);
        assertEquals(DiagramKind.ACTIVITY, controller.currentKind);
    }

    @Test
    public void setAllMethodEntries_setsAllThree() {
        controller.setAllMethodEntries("Bar.baz");
        assertEquals("Bar.baz", state.sequenceEntry);
        assertEquals("Bar.baz", state.activityEntry);
        assertEquals("Bar.baz", state.callGraphEntry);
    }


    @Test
    public void onTreeMethodSelected_null_isNoOp() {
        DiagramKind before = controller.currentKind;
        controller.onTreeMethodSelected(null);
        assertEquals(before, controller.currentKind);
        assertEquals(0, refreshCount.get());
    }

    @Test
    public void buildSequenceRequest_requiresDotSeparatedEntry() {
        DiagramRequest req = controller.buildSequenceRequest("Foo.bar");
        assertNotNull(req);
        assertEquals(DiagramKind.SEQUENCE, req.getKind());
    }

    @Test(expected = IllegalArgumentException.class)
    public void buildSequenceRequest_missingDot_throws() {
        controller.buildSequenceRequest("Foobar");
    }

    @Test
    public void buildActivityRequest_requiresDotSeparatedEntry() {
        DiagramRequest req = controller.buildActivityRequest("Foo.bar");
        assertNotNull(req);
        assertEquals(DiagramKind.ACTIVITY, req.getKind());
    }

    @Test
    public void buildCallGraphRequest_requiresDotSeparatedEntry() {
        DiagramRequest req = controller.buildCallGraphRequest("Foo.bar");
        assertNotNull(req);
        assertEquals(DiagramKind.CALLGRAPH, req.getKind());
    }

    @Test
    public void syncDiagramToggle_selectsToggleButton() {
        diagramToggles.get(DiagramKind.SEQUENCE).setSelected(false);
        controller.syncDiagramToggle(DiagramKind.SEQUENCE);
        assertTrue(diagramToggles.get(DiagramKind.SEQUENCE).isSelected());
    }

    @Test
    public void updateAvailableDiagrams_hidesNonAllowedKinds() {
        controller.updateAvailableDiagrams(ToolBarBuilder.DIAGRAMS_METHOD);
        assertFalse(diagramToggles.get(DiagramKind.CLASS).isVisible());
        assertTrue(diagramToggles.get(DiagramKind.SEQUENCE).isVisible());
        assertTrue(diagramToggles.get(DiagramKind.ACTIVITY).isVisible());
    }
}
