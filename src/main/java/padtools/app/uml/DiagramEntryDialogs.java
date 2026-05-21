package padtools.app.uml;

import javax.swing.JOptionPane;
import javax.swing.JRadioButtonMenuItem;

/**
 * 図のエントリ (シーケンス/アクティビティ/コールグラフ起点・レイアウト・ナビゲーション・
 * participant フィルタ) をダイアログで選択し、{@link DiagramController} の状態へ反映する補助クラス。
 */
final class DiagramEntryDialogs {

    private final DiagramController c;

    DiagramEntryDialogs(DiagramController c) {
        this.c = c;
    }

    public void pickSequenceEntry() {
        if (!c.cache().isLoaded()) {
            JOptionPane.showMessageDialog(c.parentFrame,
                    "Open a project first.",
                    "No project", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        SequenceEntryDialog dlg = new SequenceEntryDialog(c.parentFrame, c.cache().getClasses());
        if (dlg.getCandidateCount() == 0) {
            JOptionPane.showMessageDialog(c.parentFrame,
                    "No methods found in this project.",
                    "Sequence diagram", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        dlg.setVisible(true);
        String picked = dlg.getSelectedEntry();
        if (picked != null) {
            // 起点が変わったら participant フィルタはリセットする
            // (旧起点の participant 名は新図に存在しない可能性があるため)
            c.state.sequenceHiddenParticipants.clear();
            c.state.sequenceEntry = picked;
            c.setCurrentKind(DiagramKind.SEQUENCE);
            JRadioButtonMenuItem item = c.diagramItems.get(DiagramKind.SEQUENCE);
            if (item != null) {
                item.setSelected(true);
            }
            c.syncTreeToMethodByEntry(picked);
            c.refreshDiagram.run();
        }
    }

    /**
     * 現在のシーケンス図起点に登場する participant をフィルタダイアログで選択できるようにする。
     * 選択結果は {@code state.sequenceHiddenParticipants} に保存され、再描画時の
     * {@link DiagramRequest#getSequenceHiddenParticipants()} に渡される。
     */
    public void openParticipantFilterDialog() {
        if (!c.cache().isLoaded()) {
            JOptionPane.showMessageDialog(c.parentFrame,
                    "Open a project first.",
                    "No project", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        if (c.state.sequenceEntry == null || c.state.sequenceEntry.isEmpty()) {
            JOptionPane.showMessageDialog(c.parentFrame,
                    "Choose a sequence entry first (Diagram → Choose Sequence Entry...).",
                    "Sequence participants", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        int dot = c.state.sequenceEntry.lastIndexOf('.');
        if (dot < 0) {
            return;
        }
        String cls = c.state.sequenceEntry.substring(0, dot);
        String method = c.state.sequenceEntry.substring(dot + 1);
        java.util.Set<String> all =
                padtools.core.formats.uml.PlantUmlSequenceDiagram.collectParticipants(
                        c.cache().getClasses(), cls, method, null);
        if (all.isEmpty()) {
            JOptionPane.showMessageDialog(c.parentFrame,
                    "No participants found for " + c.state.sequenceEntry,
                    "Sequence participants", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        java.util.Set<String> picked = SequenceParticipantFilterDialog.show(
                c.parentFrame, c.state.sequenceEntry, all, c.state.sequenceHiddenParticipants);
        if (picked != null) {
            c.state.sequenceHiddenParticipants.clear();
            c.state.sequenceHiddenParticipants.addAll(picked);
            int total = all.size();
            int hidden = c.state.sequenceHiddenParticipants.size();
            c.statusLabel.setText("Sequence filter: showing " + (total - hidden) + "/" + total
                    + " participants");
            c.refreshDiagram.run();
        }
    }

    /**
     * アクティビティ図用にメソッドを選択する。SequenceEntryDialog を流用し、
     * タイトルだけ「Select activity method」に差し替える。
     */
    public void pickActivityEntry() {
        if (!c.cache().isLoaded()) {
            JOptionPane.showMessageDialog(c.parentFrame,
                    "Open a project first.",
                    "No project", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        SequenceEntryDialog dlg = new SequenceEntryDialog(c.parentFrame, c.cache().getClasses());
        dlg.setTitle("Select activity method");
        if (dlg.getCandidateCount() == 0) {
            JOptionPane.showMessageDialog(c.parentFrame,
                    "No methods found in this project.",
                    "Activity diagram", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        dlg.setVisible(true);
        String picked = dlg.getSelectedEntry();
        if (picked != null) {
            c.state.activityEntry = picked;
            c.setCurrentKind(DiagramKind.ACTIVITY);
            JRadioButtonMenuItem item = c.diagramItems.get(DiagramKind.ACTIVITY);
            if (item != null) {
                item.setSelected(true);
            }
            c.syncTreeToMethodByEntry(picked);
            c.refreshDiagram.run();
        }
    }

    public void pickCallGraphEntry() {
        if (!c.cache().isLoaded()) {
            JOptionPane.showMessageDialog(c.parentFrame,
                    "Open a project first.",
                    "No project", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        SequenceEntryDialog dlg = new SequenceEntryDialog(c.parentFrame, c.cache().getClasses());
        dlg.setTitle("Select call graph entry method");
        if (dlg.getCandidateCount() == 0) {
            JOptionPane.showMessageDialog(c.parentFrame,
                    "No methods found in this project.",
                    "Call graph", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        dlg.setVisible(true);
        String picked = dlg.getSelectedEntry();
        if (picked != null) {
            c.state.callGraphEntry = picked;
            c.setCurrentKind(DiagramKind.CALLGRAPH);
            JRadioButtonMenuItem item = c.diagramItems.get(DiagramKind.CALLGRAPH);
            if (item != null) {
                item.setSelected(true);
            }
            c.refreshDiagram.run();
        }
    }

    public void pickLayoutFile() {
        String picked = LayoutFileChooserDialog.chooseLayoutKey(c.parentFrame, c.cache());
        if (picked == null) {
            return;
        }
        c.state.currentLayoutKey = picked;
        c.setCurrentKind(DiagramKind.LAYOUT);
        JRadioButtonMenuItem item = c.diagramItems.get(DiagramKind.LAYOUT);
        if (item != null) {
            item.setSelected(true);
        }
        c.refreshDiagram.run();
    }

    public void pickNavigationGraph() {
        String picked = NavigationFileChooserDialog.chooseNavigationKey(c.parentFrame, c.cache());
        if (picked == null) {
            return;
        }
        c.state.currentNavigationKey = picked;
        c.setCurrentKind(DiagramKind.NAVIGATION);
        JRadioButtonMenuItem item = c.diagramItems.get(DiagramKind.NAVIGATION);
        if (item != null) {
            item.setSelected(true);
        }
        c.refreshDiagram.run();
    }
}
