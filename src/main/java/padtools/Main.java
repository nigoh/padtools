package padtools;

import padtools.app.cli.AnalysisCommands;
import padtools.app.cli.AndroidCommands;
import padtools.app.cli.AospCommands;
import padtools.app.cli.CliContext;
import padtools.app.cli.UmlCommands;
import padtools.app.uml.UmlApp;
import padtools.core.formats.uml.GraphvizLocator;
import padtools.core.formats.uml.PlantUmlRenderer;
import padtools.util.ErrorListener;
import padtools.util.Option;
import padtools.util.OptionParser;
import padtools.util.UnknownOptionException;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;

/**
 * エントリポイントクラス。
 *
 * <p>CLI 引数を解釈し、各出力モードを {@code padtools.app.cli} 配下のコマンドクラス
 * ({@link UmlCommands} / {@link AndroidCommands} / {@link AospCommands} /
 * {@link AnalysisCommands}) に委譲する。どのモードにも該当しなければ UML 専用 GUI を起動する。</p>
 */
public class Main {

    /**
     * Settingを取得する（SettingManager経由）
     */
    public static Setting getSetting() {
        return SettingManager.getInstance().getSetting();
    }

    /**
     * Settingを保存する（SettingManager経由）
     */
    public static void saveSetting() {
        SettingManager.getInstance().save();
    }

    /** 実行中の jar が置かれているディレクトリを返す。不明なら null。 */
    private static File detectJarDir() {
        try {
            URL loc = Main.class.getProtectionDomain().getCodeSource().getLocation();
            if (loc == null) {
                return null;
            }
            File f = new File(loc.toURI());
            return f.isFile() ? f.getParentFile() : f;
        } catch (URISyntaxException | SecurityException e) {
            return null;
        }
    }

    /** 指定パスが存在する/読める File を返す。問題があれば stderr に出して System.exit(1)。 */
    private static File requireReadable(File f) {
        if (f == null) {
            return null;
        }
        if (!f.exists()) {
            System.err.println("Input not found: " + f.getPath());
            System.exit(1);
        }
        if (!f.canRead()) {
            System.err.println("Cannot read: " + f.getPath());
            System.exit(1);
        }
        return f;
    }

