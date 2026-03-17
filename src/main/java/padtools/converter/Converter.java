package padtools.converter;
import padtools.core.formats.spd.ParseErrorException;
import padtools.core.formats.spd.ParseErrorReceiver;
import padtools.core.formats.spd.SPDParser;
import padtools.core.models.PADModel;
import padtools.core.view.Model2View;
import padtools.core.view.PdfWriter;
import padtools.core.view.View;
import padtools.core.view.View2Image;
import padtools.core.view.BufferedView;
import padtools.editor.ImageExporter;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * コンバーターのメインクラス
 */
public class Converter {
    public static void  convert(File file_in, File file_out, Double scale){
        InputStream in;
        if(file_in == null){
            in = System.in;
        }
        else{
            try{
                in = new FileInputStream(file_in);
            }
            catch(FileNotFoundException ex){
                System.err.println(String.format("File is not found: %s", file_in));
                System.exit(1);
                return;
            }
        }

        PrintStream out;
        if(file_out == null){
            out = System.out;
        }
        else{
            try{
                out = new PrintStream(file_out);
            }
            catch(FileNotFoundException ex){
                System.err.println(String.format("File is not found: %s", file_out));
                System.exit(1);
                return;
            }
        }

        if(scale == null){
            scale = 1.0;
        }

        //入力する
        BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        String buf;
        try{
            while((buf = br.readLine()) != null ){
                pw.println(buf);
            }
        }
        catch (IOException ex){
        }

        PADModel pad = SPDParser.parse(sw.toString(), new ParseErrorReceiver() {
            @Override
            public boolean receiveParseError(String lineStr, int lineNo, ParseErrorException err) {
                System.err.println(String.format("%d: %s", lineNo, err.getUserMessage()));
                return true;
            }
        });

        Model2View m2v = new Model2View();
        View view = m2v.toView(pad);
        BufferedImage image = View2Image.toImage(view, scale);

        String outputName = file_out != null ? file_out.getName().toLowerCase() : "";
        try{
            if (outputName.endsWith(".pdf")) {
                PdfWriter.writeImageAsPdf(image, file_out);
            } else if (outputName.endsWith(".svg") && file_out != null) {
                BufferedView bv = new BufferedView(view, true);
                java.awt.Rectangle bounds = new java.awt.Rectangle(image.getWidth(), image.getHeight());
                ImageExporter.writeSvg(view, file_out, bounds);
            } else {
                ImageIO.write(image, "png", out);
            }
        }
        catch(IOException ex){
            System.err.println(ex.getLocalizedMessage());
            System.exit(1);
        }
    }
}
