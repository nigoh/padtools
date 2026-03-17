package padtools.util;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * PathUtil のユニットテスト。
 */
public class PathUtilTest {

    @Test
    public void testExtConvertBasic() {
        String result = PathUtil.extConvert("file.spd", "png");
        assertEquals("file.png", result);
    }

    @Test
    public void testExtConvertWithPath() {
        String result = PathUtil.extConvert("/path/to/file.spd", "svg");
        assertEquals("/path/to/file.svg", result);
    }

    @Test
    public void testExtConvertNoExtension() {
        String result = PathUtil.extConvert("file", "pdf");
        assertEquals("file.pdf", result);
    }

    @Test
    public void testExtConvertMultipleDots() {
        String result = PathUtil.extConvert("my.file.name.spd", "png");
        assertEquals("my.file.name.png", result);
    }

    @Test
    public void testGetBasePath() {
        String path = PathUtil.getBasePath();
        assertNotNull(path);
        assertFalse(path.isEmpty());
    }
}
