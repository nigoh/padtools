package padtools.core.view;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

/**
 * PdfWriter のユニットテスト。
 */
public class PdfWriterTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private BufferedImage createTestImage(int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);
        g.setColor(Color.BLACK);
        g.drawRect(10, 10, width - 20, height - 20);
        g.dispose();
        return img;
    }

    @Test
    public void testWriteImageAsPdf() throws IOException {
        BufferedImage img = createTestImage(200, 100);
        File pdf = tempFolder.newFile("test.pdf");

        PdfWriter.writeImageAsPdf(img, pdf);

        assertTrue(pdf.exists());
        assertTrue(pdf.length() > 100);

        // PDFヘッダの確認
        byte[] header = new byte[8];
        try (FileInputStream fis = new FileInputStream(pdf)) {
            fis.read(header);
        }
        String headerStr = new String(header, StandardCharsets.ISO_8859_1);
        assertTrue(headerStr.startsWith("%PDF-1.4"));
    }

    @Test
    public void testWriteImageAsPdfContainsEOF() throws IOException {
        BufferedImage img = createTestImage(100, 100);
        File pdf = tempFolder.newFile("test_eof.pdf");

        PdfWriter.writeImageAsPdf(img, pdf);

        // PDFの末尾に%%EOFが含まれることを確認
        byte[] bytes = new byte[(int) pdf.length()];
        try (FileInputStream fis = new FileInputStream(pdf)) {
            fis.read(bytes);
        }
        String content = new String(bytes, StandardCharsets.ISO_8859_1);
        assertTrue(content.contains("%%EOF"));
        assertTrue(content.contains("/Type /Catalog"));
        assertTrue(content.contains("/Type /Page"));
        assertTrue(content.contains("/Subtype /Image"));
    }

    @Test
    public void testLargeImageScaling() throws IOException {
        // ページサイズを超える大きな画像
        BufferedImage img = createTestImage(2000, 3000);
        File pdf = tempFolder.newFile("test_large.pdf");

        PdfWriter.writeImageAsPdf(img, pdf);

        assertTrue(pdf.exists());
        assertTrue(pdf.length() > 0);
    }

    @Test
    public void testSmallImage() throws IOException {
        BufferedImage img = createTestImage(10, 10);
        File pdf = tempFolder.newFile("test_small.pdf");

        PdfWriter.writeImageAsPdf(img, pdf);

        assertTrue(pdf.exists());
        assertTrue(pdf.length() > 0);
    }
}
