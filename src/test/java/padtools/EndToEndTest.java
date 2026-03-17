package padtools;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import padtools.core.formats.spd.ParseErrorException;
import padtools.core.formats.spd.ParseErrorReceiver;
import padtools.core.formats.spd.SPDParser;
import padtools.core.models.*;
import padtools.core.view.*;
import padtools.editor.ImageExporter;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * E2E（エンドツーエンド）テスト。
 * SPDテキスト → パース → モデル → ビュー → 画像/PDF の全パイプラインをテスト。
 */
public class EndToEndTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    // 代表的なSPDプログラム
    private static final String FULL_PROGRAM =
            ":terminal START\n" +
            ":comment Program to find max\n" +
            "max = arr[0]\n" +
            ":while i < n\n" +
            "\t:if arr[i] > max\n" +
            "\t\tmax = arr[i]\n" +
            "\ti = i + 1\n" +
            ":call print(max)\n" +
            ":terminal END";

    private static final String SWITCH_PROGRAM =
            ":terminal START\n" +
            ":switch status\n" +
            ":case OK\n" +
            "\tProcess success\n" +
            ":case ERROR\n" +
            "\t:call handleError()\n" +
            ":case PENDING\n" +
            "\tWait and retry\n" +
            ":terminal END";

    private static final String NESTED_PROGRAM =
            ":terminal START\n" +
            ":while i < rows\n" +
            "\t:while j < cols\n" +
            "\t\t:if matrix[i][j] > threshold\n" +
            "\t\t\tmark(i, j)\n" +
            "\t\t:else\n" +
            "\t\t\tskip\n" +
            "\t\tj = j + 1\n" +
            "\ti = i + 1\n" +
            ":terminal END";

    // --- E2E: SPD → PNG ---

    @Test
    public void testFullProgramToPng() throws IOException {
        File outputFile = tempFolder.newFile("full_program.png");
        generatePng(FULL_PROGRAM, outputFile);

        assertTrue(outputFile.exists());
        assertTrue(outputFile.length() > 1000);

        // PNG画像として読み込めることを確認
        BufferedImage img = ImageIO.read(outputFile);
        assertNotNull(img);
        assertTrue(img.getWidth() > 50);
        assertTrue(img.getHeight() > 50);
    }

    @Test
    public void testSwitchProgramToPng() throws IOException {
        File outputFile = tempFolder.newFile("switch_program.png");
        generatePng(SWITCH_PROGRAM, outputFile);

        BufferedImage img = ImageIO.read(outputFile);
        assertNotNull(img);
        assertTrue(img.getWidth() > 50);
    }

    @Test
    public void testNestedProgramToPng() throws IOException {
        File outputFile = tempFolder.newFile("nested_program.png");
        generatePng(NESTED_PROGRAM, outputFile);

        BufferedImage img = ImageIO.read(outputFile);
        assertNotNull(img);
    }

    // --- E2E: SPD → PDF ---

    @Test
    public void testFullProgramToPdf() throws IOException {
        File outputFile = tempFolder.newFile("full_program.pdf");
        generatePdf(FULL_PROGRAM, outputFile);

        assertTrue(outputFile.exists());
        assertTrue(outputFile.length() > 100);

        // PDFヘッダ確認
        byte[] header = new byte[9];
        try (FileInputStream fis = new FileInputStream(outputFile)) {
            fis.read(header);
        }
        assertTrue(new String(header, StandardCharsets.ISO_8859_1).startsWith("%PDF-1.4"));
    }

    @Test
    public void testSwitchProgramToPdf() throws IOException {
        File outputFile = tempFolder.newFile("switch_program.pdf");
        generatePdf(SWITCH_PROGRAM, outputFile);

        assertTrue(outputFile.exists());
        assertTrue(outputFile.length() > 100);
    }

    // --- E2E: SPD → SVG ---

    @Test
    public void testFullProgramToSvg() throws IOException {
        File outputFile = tempFolder.newFile("full_program.svg");
        generateSvg(FULL_PROGRAM, outputFile);

        assertTrue(outputFile.exists());
        assertTrue("SVG file should not be empty", outputFile.length() > 100);

        // SVGファイルの内容確認
        byte[] bytes = new byte[(int) outputFile.length()];
        try (FileInputStream fis = new FileInputStream(outputFile)) {
            fis.read(bytes);
        }
        String content = new String(bytes, StandardCharsets.UTF_8);
        assertTrue("SVG should contain <svg tag", content.contains("<svg"));
    }

    // --- E2E: エラーハンドリング ---

    @Test
    public void testInvalidProgramStillGeneratesOutput() throws IOException {
        String invalidSPD = ":terminal START\n:unknown command\nvalid process\n:terminal END";
        List<String> errors = new ArrayList<>();

        PADModel model = SPDParser.parse(invalidSPD, new ParseErrorReceiver() {
            @Override
            public boolean receiveParseError(String lineStr, int lineNo, ParseErrorException err) {
                errors.add("line " + (lineNo + 1) + ": " + err.getUserMessage());
                return true; // continue parsing
            }
        });

        assertNotNull(model);
        assertFalse(errors.isEmpty());

        // エラーがあっても残りのノードで画像生成可能
        Model2View m2v = new Model2View();
        View view = m2v.toView(model);
        assertNotNull(view);

        BufferedImage img = View2Image.toImage(view, 1.0);
        assertNotNull(img);
    }

    // --- E2E: スケーリング ---

    @Test
    public void testScaledPngOutput() throws IOException {
        File output1x = tempFolder.newFile("scale_1x.png");
        File output2x = tempFolder.newFile("scale_2x.png");

        generatePngWithScale(FULL_PROGRAM, output1x, 1.0);
        generatePngWithScale(FULL_PROGRAM, output2x, 2.0);

        BufferedImage img1 = ImageIO.read(output1x);
        BufferedImage img2 = ImageIO.read(output2x);

        assertNotNull(img1);
        assertNotNull(img2);
        // 2xは約2倍のサイズ
        assertTrue(img2.getWidth() >= img1.getWidth() * 1.8);
    }

    // --- E2E: 空入力 ---

    @Test
    public void testEmptyProgramToPng() throws IOException {
        File outputFile = tempFolder.newFile("empty.png");
        generatePng("", outputFile);

        BufferedImage img = ImageIO.read(outputFile);
        assertNotNull(img);
    }

    // --- E2E: マルチライン ---

    @Test
    public void testMultiLineProgramToPng() throws IOException {
        String spd = ":terminal START\nLine1@\nLine2@\nLine3\n:terminal END";
        File outputFile = tempFolder.newFile("multiline.png");
        generatePng(spd, outputFile);

        BufferedImage img = ImageIO.read(outputFile);
        assertNotNull(img);
    }

    // --- E2E: テンプレートコンテンツが有効なSPDかテスト ---

    @Test
    public void testTemplateBasicIsValidSPD() {
        String template = padtools.util.Messages.get("template.basic")
                .replace("\\n", "\n").replace("\\t", "\t");
        PADModel model = SPDParser.parse(template, null);
        assertNotNull(model);
        assertNotNull(model.getTopNode());
    }

    @Test
    public void testTemplateIfElseIsValidSPD() {
        String template = padtools.util.Messages.get("template.ifelse")
                .replace("\\n", "\n").replace("\\t", "\t");
        PADModel model = SPDParser.parse(template, null);
        assertNotNull(model);
        assertNotNull(model.getTopNode());
    }

    @Test
    public void testTemplateLoopIsValidSPD() {
        String template = padtools.util.Messages.get("template.loop")
                .replace("\\n", "\n").replace("\\t", "\t");
        PADModel model = SPDParser.parse(template, null);
        assertNotNull(model);
        assertNotNull(model.getTopNode());
    }

    @Test
    public void testTemplateSwitchIsValidSPD() {
        String template = padtools.util.Messages.get("template.switch")
                .replace("\\n", "\n").replace("\\t", "\t");
        PADModel model = SPDParser.parse(template, null);
        assertNotNull(model);
        assertNotNull(model.getTopNode());
    }

    // --- ヘルパー ---

    private void generatePng(String spd, File output) throws IOException {
        generatePngWithScale(spd, output, 1.0);
    }

    private void generatePngWithScale(String spd, File output, double scale) throws IOException {
        PADModel model = SPDParser.parse(spd, null);
        assertNotNull("Model should not be null", model);

        Model2View m2v = new Model2View();
        View view = m2v.toView(model);
        assertNotNull("View should not be null", view);

        BufferedImage img = View2Image.toImage(view, scale);
        ImageIO.write(img, "png", output);
    }

    private void generatePdf(String spd, File output) throws IOException {
        PADModel model = SPDParser.parse(spd, null);
        assertNotNull(model);

        Model2View m2v = new Model2View();
        View view = m2v.toView(model);
        BufferedImage img = View2Image.toImage(view, 1.0);
        PdfWriter.writeImageAsPdf(img, output);
    }

    private void generateSvg(String spd, File output) throws IOException {
        PADModel model = SPDParser.parse(spd, null);
        assertNotNull(model);

        Model2View m2v = new Model2View();
        View view = m2v.toView(model);

        BufferedImage tmpImg = new BufferedImage(1, 1, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D tmpG = tmpImg.createGraphics();
        java.awt.geom.Point2D.Double size = view.getSize(tmpG);
        Rectangle bounds = new Rectangle((int) size.x, (int) size.y);

        ImageExporter.writeSvg(view, output, bounds);
    }
}
