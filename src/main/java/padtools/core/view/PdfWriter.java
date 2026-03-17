package padtools.core.view;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import javax.imageio.ImageIO;

/**
 * 最小限のPDF生成ユーティリティ。
 * PNG画像を1ページのPDFに埋め込む。
 */
public class PdfWriter {

    private PdfWriter() {}

    /**
     * BufferedImageをPDFファイルとして書き出す。
     */
    public static void writeImageAsPdf(BufferedImage image, File file) throws IOException {
        // PNG画像データをバイト列に変換
        ByteArrayOutputStream imgBytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", imgBytes);
        byte[] imageData = imgBytes.toByteArray();

        int imgWidth = image.getWidth();
        int imgHeight = image.getHeight();

        // PDF内のページサイズ（ポイント単位、72dpi基準）
        // 画像を72dpiとして扱い、A4に収める
        float pageWidth = Math.max(imgWidth, 595.28f);  // A4 width
        float pageHeight = Math.max(imgHeight, 841.89f); // A4 height

        // 画像をページ中央上部に配置
        float displayWidth = imgWidth;
        float displayHeight = imgHeight;

        // ページに収まらない場合はスケーリング
        if (displayWidth > pageWidth - 40 || displayHeight > pageHeight - 40) {
            float scaleX = (pageWidth - 40) / displayWidth;
            float scaleY = (pageHeight - 40) / displayHeight;
            float scale = Math.min(scaleX, scaleY);
            displayWidth *= scale;
            displayHeight *= scale;
            pageWidth = Math.max(displayWidth + 40, pageWidth);
            pageHeight = Math.max(displayHeight + 40, pageHeight);
        }

        float imgX = (pageWidth - displayWidth) / 2;
        float imgY = pageHeight - displayHeight - 20;

        try (OutputStream os = new FileOutputStream(file)) {
            StringBuilder pdf = new StringBuilder();

            // ヘッダ
            pdf.append("%PDF-1.4\n");

            // オブジェクト1: カタログ
            int obj1Offset = pdf.length();
            pdf.append("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n");

            // オブジェクト2: ページツリー
            int obj2Offset = pdf.length();
            pdf.append("2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n");

            // オブジェクト3: ページ
            int obj3Offset = pdf.length();
            pdf.append("3 0 obj\n<< /Type /Page /Parent 2 0 R ");
            pdf.append(String.format("/MediaBox [0 0 %.2f %.2f] ", pageWidth, pageHeight));
            pdf.append("/Contents 4 0 R /Resources << /XObject << /Img0 5 0 R >> >> >>\nendobj\n");

            // オブジェクト4: ページ内容（画像配置コマンド）
            String content = String.format("q\n%.2f 0 0 %.2f %.2f %.2f cm\n/Img0 Do\nQ\n",
                    displayWidth, displayHeight, imgX, imgY);
            int obj4Offset = pdf.length();
            pdf.append("4 0 obj\n<< /Length ").append(content.length()).append(" >>\nstream\n");
            pdf.append(content);
            pdf.append("endstream\nendobj\n");

            // ヘッダ部分をバイト列に変換
            byte[] headerBytes = pdf.toString().getBytes("ISO-8859-1");
            os.write(headerBytes);

            // オブジェクト5: 画像XObject（バイナリデータ含む）
            int obj5Offset = headerBytes.length;
            String imgHeader = "5 0 obj\n<< /Type /XObject /Subtype /Image "
                    + "/Width " + imgWidth + " /Height " + imgHeight
                    + " /ColorSpace /DeviceRGB /BitsPerComponent 8"
                    + " /Filter /FlateDecode"
                    + " /Length " + compressedImageData(image).length + " >>\nstream\n";
            os.write(imgHeader.getBytes("ISO-8859-1"));

            byte[] compressedData = compressedImageData(image);
            os.write(compressedData);
            os.write("\nendstream\nendobj\n".getBytes("ISO-8859-1"));

            int afterObj5 = obj5Offset + imgHeader.length() + compressedData.length + "\nendstream\nendobj\n".length();

            // クロスリファレンステーブル
            String xref = "xref\n0 6\n"
                    + String.format("0000000000 65535 f \n")
                    + String.format("%010d 00000 n \n", obj1Offset)
                    + String.format("%010d 00000 n \n", obj2Offset)
                    + String.format("%010d 00000 n \n", obj3Offset)
                    + String.format("%010d 00000 n \n", obj4Offset)
                    + String.format("%010d 00000 n \n", obj5Offset);
            os.write(xref.getBytes("ISO-8859-1"));

            String trailer = "trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n"
                    + afterObj5 + "\n%%EOF\n";
            os.write(trailer.getBytes("ISO-8859-1"));
        }
    }

    /**
     * 画像のRGBデータをDeflate圧縮する。
     */
    private static byte[] compressedImageData(BufferedImage image) throws IOException {
        int w = image.getWidth();
        int h = image.getHeight();

        // RGB生データを取得
        ByteArrayOutputStream rawBytes = new ByteArrayOutputStream();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = image.getRGB(x, y);
                rawBytes.write((rgb >> 16) & 0xFF); // R
                rawBytes.write((rgb >> 8) & 0xFF);  // G
                rawBytes.write(rgb & 0xFF);          // B
            }
        }

        // Deflate圧縮
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        java.util.zip.DeflaterOutputStream deflater = new java.util.zip.DeflaterOutputStream(compressed);
        deflater.write(rawBytes.toByteArray());
        deflater.finish();
        deflater.close();

        return compressed.toByteArray();
    }
}
