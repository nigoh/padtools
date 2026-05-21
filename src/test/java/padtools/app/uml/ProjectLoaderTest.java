package padtools.app.uml;

import org.junit.Before;
import org.junit.Test;

import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JProgressBar;

import static org.junit.Assert.*;

public class ProjectLoaderTest {

    private JProgressBar progressBar;
    private JLabel statusLabel;
    private ProjectLoader loader;

    @Before
    public void setUp() {
        progressBar = new JProgressBar();
        statusLabel = new JLabel();
        loader = new ProjectLoader(
                new ProjectAnalysisCache(),
                new ReferenceIndexCache(new ProjectAnalysisCache()),
                new DiagramState(),
                new ProjectTreePanel(),
                new ManifestSummaryPanel(),
                progressBar,
                new JMenuItem(),
                statusLabel,
                null,
                token -> { },
                root -> { },
                root -> { });
    }

    @Test
    public void updateLoadProgress_withTotal_setsDeterminate() {
        progressBar.setIndeterminate(true);
        loader.updateLoadProgress(3, 10, "parsing");
        assertFalse(progressBar.isIndeterminate());
        assertEquals(10, progressBar.getMaximum());
        assertEquals(3, progressBar.getValue());
    }

    @Test
    public void updateLoadProgress_withZeroTotal_setsIndeterminate() {
        progressBar.setIndeterminate(false);
        loader.updateLoadProgress(0, 0, "Scanning...");
        assertTrue(progressBar.isIndeterminate());
    }

    @Test
    public void updateLoadProgress_withMessage_updatesStatus() {
        loader.updateLoadProgress(5, 20, "MyClass.java");
        assertTrue(statusLabel.getText().contains("MyClass.java"));
    }

    @Test
    public void updateLoadProgress_doneExceedsTotal_clampsToTotal() {
        loader.updateLoadProgress(25, 10, null);
        assertEquals(10, progressBar.getValue());
    }
}