    /**
     * エントリポイント
     * @param args 引数
     */
    public static void main(String[] args) throws IOException {
        // 専用サブコマンドの早期分岐 (option parser を経由しない)。
        // 既存の "java -jar PadTools.jar -c <path>" のような呼び出しは args[0] が "-c"
        // 等のオプション or 入力パスになるので、ここでは "index" だけを intercept する。
        if (args.length > 0 && "index".equals(args[0])) {
            int code = padtools.app.cli.IndexCommand.execute(
                    args, padtools.util.ErrorListener.stderr());
            if (code != 0) {
                System.exit(code);
            }
            return;
        }

        // SettingManager / ProjectRepository を初期化し、永続化されたスタイルをレンダラへ反映
        SettingManager.initialize();
        ProjectRepository.initialize();
        PlantUmlRenderer.setStyle(SettingManager.getInstance().getSetting().getStyle());
        GraphvizLocator.init(detectJarDir());

        //オプション定義
        final Option optHelp = new Option("h", "help", false);
        final Option optOut = new Option("o", "output", true);
        final Option optClassDiagram = new Option("c", "class-diagram", false);
        final Option optSequenceDiagram = new Option("q", "sequence-diagram", true);
        final Option optVerbose = new Option("v", "verbose", false);
        final Option optLegend = new Option("L", "legend", false);
        final Option optNoLegend = new Option(null, "no-legend", false);
        final Option optGradle = new Option("g", "gradle", false);
        final Option optManifest = new Option("m", "manifest", false);
        final Option optComponent = new Option("d", "component-diagram", false);
        final Option optManifestDiagram = new Option("M", "manifest-diagram", false);
        final Option optDeepLinkDiagram = new Option("D", "deeplink-diagram", false);
        final Option optDepGraph = new Option("G", "dependency-graph", false);
        final Option optSummary = new Option(null, "summary", false);
        final Option optAll = new Option("A", "all", false);
        final Option optNoManifestMerge = new Option(null, "no-manifest-merge", false);
        final Option optNoComments = new Option(null, "no-comments", false);
        final Option optCommentStyle = new Option(null, "comment-style", true);
        final Option optNoAnnotations = new Option(null, "no-annotations", false);
        final Option optNoEnumConstants = new Option(null, "no-enum-constants", false);
        final Option optNoFinal = new Option(null, "no-final", false);
        final Option optListMethods = new Option(null, "list-methods", false);
        final Option optSeqDepth = new Option(null, "seq-depth", true);
        final Option optSequenceDiagrams = new Option("Q", "sequence-diagrams", false);
        final Option optJetpack = new Option(null, "jetpack", false);
        final Option optPerFolder = new Option("P", "per-folder", false);
        final Option optPreset = new Option(null, "preset", true);
        final Option optNoFields = new Option(null, "no-fields", false);
        final Option optNoMethods = new Option(null, "no-methods", false);
        final Option optPublicOnly = new Option(null, "public-only", false);
        final Option optExcludeExternal = new Option(null, "exclude-external", false);
        final Option optExcludePackage = new Option(null, "exclude-package", true);
        final Option optRelation = new Option(null, "relation", true);
        final Option optMode = new Option(null, "mode", true);
        final Option optInteractiveSvg = new Option(null, "interactive-svg", false);
        final Option optHiddenAnnotations = new Option(null, "hidden-annotations", true);
        final Option optCommentMaxLength = new Option(null, "comment-max-length", true);
        final Option optImpact = new Option(null, "impact", true);
        final Option optImpactDepth = new Option(null, "impact-depth", true);
        final Option optRefFind = new Option(null, "ref-find", true);
        final Option optVhalFlow = new Option(null, "vhal-flow", false);
        final Option optAidlBinding = new Option(null, "aidl-binding", false);
        final Option optErDiagram = new Option(null, "er-diagram", false);
        final Option optDataFlow = new Option(null, "data-flow", false);
        final Option optScreenFlow = new Option(null, "screen-flow", false);
        final Option optAndroidBp = new Option(null, "android-bp", false);
        final Option optSelinux = new Option(null, "selinux", false);
        final Option optRro = new Option(null, "rro-overlays", false);
        final Option optSettings = new Option(null, "settings", false);
        final Option optInitFlow = new Option(null, "init-flow", false);
        final Option optActionMap = new Option(null, "action-map", false);
        final Option optFuncDiff = new Option(null, "func-diff", true);

        final OptionParser optParser = new OptionParser(new Option[]{
                optHelp, optOut,
                optClassDiagram, optSequenceDiagram, optVerbose,
                optLegend, optNoLegend,
                optGradle, optManifest, optComponent, optManifestDiagram,
                optDeepLinkDiagram, optDepGraph, optSummary,
                optAll, optNoManifestMerge,
                optNoComments, optCommentStyle, optNoAnnotations,
                optNoEnumConstants, optNoFinal,
                optListMethods, optSeqDepth,
                optSequenceDiagrams, optJetpack, optPerFolder,
                optPreset, optNoFields, optNoMethods, optPublicOnly,
                optExcludeExternal, optExcludePackage, optRelation, optMode,
                optInteractiveSvg, optHiddenAnnotations, optCommentMaxLength,
                optImpact, optImpactDepth, optRefFind,
                optVhalFlow, optAidlBinding, optErDiagram, optDataFlow,
                optScreenFlow, optAndroidBp, optSelinux, optRro,
                optSettings, optInitFlow, optActionMap,
                optFuncDiff});

        try {
            optParser.parse(args);
        } catch (UnknownOptionException ex) {
            System.err.println("Unknown option: " + ex.getOption());
            System.exit(1);
        }

        if (optHelp.isSet()) {
            printUsage();
            System.exit(1);
        }

        File file_in;
        if (optParser.getArguments().isEmpty()) {
            file_in = null;
        } else {
            file_in = requireReadable(new File(optParser.getArguments().getFirst()));
        }

        File file_out;
        if (optOut.getArguments().isEmpty()) {
            file_out = null;
        } else {
            file_out = new File(optOut.getArguments().getLast());
        }

        ErrorListener listener = optVerbose.isSet()
                ? ErrorListener.stderr() : ErrorListener.silent();
        // PlantUML 同梱 Smetana が直接 stderr に書く UNSURE_ABOUT 等のデバッグ出力を
        // 既定で抑制する。-v 時は素通しさせて Smetana 内部のログも見えるようにする。
        PlantUmlRenderer.setVerbose(optVerbose.isSet());
        // UML 凡例は既定 ON。明示的な --legend / --no-legend で上書き可。
        Boolean legendOverride = null;
        if (optLegend.isSet()) {
            legendOverride = Boolean.TRUE;
        } else if (optNoLegend.isSet()) {
            legendOverride = Boolean.FALSE;
        }
        boolean mergeManifest = !optNoManifestMerge.isSet();
        UmlOverrides umlOverrides = UmlOverrides.build(
                optNoComments, optNoAnnotations, optNoEnumConstants,
                optNoFinal, optCommentStyle, optSeqDepth, optJetpack,
                optPreset, optNoFields, optNoMethods, optPublicOnly,
                optExcludeExternal, optExcludePackage, optRelation, optMode,
                optInteractiveSvg, optHiddenAnnotations, optCommentMaxLength);
        if (umlOverrides == null) {
            return; // 引数エラー: UmlOverrides.build 内で System.exit 済み
        }
        CliContext ctx = new CliContext(file_in, file_out, listener,
                legendOverride, mergeManifest, umlOverrides);

        if (optListMethods.isSet()) {
            UmlCommands.handleListMethods(ctx);
            return;
        }
        if (optSequenceDiagrams.isSet()) {
            UmlCommands.handleSequenceDiagrams(ctx);
            return;
        }
        if (optAll.isSet()) {
            AndroidCommands.handleAll(ctx);
            return;
        }
        if (optGradle.isSet()) {
            AndroidCommands.handleGradleInput(ctx);
            return;
        }
        if (optManifest.isSet()) {
            AndroidCommands.handleManifestInput(ctx);
            return;
        }
        if (optComponent.isSet()) {
            AndroidCommands.handleComponentDiagram(ctx);
            return;
        }
        if (optManifestDiagram.isSet()) {
            AndroidCommands.handleManifestDiagram(ctx);
            return;
        }
        if (optDeepLinkDiagram.isSet()) {
            AndroidCommands.handleDeepLinkDiagram(ctx);
            return;
        }
        if (optDepGraph.isSet()) {
            AndroidCommands.handleDependencyGraph(ctx);
            return;
        }
        if (optSummary.isSet()) {
            AndroidCommands.handleSummary(ctx);
            return;
        }
        if (optImpact.isSet()) {
            int depth = 3;
            if (!optImpactDepth.getArguments().isEmpty()) {
                try {
                    depth = Integer.parseInt(optImpactDepth.getArguments().getLast());
                } catch (NumberFormatException ex) {
                    System.err.println("--impact-depth must be an integer");
                    System.exit(1);
                }
            }
            AnalysisCommands.handleImpact(ctx, optImpact.getArguments().getLast(), depth);
            return;
        }
        if (optRefFind.isSet()) {
            AnalysisCommands.handleRefFind(ctx, optRefFind.getArguments().getLast());
            return;
        }
        if (optVhalFlow.isSet()) {
            AospCommands.handleVhalFlow(ctx);
            return;
        }
        if (optAidlBinding.isSet()) {
            AospCommands.handleAidlBinding(ctx);
            return;
        }
        if (optErDiagram.isSet()) {
            AnalysisCommands.handleErDiagram(ctx);
            return;
        }
        if (optDataFlow.isSet()) {
            AnalysisCommands.handleDataFlow(ctx);
            return;
        }
        if (optScreenFlow.isSet()) {
            AnalysisCommands.handleScreenFlow(ctx);
            return;
        }
        if (optAndroidBp.isSet()) {
            AospCommands.handleAndroidBp(ctx);
            return;
        }
        if (optSelinux.isSet()) {
            AospCommands.handleSelinux(ctx);
            return;
        }
        if (optRro.isSet()) {
            AospCommands.handleRroOverlays(ctx);
            return;
        }
        if (optSettings.isSet()) {
            AndroidCommands.handleSettings(ctx);
            return;
        }
        if (optInitFlow.isSet()) {
            UmlCommands.handleInitFlow(ctx);
            return;
        }
        if (optActionMap.isSet()) {
            AndroidCommands.handleActionMap(ctx);
            return;
        }
        if (optFuncDiff.isSet()) {
            AnalysisCommands.handleFuncDiff(ctx, optFuncDiff.getArguments().getLast());
            return;
        }
        if (optClassDiagram.isSet() && optPerFolder.isSet()) {
            UmlCommands.handleClassDiagramsPerFolder(ctx);
            return;
        }
        if (optClassDiagram.isSet() || optSequenceDiagram.isSet()) {
            UmlCommands.handleUmlInput(ctx,
                    optClassDiagram.isSet(),
                    optSequenceDiagram.isSet()
                            ? optSequenceDiagram.getArguments().getLast() : null);
            return;
        }

        if (file_out != null) {
            System.err.println("-o requires one of: -c / -q / -d / -G / -g / -m / -M / -D"
                    + " / --summary / -A / -Q / --list-methods");
            System.exit(1);
            return;
        }
        // 既定: UML 専用 GUI を起動。引数があれば初期プロジェクトとして渡す。
        UmlApp.launch(file_in);
    }

