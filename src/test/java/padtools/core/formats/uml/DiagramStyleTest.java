// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package padtools.core.formats.uml;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

/**
 * {@link DiagramStyle#toPlantUmlPrelude()} の出力規則とコピー動作のテスト。
 */
public class DiagramStyleTest {

    @Test
    public void emptyStyleProducesEmptyPrelude() {
        DiagramStyle s = DiagramStyle.defaults();
        assertEquals("", s.toPlantUmlPrelude());
    }

    @Test
    public void themeOnlyEmitsThemeLine() {
        DiagramStyle s = new DiagramStyle();
        s.setTheme("cerulean");
        assertEquals("!theme cerulean\n", s.toPlantUmlPrelude());
    }

    @Test
    public void allFieldsEmitAllLines() {
        DiagramStyle s = new DiagramStyle();
        s.setTheme("plain");
        s.setBackgroundColor("#FFFFFF");
        s.setFontName("Helvetica");
        s.setFontSize(12);
        s.setDirection(DiagramStyle.Direction.LEFT_TO_RIGHT);
        s.setCustomSkinparam("skinparam shadowing false");
        String out = s.toPlantUmlPrelude();
        assertTrue(out, out.contains("!theme plain\n"));
        assertTrue(out, out.contains("skinparam backgroundColor #FFFFFF\n"));
        assertTrue(out, out.contains("skinparam defaultFontName Helvetica\n"));
        assertTrue(out, out.contains("skinparam defaultFontSize 12\n"));
        assertTrue(out, out.contains("left to right direction\n"));
        assertTrue(out, out.contains("skinparam shadowing false\n"));
    }

    @Test
    public void topToBottomDirectionEmitsExplicitLine() {
        DiagramStyle s = new DiagramStyle();
        s.setDirection(DiagramStyle.Direction.TOP_TO_BOTTOM);
        assertEquals("top to bottom direction\n", s.toPlantUmlPrelude());
    }

    @Test
    public void customSkinparamWithoutTrailingNewlineGetsOne() {
        DiagramStyle s = new DiagramStyle();
        s.setCustomSkinparam("skinparam shadowing false");
        assertEquals("skinparam shadowing false\n", s.toPlantUmlPrelude());
    }

    @Test
    public void customSkinparamCrlfIsNormalized() {
        DiagramStyle s = new DiagramStyle();
        s.setCustomSkinparam("skinparam shadowing false\r\nskinparam classBackgroundColor #EEE\r\n");
        String out = s.toPlantUmlPrelude();
        assertEquals(
                "skinparam shadowing false\nskinparam classBackgroundColor #EEE\n",
                out);
    }

    @Test
    public void copyReturnsIndependentInstance() {
        DiagramStyle s = new DiagramStyle();
        s.setTheme("plain");
        DiagramStyle c = s.copy();
        assertNotSame(s, c);
        assertEquals(s, c);
        c.setTheme("hacker");
        assertEquals("plain", s.getTheme());
    }

    @Test
    public void nullSettersDoNotThrow() {
        DiagramStyle s = new DiagramStyle();
        s.setTheme(null);
        s.setBackgroundColor(null);
        s.setFontName(null);
        s.setCustomSkinparam(null);
        s.setDirection(null);
        s.setFontSize(-5);
        assertEquals("", s.getTheme());
        assertEquals("", s.getBackgroundColor());
        assertEquals("", s.getFontName());
        assertEquals("", s.getCustomSkinparam());
        assertEquals(DiagramStyle.Direction.DEFAULT, s.getDirection());
        assertEquals(0, s.getFontSize());
    }
}
