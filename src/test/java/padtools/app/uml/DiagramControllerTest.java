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
        DiagramControllerDeps deps = new DiagramControllerDeps();
        deps.state = state;
        deps.cacheSupplier = () -> cache;
        deps.diagramItems = diagramItems;
        deps.diagramToggles = diagramToggles;
        deps.treePanel = new ProjectTreePanel();
        deps.mainTabs = new JTabbedPane();
        deps.tabPane = null;
        deps.statusLabel = new JLabel();
        deps.parentFrame = null;
        deps.refreshDiagram = () -> refreshCount.incrementAndGet();
        deps.onKindChanged = kind -> lastKind.set(kind);
        controller = new DiagramController(deps);
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

    @Test
    public void entryMissingFor_trueWhenUnsetFalseWhenSet() {
        assertTrue(controller.entryMissingFor(DiagramKind.SEQUENCE));
        assertTrue(controller.entryMissingFor(DiagramKind.ACTIVITY));
        assertTrue(controller.entryMissingFor(DiagramKind.CALLGRAPH));
        assertTrue(controller.entryMissingFor(DiagramKind.LAYOUT));
        assertTrue(controller.entryMissingFor(DiagramKind.NAVIGATION));
        // 起点不要の図種は常に false
        assertFalse(controller.entryMissingFor(DiagramKind.CLASS));
        assertFalse(controller.entryMissingFor(DiagramKind.PACKAGE));
        assertFalse(controller.entryMissingFor(DiagramKind.MANIFEST));

        state.sequenceEntry = "Foo.bar";
        state.activityEntry = "Foo.bar";
        state.callGraphEntry = "Foo.bar";
        state.currentLayoutKey = "m::main::::a.xml";
        state.currentNavigationKey = "m::main::nav.xml";
        assertFalse(controller.entryMissingFor(DiagramKind.SEQUENCE));
        assertFalse(controller.entryMissingFor(DiagramKind.ACTIVITY));
        assertFalse(controller.entryMissingFor(DiagramKind.CALLGRAPH));
        assertFalse(controller.entryMissingFor(DiagramKind.LAYOUT));
        assertFalse(controller.entryMissingFor(DiagramKind.NAVIGATION));
    }

    @Test
    public void revertKindSelection_restoresPreviousKindMenuAndToggle() {
        // SEQUENCE に切り替わった状態を作る (トグル/メニューも SEQUENCE 選択)
        controller.setCurrentKind(DiagramKind.SEQUENCE);
        diagramItems.get(DiagramKind.SEQUENCE).setSelected(true);
        diagramToggles.get(DiagramKind.SEQUENCE).setSelected(true);

        controller.revertKindSelection(DiagramKind.CLASS);

        assertEquals(DiagramKind.CLASS, controller.currentKind);
        assertEquals(DiagramKind.CLASS, lastKind.get());
        assertTrue(diagramItems.get(DiagramKind.CLASS).isSelected());
        assertTrue(diagramToggles.get(DiagramKind.CLASS).isSelected());
    }

    @Test
    public void selectDiagramKind_nonEntryKind_commitsAndRefreshes() {
        controller.selectDiagramKind(DiagramKind.PACKAGE);
        assertEquals(DiagramKind.PACKAGE, controller.currentKind);
        assertTrue(diagramItems.get(DiagramKind.PACKAGE).isSelected());
        assertEquals(1, refreshCount.get());
    }

    @Test
    public void selectDiagramKind_entryKindWithEntryPreset_commitsWithoutDialog() {
        state.sequenceEntry = "Foo.bar";
        controller.selectDiagramKind(DiagramKind.SEQUENCE);
        assertEquals(DiagramKind.SEQUENCE, controller.currentKind);
        assertEquals(1, refreshCount.get());
    }

    @Test
    public void selectDiagramKind_entryKindMissingEntryUnloadedCache_refreshesWithoutDialog() {
        // cache 未ロードなのでダイアログは開かず refresh のみ (既存挙動を維持)
        controller.selectDiagramKind(DiagramKind.SEQUENCE);
        assertEquals(DiagramKind.SEQUENCE, controller.currentKind);
        assertEquals(1, refreshCount.get());
    }
}
