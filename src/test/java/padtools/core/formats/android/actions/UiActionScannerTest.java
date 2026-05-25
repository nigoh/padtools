// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package padtools.core.formats.android.actions;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * UiActionScanner のユニットテスト。
 */
public class UiActionScannerTest {

    private final UiActionScanner scanner = new UiActionScanner();

    @Test
    public void detectsSetOnClickListener() {
        String src = "public class MainActivity {\n"
                + "  void setup() {\n"
                + "    btnLogin.setOnClickListener(v -> login());\n"
                + "  }\n"
                + "}\n";
        List<UiActionEntry> entries = scanner.analyzeSource(src, "MainActivity.java");
        boolean found = false;
        for (UiActionEntry e : entries) {
            if (e.actionType == UiActionEntry.ActionType.ON_CLICK) {
                found = true;
                assertEquals(3, e.line);
            }
        }
        assertTrue("Must detect setOnClickListener", found);
    }

    @Test
    public void detectsSetOnLongClickListener() {
        String src = "view.setOnLongClickListener(v -> true);\n";
        List<UiActionEntry> entries = scanner.analyzeSource(src, "A.java");
        boolean found = entries.stream()
                .anyMatch(e -> e.actionType == UiActionEntry.ActionType.ON_LONG_CLICK);
        assertTrue(found);
    }

    @Test
    public void detectsOnOptionsItemSelected() {
        String src = "public class Frag {\n"
                + "  public boolean onOptionsItemSelected(MenuItem item) {\n"
                + "    return true;\n"
                + "  }\n"
                + "}\n";
        List<UiActionEntry> entries = scanner.analyzeSource(src, "Frag.java");
        boolean found = entries.stream()
                .anyMatch(e -> e.actionType == UiActionEntry.ActionType.MENU_ITEM);
        assertTrue(found);
    }

    @Test
    public void detectsComposeClick() {
        String src = "Button(\n"
                + "  onClick = { viewModel.submit() }\n"
                + ")\n";
        List<UiActionEntry> entries = scanner.analyzeSource(src, "Screen.kt");
        boolean found = entries.stream()
                .anyMatch(e -> e.actionType == UiActionEntry.ActionType.COMPOSE_CLICK);
        assertTrue(found);
    }

    @Test
    public void detectsXmlOnClickAttribute() {
        String xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                + "    <Button\n"
                + "        android:id=\"@+id/btn_save\"\n"
                + "        android:onClick=\"onSaveClicked\" />\n"
                + "</LinearLayout>\n";
        List<UiActionEntry> entries = scanner.analyzeLayoutXml(xml, "activity_main.xml");
        assertEquals(1, entries.size());
        UiActionEntry e = entries.get(0);
        assertEquals(UiActionEntry.ActionType.XML_ON_CLICK, e.actionType);
        assertEquals("onSaveClicked", e.handler);
        assertEquals("btn_save", e.componentId);
    }

    @Test
    public void emptySourceReturnsEmptyList() {
        List<UiActionEntry> entries = scanner.analyzeSource("", "A.java");
        assertTrue(entries.isEmpty());
    }
}
