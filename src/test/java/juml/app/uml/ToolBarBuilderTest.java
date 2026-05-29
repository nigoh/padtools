// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.junit.Test;

import javax.swing.JToggleButton;

import static org.junit.Assert.*;

public class ToolBarBuilderTest {

    private ToolBarBuilder.Result buildDefault() {
        ToolBarBuilder.Callbacks cb = new ToolBarBuilder.Callbacks();
        cb.chooseProject = () -> {};
        cb.chooseAndExport = () -> {};
        cb.refreshDiagram = () -> {};
        cb.openEntitySearch = () -> {};
        cb.selectDiagramKind = k -> {};
        return new ToolBarBuilder(DiagramKind.CLASS, cb).build();
    }

    @Test
    public void build_createsToggleForEveryDiagramKind() {
        ToolBarBuilder.Result r = buildDefault();
        for (DiagramKind k : DiagramKind.values()) {
            assertNotNull("Missing toggle for " + k, r.diagramToggles.get(k));
        }
        assertEquals(DiagramKind.values().length, r.diagramToggles.size());
    }

    @Test
    public void build_initialKindIsSelected() {
        ToolBarBuilder.Result r = buildDefault();
        JToggleButton classBtn = r.diagramToggles.get(DiagramKind.CLASS);
        assertTrue("CLASS button should be selected initially", classBtn.isSelected());
    }

    @Test
    public void build_nonInitialKindIsNotSelected() {
        ToolBarBuilder.Result r = buildDefault();
        JToggleButton seqBtn = r.diagramToggles.get(DiagramKind.SEQUENCE);
        assertFalse("SEQUENCE button should not be selected initially", seqBtn.isSelected());
    }

    @Test
    public void build_toolBarPanelIsNotNull() {
        ToolBarBuilder.Result r = buildDefault();
        assertNotNull(r.toolBarPanel);
    }

    @Test
    public void diagramsMethod_containsSequenceActivityCallgraph() {
        assertTrue(ToolBarBuilder.DIAGRAMS_METHOD.contains(DiagramKind.SEQUENCE));
        assertTrue(ToolBarBuilder.DIAGRAMS_METHOD.contains(DiagramKind.ACTIVITY));
        assertTrue(ToolBarBuilder.DIAGRAMS_METHOD.contains(DiagramKind.CALLGRAPH));
        assertFalse(ToolBarBuilder.DIAGRAMS_METHOD.contains(DiagramKind.CLASS));
    }

    /**
     * すべての図種で短く一貫したトグルラベルが付くこと。
     * 以前は NAVIGATION / MODULE だけ {@code switch} の case 漏れで
     * {@code getDisplayName()} の長いラベル ("Navigation Graph" / "Module Diagram")
     * になり、他のボタン ("Class" 等) と不揃いだった。
     */
    @Test
    public void toolbarLabel_isNonEmptyForEveryKind() {
        for (DiagramKind k : DiagramKind.values()) {
            String label = ToolBarBuilder.toolbarLabel(k);
            assertNotNull("label for " + k, label);
            assertFalse(k + " label should not be empty", label.isEmpty());
        }
    }

    /**
     * NAVIGATION / MODULE は以前 {@code switch} の case 漏れで
     * {@code getDisplayName()} の長いラベル ("Navigation Graph" / "Module Diagram")
     * になり、他のボタン ("Class" 等) と不揃いだった。短いラベルを付ける。
     */
    @Test
    public void toolbarLabel_navigationAndModuleAreShort() {
        assertEquals("Navigation", ToolBarBuilder.toolbarLabel(DiagramKind.NAVIGATION));
        assertEquals("Module", ToolBarBuilder.toolbarLabel(DiagramKind.MODULE));
    }
}
