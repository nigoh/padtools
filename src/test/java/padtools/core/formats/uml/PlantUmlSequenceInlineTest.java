package padtools.core.formats.uml;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertTrue;

/**
 * フィールド初期化子 (匿名クラス / ラムダ) およびメソッド引数に直接渡された
 * コールバック/リスナー本体がシーケンス図に展開されることを確認するテスト。
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

    // ── 引数渡しコールバック (call.getInlineMethods) の展開テスト ──────────────────

    @Test
    public void testAnonymousClassArgExpandedWithOwnerClass() {
        // button.setOnClickListener(new OnClickListener() { onClick(){...} }) の形で
        // 引数に渡した匿名クラスの本体がシーケンス図に展開され、
        // 参加者名が「定義元クラス$メソッド名」になること
        String src = ""
                + "class MyActivity {\n"
                + "  private IService mService;\n"
                + "  void onCreate() {\n"
                + "    button.setOnClickListener(new OnClickListener() {\n"
                + "      public void onClick(View v) { mService.start(); }\n"
                + "    });\n"
                + "  }\n"
                + "}";
        List<JavaClassInfo> classes = JavaStructureExtractor.extract(src);
        String diagram = PlantUmlSequenceDiagram.generate(classes, "MyActivity", "onCreate", null);
        // 定義元クラス$コールバックメソッド名 の参加者が生成されること
        assertTrue("inline participant should be MyActivity$onClick: \n" + diagram,
                diagram.contains("\"MyActivity$onClick\""));
        // <<inline>> ステレオタイプが付くこと
        assertTrue("inline stereotype should appear: \n" + diagram,
                diagram.contains("<<inline>>"));
        // コールバック本体内の mService.start() が展開されること
        assertTrue("callback body should be expanded: \n" + diagram,
                diagram.contains("IService") && diagram.contains("start()"));
    }

    @Test
    public void testLambdaArgExpandedWithOwnerClass() {
        // setOnClickListener(v -> mService.start()) のラムダ引数が展開されること
        String src = ""
                + "class MyFragment {\n"
                + "  private IService mService;\n"
                + "  void onViewCreated() {\n"
                + "    btn.setOnClickListener(v -> mService.start());\n"
                + "  }\n"
                + "}";
        List<JavaClassInfo> classes = JavaStructureExtractor.extract(src);
        String diagram = PlantUmlSequenceDiagram.generate(classes, "MyFragment", "onViewCreated", null);
        assertTrue("inline participant should contain MyFragment$: \n" + diagram,
                diagram.contains("\"MyFragment$"));
        assertTrue("<<inline>> stereotype should appear: \n" + diagram,
                diagram.contains("<<inline>>"));
        assertTrue("lambda body mService.start() should be expanded: \n" + diagram,
                diagram.contains("start()"));
    }

    @Test
    public void testInlineParticipantShowsOwnerClass() {
        // 参加者名に「定義元クラス$」プレフィックスがつくことを確認する
        String src = ""
                + "class Presenter {\n"
                + "  private IRepo mRepo;\n"
                + "  void load() {\n"
                + "    executor.execute(new Runnable() {\n"
                + "      public void run() { mRepo.fetch(); }\n"
                + "    });\n"
                + "  }\n"
                + "}";
        List<JavaClassInfo> classes = JavaStructureExtractor.extract(src);
        String diagram = PlantUmlSequenceDiagram.generate(classes, "Presenter", "load", null);
        assertTrue("participant should start with Presenter$: \n" + diagram,
                diagram.contains("\"Presenter$run\"") || diagram.contains("\"Presenter$"));
        // 凡例に <<inline>> の説明が出ること
        assertTrue("legend should mention <<inline>>: \n" + diagram,
                diagram.contains("<<inline>>"));
    }
}