    private static void printUsage() {
        System.err.println(
                "Arguments: [-o file] [-c|-q M|-d|-M|-G|-g|-m|-Q|-A|--summary] [-v] [-h] [input]");
        System.err.println("  No arguments / [input dir]: Launch UML GUI for the project.");
        System.err.println("  -o file: Save diagram (puml/svg) or report (md) to file.");
        System.err.println("  -h: Show this help.");
        System.err.println("  -c --class-diagram: Output PlantUML class diagram.");
        System.err.println("  -q --sequence-diagram Class.method: PlantUML sequence diagram.");
        System.err.println("  -v --verbose: Emit per-file warnings and summary to stderr.");
        System.err.println("  -L --legend: Force include legend (default on for UML).");
        System.err.println("  --no-legend: Force exclude legend.");
        System.err.println("  -g --gradle: Output Markdown summary from build.gradle.");
        System.err.println("  -m --manifest: Output Markdown summary from AndroidManifest.xml.");
        System.err.println("  -d --component-diagram: PlantUML Android component diagram.");
        System.err.println("  -M --manifest-diagram: "
                + "PlantUML AndroidManifest diagram (Application + components).");
        System.err.println("  -D --deeplink-diagram: "
                + "PlantUML Deep Link / App Links diagram (VIEW + BROWSABLE filters).");
        System.err.println("  -G --dependency-graph: PlantUML Gradle dependency graph.");
        System.err.println("  --summary: Full project Markdown summary (dir).");
        System.err.println("  -A --all: Output ALL artifacts as SVG "
                + "(summary.md + svg files) to the directory specified by -o.");
        System.err.println("  --no-manifest-merge: Disable manifest auto-merge in class diagram.");
        System.err.println("  --no-comments: Disable JavaDoc/comment rendering"
                + " in class & sequence diagrams.");
        System.err.println("  --comment-style inline|note: "
                + "Choose comment placement for class & sequence diagrams (default: inline).");
        System.err.println("  --no-annotations: Disable @annotation rendering in class diagram.");
        System.err.println("  --no-enum-constants: Disable enum constant rendering.");
        System.err.println("  --no-final: Disable {final} marker on final fields.");
        System.err.println("  --list-methods: List Class.method candidates for use with -q.");
        System.err.println("  --seq-depth N: Sequence trace depth limit (default 5, 0=unlimited).");
        System.err.println("  -Q --sequence-diagrams: Output PlantUML sequence diagrams"
                + " (.puml + .svg) for Android lifecycle entry points to the directory specified by -o.");
        System.err.println("  --jetpack: Enable Jetpack stereotypes (Fragment / ViewModel /"
                + " Hilt etc.) on class diagram (-c / -A).");
        System.err.println("  -P --per-folder: With -c: write one class diagram (.puml + .svg)"
                + " per source folder into the -o directory (preserves subfolder layout).");
        System.err.println("  --preset minimal|balanced|detailed: "
                + "Apply a readability preset to the class diagram (default: balanced).");
        System.err.println("  --no-fields: Hide fields in the class diagram.");
        System.err.println("  --no-methods: Hide methods in the class diagram.");
        System.err.println("  --public-only: Show only public classes and members.");
        System.err.println("  --exclude-external: Exclude classes from java.*, android.*,"
                + " kotlin.* and other external libraries (Origin + prefix).");
        System.err.println("  --exclude-package PREFIX: Exclude classes whose package"
                + " matches PREFIX (may be specified multiple times).");
        System.err.println("  --relation inherit,impl,use: Limit relation lines to the"
                + " listed kinds (CSV).");
        System.err.println("  --mode headers-only|full: Use the lightweight headers-only"
                + " parse mode for projects (default: full).");
        System.err.println("  --interactive-svg: Embed clickable class hyperlinks in"
                + " the generated SVG (padtools://class/...).");
        System.err.println("  --hidden-annotations CSV: Override the list of hidden"
                + " annotation names (comma-separated).");
        System.err.println("  --comment-max-length N: Maximum inline comment length"
                + " (0 disables comments).");
        System.err.println("  --impact SYMBOL: Show reverse-reference impact for a class"
                + " FQN or FQN.method (Markdown + PlantUML).");
        System.err.println("  --impact-depth N: BFS depth for --impact (default 3).");
        System.err.println("  --ref-find SYMBOL: Print every reference site of a class"
                + " FQN or FQN.method (plain text, grep-friendly).");
        System.err.println("  --vhal-flow: Detect CarPropertyManager get/set/subscribe"
                + " usage in the project and emit Markdown + PlantUML flow.");
        System.err.println("  --aidl-binding: List AIDL interfaces and the classes that"
                + " extend their Stub (Markdown table).");
        System.err.println("  --er-diagram: Generate a PlantUML ER diagram for Room"
                + " @Entity classes grouped by @Database.");
        System.err.println("  --data-flow: Markdown report + ER diagram covering Room"
                + " @Entity / @Dao / @Database in the project.");
        System.err.println("  --screen-flow: Intent-based screen transitions"
                + " (startActivity / setClass) as Markdown + PlantUML state diagram.");
        System.err.println("  --android-bp: Parse all Android.bp (Soong) files under"
                + " the project and emit module inventory + dependency graph"
                + " (Markdown + PlantUML).");
        System.err.println("  --selinux: Parse all *.te SELinux policy files under"
                + " the project (type declarations, allow/neverallow rules,"
                + " Markdown report).");
        System.err.println("  --rro-overlays: Detect Android Runtime Resource Overlays"
                + " by scanning AndroidManifest.xml for <overlay> elements.");
        System.err.println("  --settings: Scan SharedPreferences get*/put* calls and"
                + " res/xml/ Preference XML to report keys, types, and access locations"
                + " (Markdown).");
        System.err.println("  --init-flow: Generate PlantUML sequence diagrams for"
                + " Application subclass onCreate / onConfigurationChanged / onLowMemory"
                + " (output dir required via -o).");
        System.err.println("  --action-map: Detect onClick handlers (setOnClickListener,"
                + " android:onClick, Compose onClick) and menu handlers, output as"
                + " Markdown table.");
        System.err.println("  input: Java/AIDL file or Gradle/Android project directory.");
    }
}
