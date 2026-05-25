// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package padtools.app.uml;

import padtools.util.CancelToken;
import padtools.util.ErrorListener;
import padtools.util.ProgressListener;

import javax.swing.JFrame;
import javax.swing.JMenuItem;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.io.File;
import java.util.function.Consumer;

/**
 * プロジェクト解析の SwingWorker ライフサイクルを管理するクラス。
 *
 * <p>解析の開始・進捗更新・完了/失敗後の後処理を担う。
 * UI 状態（スコープ・図種）の変更は {@code onLoadSuccess} コールバック経由で
 * 呼び出し側（UmlMainFrame）が行う。</p>
 */
public final class ProjectLoader {

    private final ProjectAnalysisCache cache;
    private final ReferenceIndexCache refIndexCache;
    private final DiagramState state;
    private final ProjectTreePanel treePanel;
    private final ManifestSummaryPanel manifestSummaryPanel;
    private final JProgressBar loadProgress;
    private final JMenuItem cancelLoadingItem;
    private final JLabel statusLabel;
    private final JFrame parentFrame;
    private final Consumer<CancelToken> cancelTokenSetter;
    private final Consumer<File> projectRootSetter;
    private final Consumer<File> onLoadSuccess;

    public ProjectLoader(ProjectLoaderDeps deps) {
        this.cache = deps.cache;
        this.refIndexCache = deps.refIndexCache;
        this.state = deps.state;
        this.treePanel = deps.treePanel;
        this.manifestSummaryPanel = deps.manifestSummaryPanel;
        this.loadProgress = deps.loadProgress;
        this.cancelLoadingItem = deps.cancelLoadingItem;
        this.statusLabel = deps.statusLabel;
        this.parentFrame = deps.parentFrame;
        this.cancelTokenSetter = deps.cancelTokenSetter;
        this.projectRootSetter = deps.projectRootSetter;
        this.onLoadSuccess = deps.onLoadSuccess;
    }

    /** プロジェクト解析を開始する。EDT から呼ぶこと。 */
    public void start(File root) {
        statusLabel.setText("Analyzing " + root.getName() + " ...");
        treePanel.clear();
        manifestSummaryPanel.setText("");
        loadProgress.setVisible(true);
        loadProgress.setIndeterminate(true);
        loadProgress.setString("Scanning...");
        cancelLoadingItem.setEnabled(true);
        final CancelToken cancel = new CancelToken();
        cancelTokenSetter.accept(cancel);
        final ProgressListener prog = ProgressListener.throttled(
                (done, total, message) -> SwingUtilities.invokeLater(
                        () -> updateLoadProgress(done, total, message)),
                150L);
        new SwingWorker<Void, Void>() {
            private Throwable error;
            private boolean cancelled;

            @Override
            protected Void doInBackground() {
                try {
                    cache.clear();
                    refIndexCache.invalidate();
                    cache.load(root, ErrorListener.silent(), prog, cancel, null);
                    cancelled = cancel.isCancelled();
                } catch (Exception ex) {
                    error = ex;
                }
                return null;
            }

            @Override
            protected void done() {
                cancelTokenSetter.accept(null);
                cancelLoadingItem.setEnabled(false);
                loadProgress.setVisible(false);
                loadProgress.setIndeterminate(false);
                loadProgress.setValue(0);
                loadProgress.setString(null);
                if (error != null) {
                    JOptionPane.showMessageDialog(parentFrame,
                            "Failed to analyze project: " + error.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                    statusLabel.setText(" ");
                    return;
                }
                if (cancelled) {
                    statusLabel.setText("Cancelled.");
                    return;
                }
                projectRootSetter.accept(root);
                treePanel.populate(cache.getAnalysis(), cache.getClasses(),
                        root.getName(), cache.getClassToModule());
                state.sequenceEntry = null;
                state.activityEntry = null;
                state.callGraphEntry = null;
                state.sequenceHiddenParticipants.clear();
                state.currentScope = null;
                StringBuilder st = new StringBuilder();
                st.append("Analyzed ").append(cache.getClasses().size())
                        .append(" class(es) from ").append(root.getAbsolutePath());
                int missing = cache.getDependencyIndex().getMissingArtifacts().size();
                if (missing > 0) {
                    st.append(" — ").append(missing).append(" dependency(ies) not resolved");
                }
                statusLabel.setText(st.toString());
                onLoadSuccess.accept(root);
            }
        }.execute();
    }

    void updateLoadProgress(int done, int total, String message) {
        if (total > 0) {
            if (loadProgress.isIndeterminate()) {
                loadProgress.setIndeterminate(false);
            }
            loadProgress.setMaximum(total);
            loadProgress.setValue(Math.min(done, total));
            loadProgress.setString(done + "/" + total);
            statusLabel.setText("Analyzing " + done + "/" + total
                    + (message != null && !message.isEmpty() ? " — " + message : ""));
        } else {
            loadProgress.setIndeterminate(true);
            loadProgress.setString(message != null ? message : "Scanning...");
            if (message != null) {
                statusLabel.setText(message);
            }
        }
    }
}
