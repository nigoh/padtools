package padtools.editor;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.awt.Rectangle;

import javax.imageio.ImageIO;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.apache.batik.dom.GenericDOMImplementation;
import org.apache.batik.svggen.SVGGraphics2D;
import org.apache.batik.svggen.SVGGraphics2DIOException;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import padtools.core.view.BufferedView;
import padtools.core.view.PdfWriter;
import padtools.core.view.View;
import padtools.util.PathUtil;

/**
 * PAD図をPNG/SVG/PDF形式で出力するクラス。
 */
public class ImageExporter {
    private final Component parent;

    public ImageExporter(Component parent) {
        this.parent = parent;
    }

    // --- PNG ---

    public void exportAsPng(BufferedView view, File currentFile) {
        JFileChooser fc = new JFileChooser(currentFile == null ? new File(".") : currentFile);
        fc.setFileFilter(new FileNameExtensionFilter("png image(*.png)", "png"));

        File sel;
        if (currentFile == null) {
            sel = new File("./new_pad.png");
        } else {
            sel = new File(PathUtil.extConvert(currentFile.getPath(), "png"));
        }

        fc.setSelectedFile(sel);

        if (fc.showSaveDialog(parent) == JFileChooser.APPROVE_OPTION) {
            writePng(view, fc.getSelectedFile(), 1.0);
        }
    }

