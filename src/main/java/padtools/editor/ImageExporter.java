package padtools.editor;

import java.awt.Color;
import java.awt.Component;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.awt.Graphics2D;
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
import padtools.core.view.View;
import padtools.util.PathUtil;

/**
 * PAD図をPNG/SVG形式で出力するクラス。
 */
public class ImageExporter {
    private final Component parent;

    public ImageExporter(Component parent) {
        this.parent = parent;
    }

    /**
     * PNG形式で保存ダイアログを表示し、エクスポートする。
     */
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

    /**
     * PNG画像をファイルに書き出す。
     */
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
            ex.printStackTrace();
        }
    }

    /**
     * SVG形式で保存ダイアログを表示し、エクスポートする。
     */
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

    /**
     * SVG画像をファイルに書き出す。
     */
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
            ue.printStackTrace();
        } catch (SVGGraphics2DIOException se) {
            se.printStackTrace();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }
}
