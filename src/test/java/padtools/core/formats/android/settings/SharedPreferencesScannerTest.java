// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package padtools.core.formats.android.settings;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * SharedPreferencesScanner のユニットテスト。
 */
public class SharedPreferencesScannerTest {

    private final SharedPreferencesScanner scanner = new SharedPreferencesScanner();

    @Test
    public void detectsGetStringRead() {
        String src = "prefs.getString(\"user_name\", \"\");\n";
        List<SharedPreferencesEntry> entries = scanner.analyzeSource(src, "Test.java");
        assertEquals(1, entries.size());
        SharedPreferencesEntry e = entries.get(0);
        assertEquals("user_name", e.key);
        assertEquals("String", e.type);
        assertFalse(e.isWrite);
        assertEquals(1, e.line);
    }

    @Test
    public void detectsPutBooleanWrite() {
        String src = "editor.putBoolean(\"is_dark_mode\", true);\n";
        List<SharedPreferencesEntry> entries = scanner.analyzeSource(src, "Test.java");
        assertEquals(1, entries.size());
        SharedPreferencesEntry e = entries.get(0);
        assertEquals("is_dark_mode", e.key);
        assertEquals("Boolean", e.type);
        assertTrue(e.isWrite);
    }

    @Test
    public void detectsStoreName() {
        String src = "SharedPreferences prefs = getSharedPreferences(\"user_prefs\", MODE_PRIVATE);\n"
                + "prefs.getString(\"name\", \"\");\n";
        List<SharedPreferencesEntry> entries = scanner.analyzeSource(src, "Test.java");
        assertEquals(1, entries.size());
        assertEquals("user_prefs", entries.get(0).storeName);
    }

    @Test
    public void detectsDefaultStoreViaGetDefaultSharedPreferences() {
        String src = "SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);\n"
                + "prefs.getBoolean(\"notifications\", true);\n";
        List<SharedPreferencesEntry> entries = scanner.analyzeSource(src, "Test.java");
        assertFalse(entries.isEmpty());
        assertEquals("(default)", entries.get(0).storeName);
    }

    @Test
    public void detectsDefaultValue() {
        String src = "prefs.getString(\"city\", \"Tokyo\");\n";
        List<SharedPreferencesEntry> entries = scanner.analyzeSource(src, "Test.java");
        assertEquals(1, entries.size());
        assertEquals("Tokyo", entries.get(0).defaultValue);
    }

    @Test
    public void detectsMultipleKeysOnSeparateLines() {
        String src = "prefs.getInt(\"score\", 0);\n"
                + "prefs.putString(\"username\", name);\n"
                + "prefs.getLong(\"timestamp\", 0L);\n";
        List<SharedPreferencesEntry> entries = scanner.analyzeSource(src, "Test.java");
        assertEquals(3, entries.size());
        assertEquals("score", entries.get(0).key);
        assertEquals("username", entries.get(1).key);
        assertEquals("timestamp", entries.get(2).key);
    }

    @Test
    public void emptySourceReturnsEmptyList() {
        List<SharedPreferencesEntry> entries = scanner.analyzeSource("", "Test.java");
        assertTrue(entries.isEmpty());
    }

    @Test
    public void shortFileNameIsCorrect() {
        String src = "prefs.getString(\"key\", \"\");\n";
        List<SharedPreferencesEntry> entries = scanner.analyzeSource(src, "/path/to/MyFragment.java");
        assertFalse(entries.isEmpty());
        assertEquals("MyFragment.java", entries.get(0).shortFileName());
    }
}
