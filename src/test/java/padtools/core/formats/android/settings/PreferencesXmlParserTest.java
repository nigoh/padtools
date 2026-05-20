package padtools.core.formats.android.settings;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * PreferencesXmlParser のユニットテスト。
 */
public class PreferencesXmlParserTest {

    private final PreferencesXmlParser parser = new PreferencesXmlParser();

    private static final String SIMPLE_XML =
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
            + "<PreferenceScreen xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
            + "    <SwitchPreference\n"
            + "        android:key=\"notifications_enabled\"\n"
            + "        android:title=\"Notifications\"\n"
            + "        android:defaultValue=\"true\" />\n"
            + "    <EditTextPreference\n"
            + "        android:key=\"username\"\n"
            + "        android:title=\"User Name\"\n"
            + "        android:defaultValue=\"\" />\n"
            + "</PreferenceScreen>\n";

    @Test
    public void parsesKeysFromXml() {
        List<PreferenceXmlEntry> entries = parser.parse(SIMPLE_XML, "preferences.xml");
        assertEquals(2, entries.size());
    }

    @Test
    public void parsesSwitchPreferenceAttributes() {
        List<PreferenceXmlEntry> entries = parser.parse(SIMPLE_XML, "preferences.xml");
        PreferenceXmlEntry e = entries.get(0);
        assertEquals("notifications_enabled", e.key);
        assertEquals("SwitchPreference", e.elementType);
        assertEquals("true", e.defaultValue);
        assertEquals("Notifications", e.title);
    }

    @Test
    public void parsesEditTextPreference() {
        List<PreferenceXmlEntry> entries = parser.parse(SIMPLE_XML, "preferences.xml");
        PreferenceXmlEntry e = entries.get(1);
        assertEquals("username", e.key);
        assertEquals("EditTextPreference", e.elementType);
    }

    @Test
    public void returnsEmptyOnInvalidXml() {
        List<PreferenceXmlEntry> entries = parser.parse("not-valid-xml", "bad.xml");
        assertTrue(entries.isEmpty());
    }

    @Test
    public void parsesNestedPreferences() {
        String xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<PreferenceScreen xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                + "    <PreferenceCategory android:title=\"Category\">\n"
                + "        <CheckBoxPreference android:key=\"opt_a\" android:defaultValue=\"false\" />\n"
                + "    </PreferenceCategory>\n"
                + "</PreferenceScreen>\n";
        List<PreferenceXmlEntry> entries = parser.parse(xml, "prefs.xml");
        assertEquals(1, entries.size());
        assertEquals("opt_a", entries.get(0).key);
    }
}
