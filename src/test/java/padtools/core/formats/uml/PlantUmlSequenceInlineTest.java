package padtools.core.formats.uml;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertTrue;

/**
 * フィールド初期化子 (匿名クラス / ラムダ) のリスナー本体が、シーケンス図に
 * 展開されることを確認するエンドツーエンドテスト。
 */
public class PlantUmlSequenceInlineTest {

    @Test
    public void testFieldListenerBodyIsExpandedInSequenceDiagram() {
        String src = ""
                + "class Foo {\n"
                + "  private OnClickListener listener = new OnClickListener() {\n"
                + "    public void onClick(View v) { mService.start(); }\n"
                + "  };\n"
                + "  private IService mService;\n"
                + "  void register() { listener.onClick(null); }\n"
                + "}";
        List<JavaClassInfo> classes = JavaStructureExtractor.extract(src);
        String diagram = PlantUmlSequenceDiagram.generate(classes, "Foo", "register", null);
        // フィールド型に解決された participant がアクティベートされ、本体内の
        // mService.start() が展開されていること (mService のフィールド型 IService に解決)
        assertTrue("should activate listener (field type): \n" + diagram,
                diagram.contains("activate OnClickListener"));
        assertTrue("should call IService.start in inline body: \n" + diagram,
                diagram.contains(" -> IService: IService.start()"));
    }

    @Test
    public void testLambdaListenerBodyIsExpanded() {
        String src = ""
                + "class Bar {\n"
                + "  Runnable r = () -> mWorker.execute();\n"
                + "  private Executor mWorker;\n"
                + "  void kick() { r.run(); }\n"
                + "}";
        List<JavaClassInfo> classes = JavaStructureExtractor.extract(src);
        String diagram = PlantUmlSequenceDiagram.generate(classes, "Bar", "kick", null);
        assertTrue("lambda body should expand: \n" + diagram,
                diagram.contains(" -> Executor: Executor.execute()"));
        assertTrue("Runnable participant should be activated: \n" + diagram,
                diagram.contains("activate Runnable"));
    }

    @Test
    public void testConstructorAssignmentListenerExpanded() {
        // コンストラクタ内で代入したリスナーも、後で listener.onClick() を呼んだ際に
        // シーケンス図で展開できること
        String src = ""
                + "class Baz {\n"
                + "  private OnClickListener listener;\n"
                + "  Baz() {\n"
                + "    this.listener = new OnClickListener() {\n"
                + "      public void onClick(View v) { mService.start(); }\n"
                + "    };\n"
                + "  }\n"
                + "  private IService mService;\n"
                + "  void register() { listener.onClick(null); }\n"
                + "}";
        List<JavaClassInfo> classes = JavaStructureExtractor.extract(src);
        String diagram = PlantUmlSequenceDiagram.generate(classes, "Baz", "register", null);
        assertTrue("constructor-assigned listener should expand: \n" + diagram,
                diagram.contains(" -> IService: IService.start()"));
    }

    @Test
    public void testFieldMethodReferenceExpanded() {
        // メソッド参照によるフィールド初期化も展開できること
        String src = ""
                + "class Qux {\n"
                + "  Runnable r = Worker::tick;\n"
                + "  void kick() { r.run(); }\n"
                + "}";
        List<JavaClassInfo> classes = JavaStructureExtractor.extract(src);
        String diagram = PlantUmlSequenceDiagram.generate(classes, "Qux", "kick", null);
        assertTrue("method reference body should expand: \n" + diagram,
                diagram.contains("Worker.tick()"));
    }
}
