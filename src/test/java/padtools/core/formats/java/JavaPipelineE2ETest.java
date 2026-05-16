package padtools.core.formats.java;

import org.junit.Test;
import padtools.core.formats.spd.ParseErrorException;
import padtools.core.formats.spd.ParseErrorReceiver;
import padtools.core.formats.spd.SPDParser;
import padtools.core.models.PADModel;
import padtools.core.view.Model2View;
import padtools.core.view.View;
import padtools.core.view.View2Image;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Java ソース → SPD → PADModel → 画像 までのパイプライン全体を実行する E2E テスト。
 */
public class JavaPipelineE2ETest {

    private BufferedImage render(String javaSource) {
        String spd = JavaSourceConverter.convert(javaSource);
        final List<String> errors = new ArrayList<>();
        PADModel pad = SPDParser.parse(spd, new ParseErrorReceiver() {
            @Override
            public boolean receiveParseError(String lineStr, int lineNo, ParseErrorException err) {
                errors.add("line " + (lineNo + 1) + ": " + err.getUserMessage());
                return true;
            }
        });
        assertTrue("SPD parse errors: " + errors + "\nSPD:\n" + spd, errors.isEmpty());
        assertNotNull("PAD model is null", pad);
        View view = new Model2View().toView(pad);
        BufferedImage img = View2Image.toImage(view, 1.0);
        assertNotNull("rendered image is null", img);
        assertTrue("image width must be positive", img.getWidth() > 0);
        assertTrue("image height must be positive", img.getHeight() > 0);
        return img;
    }

    @Test
    public void testSimpleClassRenders() {
        BufferedImage img = render(
                "public class Hello { public static void main(String[] args) {"
                        + " System.out.println(\"Hello\"); } }");
        assertNotNull(img);
    }

    @Test
    public void testFizzBuzzRenders() {
        BufferedImage img = render(
                "public class FizzBuzz {\n"
                        + "  public void run(int n) {\n"
                        + "    for (int i = 1; i <= n; i++) {\n"
                        + "      if (i % 15 == 0) print(\"FizzBuzz\");\n"
                        + "      else if (i % 3 == 0) print(\"Fizz\");\n"
                        + "      else if (i % 5 == 0) print(\"Buzz\");\n"
                        + "      else print(String.valueOf(i));\n"
                        + "    }\n"
                        + "  }\n"
                        + "}\n");
        assertNotNull(img);
    }

    @Test
    public void testAndroidActivityRenders() {
        BufferedImage img = render(
                "package com.example.app;\n"
                        + "public class MainActivity {\n"
                        + "  private int count = 0;\n"
                        + "  public void onCreate() {\n"
                        + "    setContentView(R.layout.main);\n"
                        + "    Button button = findViewById(R.id.btn);\n"
                        + "    button.setOnClickListener(v -> count++);\n"
                        + "    while (count < 10) {\n"
                        + "      try {\n"
                        + "        doWork();\n"
                        + "      } catch (Exception e) {\n"
                        + "        Log.e(TAG, e.getMessage());\n"
                        + "        break;\n"
                        + "      }\n"
                        + "    }\n"
                        + "  }\n"
                        + "}\n");
        assertNotNull(img);
    }

    @Test
    public void testSwitchPatternRenders() {
        BufferedImage img = render(
                "class A { String f(int x) { switch (x) {\n"
                        + "  case 1: return \"one\";\n"
                        + "  case 2: case 3: return \"few\";\n"
                        + "  case 4: case 5: case 6: case 7: case 8: case 9: return \"many\";\n"
                        + "  default: return \"unknown\";\n"
                        + "} } }");
        assertNotNull(img);
    }

    @Test
    public void testNestedLoopsRenders() {
        BufferedImage img = render(
                "class M { void multiply(int[][] a, int[][] b, int[][] c, int n) {\n"
                        + "  for (int i = 0; i < n; i++) {\n"
                        + "    for (int j = 0; j < n; j++) {\n"
                        + "      c[i][j] = 0;\n"
                        + "      for (int k = 0; k < n; k++) {\n"
                        + "        c[i][j] += a[i][k] * b[k][j];\n"
                        + "      }\n"
                        + "    }\n"
                        + "  }\n"
                        + "} }");
        assertNotNull(img);
    }

    @Test
    public void testEmptyClassRendersAsEmpty() {
        String spd = JavaSourceConverter.convert("class Empty {}");
        PADModel pad = SPDParser.parse(spd, null);
        // メソッドが無い場合 SPD は空 → null トップノード
        assertNotNull(pad);
        assertNull(pad.getTopNode());
    }
}