    public static void writePng(BufferedView view, File file, double scale) {
        BufferedImage tmp = new BufferedImage(1, 1, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D tmpg = tmp.createGraphics();

        Point2D.Double size = view.getSize(tmpg);

        BufferedImage img = new BufferedImage((int) (size.x * scale), (int) (size.y * scale), BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g = img.createGraphics();
        g.setTransform(AffineTransform.getScaleInstance(scale, scale));
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        view.getView().draw(g, new Point2D.Double());

        try {
            ImageIO.write(img, "png", file);
        } catch (IOException ex) {
            System.err.println("Failed to write PNG: " + ex.getMessage());
        }
    }

    // --- SVG ---

    public void exportAsSvg(BufferedView view, File currentFile, Rectangle viewBounds) {
        JFileChooser fc = new JFileChooser(currentFile == null ? new File(".") : currentFile);
        fc.setFileFilter(new FileNameExtensionFilter("svg image(*.svg)", "svg"));

        File sel;
        if (currentFile == null) {
            sel = new File("./new_pad.svg");
        } else {
            sel = new File(PathUtil.extConvert(currentFile.getPath(), "svg"));
        }

        fc.setSelectedFile(sel);

        if (fc.showSaveDialog(parent) == JFileChooser.APPROVE_OPTION) {
            writeSvg(view.getView(), fc.getSelectedFile(), viewBounds);
        }
    }

    public static void writeSvg(View view, File file, Rectangle bounds) {
        DOMImplementation domImpl = GenericDOMImplementation.getDOMImplementation();
        Document document = domImpl.createDocument(null, "svg", null);
        SVGGraphics2D svg2d = new SVGGraphics2D(document);
        svg2d.setBackground(new Color(255, 255, 255, 0));
        svg2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        view.draw(svg2d, new Point2D.Double());
        Element sv = svg2d.getRoot();
        sv.setAttribute("xml:space", "preserve");
        sv.setAttribute("width", Integer.toString((int) bounds.getWidth()));
        sv.setAttribute("height", Integer.toString((int) bounds.getHeight()));
        sv.setAttribute("viewBox",
                "0 0 " +
                        Integer.toString((int) bounds.getWidth()) + " " +
                        Integer.toString((int) bounds.getHeight())
        );
        try (OutputStream os = new FileOutputStream(file);
             BufferedOutputStream bos = new BufferedOutputStream(os);
             Writer out = new OutputStreamWriter(bos, "UTF-8")) {
            svg2d.stream(sv, out);
        } catch (UnsupportedEncodingException ue) {
            System.err.println("Failed to write SVG (encoding): " + ue.getMessage());
        } catch (SVGGraphics2DIOException se) {
            System.err.println("Failed to write SVG: " + se.getMessage());
        } catch (IOException ioe) {
            System.err.println("Failed to write SVG (IO): " + ioe.getMessage());
        }
    }

    // --- PDF ---

    public void exportAsPdf(BufferedView view, File currentFile) {
        JFileChooser fc = new JFileChooser(currentFile == null ? new File(".") : currentFile);
        fc.setFileFilter(new FileNameExtensionFilter("PDF document(*.pdf)", "pdf"));

        File sel;
        if (currentFile == null) {
            sel = new File("./new_pad.pdf");
        } else {
            sel = new File(PathUtil.extConvert(currentFile.getPath(), "pdf"));
        }

        fc.setSelectedFile(sel);

        if (fc.showSaveDialog(parent) == JFileChooser.APPROVE_OPTION) {
            writePdf(view, fc.getSelectedFile(), 1.0);
        }
    }

    public static void writePdf(BufferedView view, File file, double scale) {
        BufferedImage tmp = new BufferedImage(1, 1, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D tmpg = tmp.createGraphics();
        Point2D.Double size = view.getSize(tmpg);

        int w = (int) (size.x * scale);
        int h = (int) (size.y * scale);

        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g = img.createGraphics();
        g.setTransform(AffineTransform.getScaleInstance(scale, scale));
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        view.getView().draw(g, new Point2D.Double());

        try {
            PdfWriter.writeImageAsPdf(img, file);
        } catch (IOException ex) {
            System.err.println("Failed to write PDF: " + ex.getMessage());
        }
    }

    // --- クリップボード ---

    public void copyToClipboard(BufferedView view) {
        if (view == null) return;
        BufferedImage tmp = new BufferedImage(1, 1, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D tmpg = tmp.createGraphics();
        Point2D.Double size = view.getSize(tmpg);

        int w = (int) size.x;
        int h = (int) size.y;
        if (w <= 0 || h <= 0) return;

        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        view.getView().draw(g, new Point2D.Double());

        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(new ImageSelection(img), null);
    }

    private static class ImageSelection implements Transferable {
        private final BufferedImage image;

        ImageSelection(BufferedImage image) {
            this.image = image;
        }

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[]{DataFlavor.imageFlavor};
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return DataFlavor.imageFlavor.equals(flavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
            if (!isDataFlavorSupported(flavor)) {
                throw new UnsupportedFlavorException(flavor);
            }
            return image;
        }
    }

    // --- 印刷 ---

    public void print(BufferedView view) {
        if (view == null) return;
        PrinterJob job = PrinterJob.getPrinterJob();
        job.setPrintable(new PadPrintable(view));
        if (job.printDialog()) {
            try {
                job.print();
            } catch (PrinterException ex) {
                System.err.println("Failed to print: " + ex.getMessage());
            }
        }
    }

    private static class PadPrintable implements Printable {
        private final BufferedView view;

        PadPrintable(BufferedView view) {
            this.view = view;
        }

        @Override
        public int print(Graphics graphics, PageFormat pageFormat, int pageIndex) {
            if (pageIndex > 0) return NO_SUCH_PAGE;

            Graphics2D g = (Graphics2D) graphics;
            g.translate(pageFormat.getImageableX(), pageFormat.getImageableY());

            BufferedImage tmp = new BufferedImage(1, 1, BufferedImage.TYPE_3BYTE_BGR);
            Point2D.Double size = view.getSize(tmp.createGraphics());

            double scaleX = pageFormat.getImageableWidth() / size.x;
            double scaleY = pageFormat.getImageableHeight() / size.y;
            double scale = Math.min(scaleX, scaleY);
            if (scale > 1.0) scale = 1.0;

            g.scale(scale, scale);
            view.getView().draw(g, new Point2D.Double());

            return PAGE_EXISTS;
        }
    }
}
