package padtools;

import padtools.app.uml.UmlApp;
import padtools.core.formats.android.AndroidProjectAnalysis;
import padtools.core.formats.android.AndroidProjectAnalyzer;
import padtools.core.formats.android.AndroidManifestInfo;
import padtools.core.formats.android.AndroidManifestParser;
import padtools.core.formats.android.GradleProjectInfo;
import padtools.core.formats.android.GradleScriptParser;
import padtools.core.formats.android.PlantUmlComponentDiagram;
import padtools.core.formats.android.PlantUmlDeepLinkDiagram;
import padtools.core.formats.android.PlantUmlGradleDependencyGraph;
import padtools.core.formats.android.PlantUmlManifestDiagram;
import padtools.core.formats.android.TextSummaryReport;
import padtools.core.formats.java.AndroidProjectScanner;
import padtools.core.formats.uml.LifecycleSequenceDiagrams;
import padtools.core.formats.uml.PlantUmlRenderer;
import padtools.core.formats.uml.UmlGenerator;
import padtools.core.aaos.AidlBinding;
import padtools.core.aaos.AidlBindingResolver;
import padtools.core.aaos.MarkdownAidlBindingReport;
import padtools.core.aaos.MarkdownVhalReport;
import padtools.core.aaos.PlantUmlVhalFlowDiagram;
import padtools.core.aaos.VehiclePropertyCatalog;
import padtools.core.aaos.VhalAccess;
import padtools.core.aaos.VhalAnalyzer;
import padtools.core.impact.ImpactAnalyzer;
import padtools.core.impact.ImpactGraph;
import padtools.core.impact.MarkdownImpactReport;
import padtools.core.impact.PlantUmlImpactDiagram;
import padtools.core.refs.ReferenceIndex;
import padtools.core.refs.ReferenceIndexBuilder;
import padtools.core.refs.ReferenceKey;
import padtools.core.refs.ReferenceSite;
import padtools.util.ErrorListener;
import padtools.util.Option;
import padtools.util.OptionParser;
import padtools.util.UnknownOptionException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

/**
 * エントリポイントクラス
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
        // SettingManager を初期化し、永続化されたスタイルをレンダラへ反映
        SettingManager.initialize();
        PlantUmlRenderer.setStyle(SettingManager.getInstance().getSetting().getStyle());

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
                optVhalFlow, optAidlBinding});

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
        if (optListMethods.isSet()) {
            handleListMethods(file_in, file_out, listener);
            return;
        }
        if (optSequenceDiagrams.isSet()) {
            handleSequenceDiagrams(file_in, file_out, listener,
                    legendOverride, mergeManifest, umlOverrides);
            return;
        }
        if (optAll.isSet()) {
            handleAll(file_in, file_out, listener, legendOverride, mergeManifest,
                    umlOverrides);
            return;
        }
        if (optGradle.isSet()) {
            handleGradleInput(file_in, file_out, listener);
            return;
        }
        if (optManifest.isSet()) {
            handleManifestInput(file_in, file_out, listener);
            return;
        }
        if (optComponent.isSet()) {
            handleComponentDiagram(file_in, file_out, listener, legendOverride);
            return;
        }
        if (optManifestDiagram.isSet()) {
            handleManifestDiagram(file_in, file_out, listener, legendOverride);
            return;
        }
        if (optDeepLinkDiagram.isSet()) {
            handleDeepLinkDiagram(file_in, file_out, listener, legendOverride);
            return;
        }
        if (optDepGraph.isSet()) {
            handleDependencyGraph(file_in, file_out, listener, legendOverride);
            return;
        }
        if (optSummary.isSet()) {
            handleSummary(file_in, file_out, listener);
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
            handleImpact(file_in, file_out, optImpact.getArguments().getLast(),
                    depth, listener);
            return;
        }
        if (optRefFind.isSet()) {
            handleRefFind(file_in, file_out, optRefFind.getArguments().getLast(),
                    listener);
            return;
        }
        if (optVhalFlow.isSet()) {
            handleVhalFlow(file_in, file_out, listener);
            return;
        }
        if (optAidlBinding.isSet()) {
            handleAidlBinding(file_in, file_out, listener);
            return;
        }
        if (optClassDiagram.isSet() && optPerFolder.isSet()) {
            handleClassDiagramsPerFolder(file_in, file_out, listener,
                    legendOverride, mergeManifest, umlOverrides);
            return;
        }
        if (optClassDiagram.isSet() || optSequenceDiagram.isSet()) {
            handleUmlInput(file_in, file_out,
                    optClassDiagram.isSet(),
                    optSequenceDiagram.isSet()
                            ? optSequenceDiagram.getArguments().getLast() : null,
                    listener, legendOverride, mergeManifest, umlOverrides);
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

    private static void writeText(File f, String content) throws IOException {
        if (f == null) {
            // System.out の既定エンコーディングに依存しないよう UTF-8 で明示出力
            System.out.write(content.getBytes(StandardCharsets.UTF_8));
            System.out.flush();
            return;
        }
        try (Writer w = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) {
            w.write(content);
        }
    }

    /**
     * PlantUML 系出力の書き出し。{@code fileOut} の拡張子が {@code .svg} なら
     * 同梱 PlantUML で SVG にレンダリングし、それ以外 (null や .puml/.txt) は
     * PlantUML テキストをそのまま書き出す (標準出力可)。
     */
    private static void writeUmlOutput(File fileOut, String puml) throws IOException {
        if (fileOut != null && fileOut.getName().toLowerCase().endsWith(".svg")) {
            try {
                PlantUmlRenderer.renderSvg(puml, fileOut);
            } catch (padtools.core.formats.uml.PlantUmlRenderFailedException ex) {
                File pumlFallback = siblingPumlFor(fileOut);
                writeText(pumlFallback, puml);
                System.err.println("[padtools] " + fileOut.getName()
                        + " FAILED: " + ex.getMessage());
                System.err.println("[padtools]    Saved " + pumlFallback.getPath()
                        + " -- render externally with: plantuml -tsvg "
                        + pumlFallback.getName());
                System.exit(2);
            }
        } else {
            writeText(fileOut, puml);
        }
    }

    /** 与えられた SVG ファイルと同じ親ディレクトリ・同じベース名で {@code .puml} を指す
     * ファイル オブジェクトを返す。フォールバック保存先として使う。 */
    private static File siblingPumlFor(File svgFile) {
        String name = svgFile.getName();
        int dot = name.lastIndexOf('.');
        String base = dot >= 0 ? name.substring(0, dot) : name;
        File parent = svgFile.getParentFile();
        if (parent == null) {
            return new File(base + ".puml");
        }
        return new File(parent, base + ".puml");
    }

    /** {@code --all} 内で 1 つの SVG をレンダリングする。失敗時はサイドカー puml に
     * フォールバックして「FAILED」ログを出し、次の図に進む。
     * @return レンダリングが成功したかどうか
     */
    private static boolean renderSvgOrFallback(String puml, File svgFile,
                                                 ProgressLogger progress,
                                                 ErrorListener listener) throws IOException {
        try {
            PlantUmlRenderer.renderSvg(puml, svgFile);
            progress.wrote(svgFile);
            listener.onError(null, -1, "wrote " + svgFile.getPath());
            return true;
        } catch (padtools.core.formats.uml.PlantUmlRenderFailedException ex) {
            File pumlFallback = siblingPumlFor(svgFile);
            writeText(pumlFallback, puml);
            System.err.println("[padtools]     -> " + svgFile.getName()
                    + " FAILED: " + ex.getMessage());
            System.err.println("[padtools]        Saved " + pumlFallback.getName()
                    + " -- render externally with: plantuml -tsvg "
                    + pumlFallback.getName());
            return false;
        }
    }

    /**
     * Java/AIDL ソースから PlantUML (クラス図 / シーケンス図) を生成。
     * @param fileIn 入力ファイルまたはディレクトリ
     * @param fileOut 出力ファイル (.puml/.plantuml/.txt)。null なら標準出力
     * @param classDiagram true でクラス図モード
     * @param sequenceEntry "Class.method" 形式のエントリ。null/空ならシーケンス図モードを無効化
     */
    private static void handleUmlInput(File fileIn, File fileOut,
                                        boolean classDiagram,
                                        String sequenceEntry,
                                        ErrorListener listener,
                                        Boolean legendOverride,
                                        boolean mergeManifest,
                                        UmlOverrides overrides) throws IOException {
        if (fileIn == null) {
            System.err.println("UML generation requires an input file or directory.");
            System.exit(1);
            return;
        }
        String spec = sequenceEntry == null ? "" : sequenceEntry.trim();
        java.util.List<padtools.core.formats.uml.JavaClassInfo> infos;
        UmlGenerator.ParseMode parseMode = overrides != null
                ? overrides.parseMode : UmlGenerator.ParseMode.FULL;
        if (fileIn.isDirectory()) {
            if (parseMode == UmlGenerator.ParseMode.HEADERS_ONLY) {
                infos = new java.util.ArrayList<>(UmlGenerator.extractFromProjectDetailed(
                        fileIn, null, listener, null, null,
                        mergeManifest, parseMode).getClasses());
            } else {
                infos = UmlGenerator.extractFromProject(fileIn, null, listener, mergeManifest);
            }
        } else {
            String src = AndroidProjectScanner.readFile(fileIn);
            if (parseMode == UmlGenerator.ParseMode.HEADERS_ONLY) {
                infos = UmlGenerator.extractHeadersFromSource(src, fileIn.getName(), listener);
            } else {
                infos = UmlGenerator.extractFromSource(src, fileIn.getName(), listener);
            }
        }
        // クラス図モードで --exclude-package が指定されていれば、ここで除外を効かせる。
        if (classDiagram && overrides != null
                && overrides.excludedPackages != null
                && !overrides.excludedPackages.isEmpty()) {
            java.util.List<padtools.core.formats.uml.JavaClassInfo> filtered =
                    new java.util.ArrayList<>(infos.size());
            for (padtools.core.formats.uml.JavaClassInfo c : infos) {
                String pkg = c.getPackageName() == null ? "" : c.getPackageName();
                boolean drop = false;
                for (String ex : overrides.excludedPackages) {
                    if (pkg.equals(ex) || pkg.startsWith(ex + ".")) {
                        drop = true;
                        break;
                    }
                }
                if (!drop) {
                    filtered.add(c);
                }
            }
            infos = filtered;
        }

        String output;
        if (sequenceEntry != null && !spec.isEmpty()) {
            int dot = spec.lastIndexOf('.');
            if (dot < 0) {
                System.err.println("Sequence entry must be in 'Class.method' format: " + spec);
                System.exit(1);
                return;
            }
            String entryClass = spec.substring(0, dot);
            String entryMethod = spec.substring(dot + 1);
            padtools.core.formats.uml.PlantUmlSequenceDiagram.Options sqOpts
                    = new padtools.core.formats.uml.PlantUmlSequenceDiagram.Options();
            if (Boolean.FALSE.equals(legendOverride)) {
                sqOpts.includeLegend = false;
            }
            if (overrides != null) {
                overrides.applyTo(sqOpts);
                if (overrides.seqDepth != null) {
                    sqOpts.maxDepth = overrides.seqDepth;
                }
            }
            output = padtools.core.formats.uml.PlantUmlSequenceDiagram.generate(
                    infos, entryClass, entryMethod, sqOpts);
        } else {
            padtools.core.formats.uml.PlantUmlClassDiagram.Options clOpts
                    = new padtools.core.formats.uml.PlantUmlClassDiagram.Options();
            if (Boolean.FALSE.equals(legendOverride)) {
                clOpts.includeLegend = false;
            }
            if (overrides != null) {
                overrides.applyTo(clOpts);
            }
            output = padtools.core.formats.uml.PlantUmlClassDiagram.generate(infos, clOpts);
        }
        writeUmlOutput(fileOut, output);
    }

    /**
     * {@code --list-methods}: 入力ソース内のメソッドを列挙し、{@code Class.method} 形式で
     * 1 行ずつ stdout (もしくは {@code -o} 指定先) に書き出す。シーケンス図の起点を
     * シェルから選ぶ用途 (fzf, peco 等と組み合わせ) を想定。
     */
    private static void handleListMethods(File fileIn, File fileOut,
                                            ErrorListener listener) throws IOException {
        if (fileIn == null) {
            System.err.println("--list-methods requires an input file or directory.");
            System.exit(1);
            return;
        }
        java.util.List<padtools.core.formats.uml.JavaClassInfo> infos;
        if (fileIn.isDirectory()) {
            infos = UmlGenerator.extractFromProject(fileIn, null, listener);
        } else {
            String src = AndroidProjectScanner.readFile(fileIn);
            infos = UmlGenerator.extractFromSource(src, fileIn.getName(), listener);
        }
        java.util.List<padtools.core.formats.uml.PlantUmlSequenceDiagram.Candidate> candidates =
                padtools.core.formats.uml.PlantUmlSequenceDiagram.listCandidates(infos);
        StringBuilder sb = new StringBuilder();
        for (padtools.core.formats.uml.PlantUmlSequenceDiagram.Candidate c : candidates) {
            sb.append(c.getEntry())
                    .append("\t(").append(c.callCount).append(" call")
                    .append(c.callCount == 1 ? "" : "s")
                    .append(", ").append(c.visibility.name().toLowerCase()).append(")\n");
        }
        writeText(fileOut, sb.toString());
    }

    /**
     * {@code --impact <FQN[.method]>}: 指定シンボルを起点に逆参照を辿り、
     * 影響を受けるクラスを推移閉包で列挙する。
     *
     * <p>出力先:</p>
     * <ul>
     *   <li>{@code -o foo.md}: Markdown レポート 1 ファイル</li>
     *   <li>{@code -o foo.puml}: PlantUML 影響図 1 ファイル</li>
     *   <li>{@code -o foo}: 拡張子なし → {@code foo.md} と {@code foo.puml} を両方書く</li>
     *   <li>{@code -o} 省略: Markdown を標準出力</li>
     * </ul>
     */
    private static void handleImpact(File fileIn, File fileOut, String target,
                                       int depth, ErrorListener listener)
            throws IOException {
        if (fileIn == null || !fileIn.isDirectory()) {
            System.err.println("--impact requires a project directory as input.");
            System.exit(1);
            return;
        }
        if (target == null || target.isEmpty()) {
            System.err.println("--impact requires a symbol target (FQN or FQN.method).");
            System.exit(1);
            return;
        }
        ReferenceIndex refIndex = buildReferenceIndex(fileIn, listener);

        ImpactGraph graph;
        ImpactAnalyzer analyzer = new ImpactAnalyzer(refIndex);
        // FQN.method の区別: 最後のドット以降が小文字始まりかつクラスが存在 → method
        int lastDot = target.lastIndexOf('.');
        if (lastDot > 0 && lastDot < target.length() - 1) {
            String maybeOwner = target.substring(0, lastDot);
            String maybeMember = target.substring(lastDot + 1);
            if (!maybeMember.isEmpty()
                    && Character.isLowerCase(maybeMember.charAt(0))) {
                graph = analyzer.analyzeMethod(maybeOwner, maybeMember, depth);
            } else {
                graph = analyzer.analyzeClass(target, depth);
            }
        } else {
            graph = analyzer.analyzeClass(target, depth);
        }

        String markdown = MarkdownImpactReport.render(graph);
        String puml = PlantUmlImpactDiagram.render(graph);
        writeImpactOutput(fileOut, markdown, puml);
    }

    /**
     * {@code --ref-find <FQN[.member]>}: シンボルを参照している箇所を 1 行ずつ
     * プレーンテキストで列挙する (grep 互換)。
     */
    private static void handleRefFind(File fileIn, File fileOut, String target,
                                        ErrorListener listener) throws IOException {
        if (fileIn == null || !fileIn.isDirectory()) {
            System.err.println("--ref-find requires a project directory as input.");
            System.exit(1);
            return;
        }
        if (target == null || target.isEmpty()) {
            System.err.println("--ref-find requires a symbol target.");
            System.exit(1);
            return;
        }
        ReferenceIndex refIndex = buildReferenceIndex(fileIn, listener);

        java.util.List<ReferenceSite> sites;
        int lastDot = target.lastIndexOf('.');
        if (lastDot > 0 && lastDot < target.length() - 1) {
            String maybeOwner = target.substring(0, lastDot);
            String maybeMember = target.substring(lastDot + 1);
            if (!maybeMember.isEmpty()
                    && Character.isLowerCase(maybeMember.charAt(0))) {
                sites = refIndex.sites(
                        ReferenceKey.ofMethod(maybeOwner, maybeMember));
            } else {
                sites = refIndex.sitesForClass(target);
            }
        } else {
            sites = refIndex.sitesForClass(target);
        }

        StringBuilder sb = new StringBuilder();
        for (ReferenceSite s : sites) {
            sb.append(s.getCallerFqn());
            if (!s.getCallerMethod().isEmpty()) {
                sb.append('.').append(s.getCallerMethod());
            }
            sb.append('\t').append(s.getKind().name());
            if (!s.getFile().isEmpty()) {
                sb.append('\t').append(s.getFile());
            }
            sb.append('\n');
        }
        if (sb.length() == 0) {
            sb.append("(no references found for ").append(target).append(")\n");
        }
        writeText(fileOut, sb.toString());
    }

    /**
     * プロジェクトをパースして {@link ReferenceIndex} を構築する。
     * Stage B 化されたクラスをすべて {@link ReferenceIndexBuilder} に流す。
     */
    private static ReferenceIndex buildReferenceIndex(File projectDir,
                                                        ErrorListener listener)
            throws IOException {
        UmlGenerator.ProjectParseResult result =
                UmlGenerator.extractFromProjectDetailed(projectDir, null, listener,
                        null, null, false, UmlGenerator.ParseMode.FULL);
        ReferenceIndex idx = new ReferenceIndex();
        ReferenceIndexBuilder builder = new ReferenceIndexBuilder(idx,
                result.getIndex(), result.getDependencyIndex(), listener);
        builder.addAll(result.getClasses());
        return idx;
    }

    /**
     * {@code --vhal-flow}: プロジェクトを走査して
     * {@link padtools.core.aaos.CarPropertyManager} 系呼び出しを検出し、
     * Property 別 GET/SET/SUBSCRIBE フローを Markdown + PlantUML で出力する。
     */
    private static void handleVhalFlow(File fileIn, File fileOut,
                                          ErrorListener listener) throws IOException {
        if (fileIn == null || !fileIn.isDirectory()) {
            System.err.println("--vhal-flow requires a project directory as input.");
            System.exit(1);
            return;
        }
        VhalAnalyzer analyzer = new VhalAnalyzer();
        java.util.List<VhalAccess> accesses = analyzer.analyzeProject(fileIn);
        VehiclePropertyCatalog catalog = VehiclePropertyCatalog.scanProject(fileIn);
        String md = MarkdownVhalReport.render(accesses, catalog);
        String puml = PlantUmlVhalFlowDiagram.render(accesses);
        writeImpactOutput(fileOut, md, puml);
    }

    /**
     * {@code --aidl-binding}: プロジェクト内の AIDL インタフェースと、その
     * {@code Stub} を継承する実装クラスとの対応表を Markdown で出力する。
     */
    private static void handleAidlBinding(File fileIn, File fileOut,
                                            ErrorListener listener) throws IOException {
        if (fileIn == null || !fileIn.isDirectory()) {
            System.err.println("--aidl-binding requires a project directory as input.");
            System.exit(1);
            return;
        }
        UmlGenerator.ProjectParseResult result =
                UmlGenerator.extractFromProjectDetailed(fileIn, null, listener,
                        null, null, false, UmlGenerator.ParseMode.FULL);
        java.util.Map<String, java.util.List<AidlBinding>> bindings =
                new AidlBindingResolver().resolve(result.getClasses());
        String md = MarkdownAidlBindingReport.render(bindings);
        writeText(fileOut, md);
    }

    /**
     * Impact レポートの書き出し。出力先の拡張子を見て .md / .puml / 両方を切り替える。
     */
    private static void writeImpactOutput(File fileOut, String markdown, String puml)
            throws IOException {
        if (fileOut == null) {
            writeText(null, markdown);
            return;
        }
        String name = fileOut.getName().toLowerCase();
        if (name.endsWith(".md") || name.endsWith(".markdown")) {
            writeText(fileOut, markdown);
        } else if (name.endsWith(".puml") || name.endsWith(".plantuml")) {
            writeText(fileOut, puml);
        } else if (name.endsWith(".svg")) {
            writeUmlOutput(fileOut, puml);
        } else {
            // 拡張子なし: 同じディレクトリ・同じベース名で .md と .puml を両方書く
            File parent = fileOut.getParentFile();
            String base = fileOut.getName();
            int dot = base.lastIndexOf('.');
            if (dot >= 0) {
                base = base.substring(0, dot);
            }
            File mdFile = parent == null ? new File(base + ".md")
                    : new File(parent, base + ".md");
            File pumlFile = parent == null ? new File(base + ".puml")
                    : new File(parent, base + ".puml");
            writeText(mdFile, markdown);
            writeText(pumlFile, puml);
        }
    }

    /**
     * {@code --sequence-diagrams} / {@code -Q}: Android プロジェクトを入力に、
     * Activity/Service/Receiver/Provider のライフサイクルメソッドを起点とする
     * PlantUML シーケンス図を {@code -o} で指定されたディレクトリへ一括出力する。
     * 各起点について {@code Class.method.puml} と {@code Class.method.svg} の両方を書き出す。
     */
    private static void handleSequenceDiagrams(File fileIn, File fileOut,
                                                 ErrorListener listener,
                                                 Boolean legendOverride,
                                                 boolean mergeManifest,
                                                 UmlOverrides overrides) throws IOException {
        if (fileIn == null || !fileIn.isDirectory()) {
            System.err.println("--sequence-diagrams requires a project directory.");
            System.exit(1);
            return;
        }
        if (fileOut == null) {
            System.err.println("--sequence-diagrams requires an output directory via -o.");
            System.exit(1);
            return;
        }
        if (!fileOut.exists() && !fileOut.mkdirs()) {
            System.err.println("Failed to create output directory: " + fileOut);
            System.exit(1);
            return;
        }
        if (!fileOut.isDirectory()) {
            System.err.println("-o must point to a directory for --sequence-diagrams: " + fileOut);
            System.exit(1);
            return;
        }
        ProgressLogger progress = new ProgressLogger();
        long startMs = System.currentTimeMillis();
        progress.step("Analyzing project: " + fileIn.getAbsolutePath());
        java.util.List<padtools.core.formats.uml.JavaClassInfo> infos =
                UmlGenerator.extractFromProject(fileIn, null, listener, mergeManifest);
        progress.step("Generating sequence diagrams (.puml + .svg)");
        int count = generateLifecycleSequenceDiagrams(infos, fileOut, legendOverride,
                overrides, progress, listener);
        progress.wrote(fileOut, "(" + count + " diagram(s))");
        progress.done(fileOut, System.currentTimeMillis() - startMs);
    }

    /**
     * {@code -c --per-folder}: プロジェクトを再帰スキャンし、ソースファイルを直接含む
     * 各フォルダごとに 1 枚ずつ PlantUML クラス図 ({@code classes.puml} + {@code classes.svg})
     * を {@code -o} で指定されたディレクトリ配下にサブフォルダ構造を維持して出力する。
     */
    private static void handleClassDiagramsPerFolder(File fileIn, File fileOut,
                                                       ErrorListener listener,
                                                       Boolean legendOverride,
                                                       boolean mergeManifest,
                                                       UmlOverrides overrides) throws IOException {
        if (fileIn == null || !fileIn.isDirectory()) {
            System.err.println("--per-folder requires a project directory.");
            System.exit(1);
            return;
        }
        if (fileOut == null) {
            System.err.println("--per-folder requires an output directory via -o.");
            System.exit(1);
            return;
        }
        if (!fileOut.exists() && !fileOut.mkdirs()) {
            System.err.println("Failed to create output directory: " + fileOut);
            System.exit(1);
            return;
        }
        if (!fileOut.isDirectory()) {
            System.err.println("-o must point to a directory for --per-folder: " + fileOut);
            System.exit(1);
            return;
        }
        padtools.core.formats.uml.PlantUmlClassDiagram.Options clsOpts =
                new padtools.core.formats.uml.PlantUmlClassDiagram.Options();
        if (Boolean.FALSE.equals(legendOverride)) {
            clsOpts.includeLegend = false;
        }
        if (overrides != null) {
            overrides.applyTo(clsOpts);
        }

        ProgressLogger progress = new ProgressLogger();
        long startMs = System.currentTimeMillis();
        progress.step("Analyzing project: " + fileIn.getAbsolutePath());
        progress.step("Generating per-folder class diagrams (.puml + .svg)");
        padtools.core.formats.uml.PerFolderClassDiagrams.Result result =
                padtools.core.formats.uml.PerFolderClassDiagrams.generate(
                        fileIn, fileOut, null, clsOpts, mergeManifest, null, listener);
        progress.wrote(fileOut,
                "(" + result.getFolderCount() + " folder(s), "
                        + result.getClassCount() + " class(es))");
        progress.done(fileOut, System.currentTimeMillis() - startMs);
    }

    /** {@code --gradle}: 単一 build.gradle (もしくはディレクトリ) を Markdown サマリーに変換。 */
    private static void handleGradleInput(File fileIn, File fileOut,
                                            ErrorListener listener) throws IOException {
        if (fileIn == null) {
            System.err.println("Gradle mode requires an input file or directory.");
            System.exit(1);
            return;
        }
        if (fileIn.isDirectory()) {
            AndroidProjectAnalysis analysis = AndroidProjectAnalyzer.analyze(fileIn, listener);
            writeText(fileOut, TextSummaryReport.toMarkdown(analysis));
        } else {
            String content = AndroidProjectScanner.readFile(fileIn);
            GradleProjectInfo info = GradleScriptParser.parse(content, fileIn.getName(), listener);
            AndroidProjectAnalysis fake = new AndroidProjectAnalysis();
            fake.getGradleByModule().put(fileIn.getName(), info);
            writeText(fileOut, TextSummaryReport.toMarkdown(fake));
        }
    }

    /** {@code --manifest}: 単一 AndroidManifest.xml (もしくはディレクトリ) を Markdown サマリーに変換。 */
    private static void handleManifestInput(File fileIn, File fileOut,
                                              ErrorListener listener) throws IOException {
        if (fileIn == null) {
            System.err.println("Manifest mode requires an input file or directory.");
            System.exit(1);
            return;
        }
        if (fileIn.isDirectory()) {
            AndroidProjectAnalysis analysis = AndroidProjectAnalyzer.analyze(fileIn, listener);
            writeText(fileOut, TextSummaryReport.toMarkdown(analysis));
        } else {
            String content = AndroidProjectScanner.readFile(fileIn);
            AndroidManifestInfo info = AndroidManifestParser.parse(content, listener);
            AndroidProjectAnalysis fake = new AndroidProjectAnalysis();
            java.util.List<AndroidManifestInfo> list = new java.util.ArrayList<>();
            list.add(info);
            fake.getManifestsByModule().put(fileIn.getName(), list);
            writeText(fileOut, TextSummaryReport.toMarkdown(fake));
        }
    }

    /** {@code --component-diagram}: コンポーネント図 PlantUML を生成。 */
    private static void handleComponentDiagram(File fileIn, File fileOut,
                                                 ErrorListener listener,
                                                 Boolean legendOverride) throws IOException {
        if (fileIn == null || !fileIn.isDirectory()) {
            System.err.println("Component diagram requires a project directory.");
            System.exit(1);
            return;
        }
        AndroidProjectAnalysis analysis = AndroidProjectAnalyzer.analyze(fileIn, listener);
        PlantUmlComponentDiagram.Options o = new PlantUmlComponentDiagram.Options();
        if (Boolean.FALSE.equals(legendOverride)) {
            o.includeLegend = false;
        }
        writeUmlOutput(fileOut, PlantUmlComponentDiagram.generate(analysis, o));
    }

    /**
     * {@code --manifest-diagram}: AndroidManifest.xml の構造 (Application + 配下コンポーネント
     * + permissions + features) を 1 枚にまとめた PlantUML 図を生成する。
     */
    private static void handleManifestDiagram(File fileIn, File fileOut,
                                                ErrorListener listener,
                                                Boolean legendOverride) throws IOException {
        if (fileIn == null) {
            System.err.println("Manifest diagram requires an input file or directory.");
            System.exit(1);
            return;
        }
        AndroidProjectAnalysis analysis;
        if (fileIn.isDirectory()) {
            analysis = AndroidProjectAnalyzer.analyze(fileIn, listener);
        } else {
            String content = AndroidProjectScanner.readFile(fileIn);
            AndroidManifestInfo info = AndroidManifestParser.parse(content, listener);
            analysis = new AndroidProjectAnalysis();
            java.util.List<AndroidManifestInfo> list = new java.util.ArrayList<>();
            list.add(info);
            analysis.getManifestsByModule().put(fileIn.getName(), list);
        }
        PlantUmlManifestDiagram.Options o = new PlantUmlManifestDiagram.Options();
        if (Boolean.FALSE.equals(legendOverride)) {
            o.includeLegend = false;
        }
        writeUmlOutput(fileOut, PlantUmlManifestDiagram.generate(analysis, o));
    }

    /**
     * {@code --deeplink-diagram} / {@code -D}: VIEW + BROWSABLE を持つ Activity の
     * Deep Link / App Links を可視化する PlantUML 図を生成する。
     */
    private static void handleDeepLinkDiagram(File fileIn, File fileOut,
                                                ErrorListener listener,
                                                Boolean legendOverride) throws IOException {
        if (fileIn == null) {
            System.err.println("Deep link diagram requires an input file or directory.");
            System.exit(1);
            return;
        }
        AndroidProjectAnalysis analysis;
        if (fileIn.isDirectory()) {
            analysis = AndroidProjectAnalyzer.analyze(fileIn, listener);
        } else {
            String content = AndroidProjectScanner.readFile(fileIn);
            AndroidManifestInfo info = AndroidManifestParser.parse(content, listener);
            analysis = new AndroidProjectAnalysis();
            java.util.List<AndroidManifestInfo> list = new java.util.ArrayList<>();
            list.add(info);
            analysis.getManifestsByModule().put(fileIn.getName(), list);
        }
        PlantUmlDeepLinkDiagram.Options o = new PlantUmlDeepLinkDiagram.Options();
        if (Boolean.FALSE.equals(legendOverride)) {
            o.includeLegend = false;
        }
        writeUmlOutput(fileOut, PlantUmlDeepLinkDiagram.generate(analysis, o));
    }

    /** {@code --dependency-graph}: Gradle 依存グラフ PlantUML を生成。 */
    private static void handleDependencyGraph(File fileIn, File fileOut,
                                                ErrorListener listener,
                                                Boolean legendOverride) throws IOException {
        if (fileIn == null || !fileIn.isDirectory()) {
            System.err.println("Dependency graph requires a project directory.");
            System.exit(1);
            return;
        }
        AndroidProjectAnalysis analysis = AndroidProjectAnalyzer.analyze(fileIn, listener);
        PlantUmlGradleDependencyGraph.Options o = new PlantUmlGradleDependencyGraph.Options();
        if (Boolean.FALSE.equals(legendOverride)) {
            o.includeLegend = false;
        }
        writeUmlOutput(fileOut, PlantUmlGradleDependencyGraph.generate(analysis, o));
    }

    /** {@code --summary}: プロジェクト全体の Markdown サマリーを生成。 */
    private static void handleSummary(File fileIn, File fileOut,
                                        ErrorListener listener) throws IOException {
        if (fileIn == null || !fileIn.isDirectory()) {
            System.err.println("Summary requires a project directory.");
            System.exit(1);
            return;
        }
        AndroidProjectAnalysis analysis = AndroidProjectAnalyzer.analyze(fileIn, listener);
        writeText(fileOut, TextSummaryReport.toMarkdown(analysis));
    }

    /**
     * {@code --all}: プロジェクトディレクトリを入力に、複数種類の成果物を出力ディレクトリへ一括書き出し。
     * <ul>
     *   <li>{@code summary.md} - Markdown プロジェクトサマリー</li>
     *   <li>{@code class-diagram.svg} - PlantUML クラス図 (manifest 自動マージ)</li>
     *   <li>{@code component-diagram.svg} - PlantUML Android コンポーネント図</li>
     *   <li>{@code manifest-diagram.svg} - PlantUML AndroidManifest 図 (Application + 配下コンポーネント)</li>
     *   <li>{@code deeplink-diagram.svg} - PlantUML Deep Link / App Links 図</li>
     *   <li>{@code dependency-graph.svg} - PlantUML Gradle 依存グラフ</li>
     *   <li>{@code methods.txt} - シーケンス図の起点候補一覧 ({@code Class.method}) </li>
     *   <li>{@code sequence-diagrams/}{@code <Class.method>.svg} - Android ライフサイクル
     *       (Activity/Service の {@code onCreate} 等) を起点に自動生成したシーケンス図</li>
     * </ul>
     * <p>すべて同梱ライブラリのみで完結するため、PlantUML/dot のインストールは不要。
     * ライフサイクル外のメソッドからシーケンス図を作る場合は {@code methods.txt} を
     * 参考に {@code -q Class.method -o seq.svg} で個別生成する。</p>
     */
    private static void handleAll(File fileIn, File fileOut,
                                    ErrorListener listener,
                                    Boolean legendOverride,
                                    boolean mergeManifest,
                                    UmlOverrides overrides) throws IOException {
        if (fileIn == null || !fileIn.isDirectory()) {
            System.err.println("--all requires a project directory.");
            System.exit(1);
            return;
        }
        if (fileOut == null) {
            System.err.println("--all requires an output directory via -o.");
            System.exit(1);
            return;
        }
        if (!fileOut.exists() && !fileOut.mkdirs()) {
            System.err.println("Failed to create output directory: " + fileOut);
            System.exit(1);
            return;
        }
        if (!fileOut.isDirectory()) {
            System.err.println("-o must point to a directory when --all is set: " + fileOut);
            System.exit(1);
            return;
        }

        // --all は時間がかかる複合処理なので、-v の有無に関わらず進捗ログを stderr に
        // 出す。ユーザが渡したリスナー (verbose なら stderr / silent なら no-op) は
        // パース警告等の付随情報として残しつつ、進捗ログは別経路で確実に表示する。
        ProgressLogger progress = new ProgressLogger();
        long startMs = System.currentTimeMillis();
        progress.step("Analyzing project: " + fileIn.getAbsolutePath());

        // プロジェクト解析を 1 回だけ実行して再利用する
        AndroidProjectAnalysis analysis = AndroidProjectAnalyzer.analyze(fileIn, listener);

        // 1) Markdown サマリー
        progress.step("[1/8] Generating summary.md");
        File summaryFile = new File(fileOut, "summary.md");
        writeText(summaryFile, TextSummaryReport.toMarkdown(analysis));
        progress.wrote(summaryFile);
        listener.onError(null, -1, "wrote " + summaryFile.getPath());

        // 2) コンポーネント図 (SVG)
        progress.step("[2/8] Generating component-diagram.svg");
        PlantUmlComponentDiagram.Options compOpts = new PlantUmlComponentDiagram.Options();
        if (Boolean.FALSE.equals(legendOverride)) {
            compOpts.includeLegend = false;
        }
        File compFile = new File(fileOut, "component-diagram.svg");
        renderSvgOrFallback(PlantUmlComponentDiagram.generate(analysis, compOpts),
                compFile, progress, listener);

        // 3) Manifest 図 (SVG) — Application + 配下コンポーネントを 1 枚で可視化
        progress.step("[3/8] Generating manifest-diagram.svg");
        PlantUmlManifestDiagram.Options manOpts = new PlantUmlManifestDiagram.Options();
        if (Boolean.FALSE.equals(legendOverride)) {
            manOpts.includeLegend = false;
        }
        File manFile = new File(fileOut, "manifest-diagram.svg");
        renderSvgOrFallback(PlantUmlManifestDiagram.generate(analysis, manOpts),
                manFile, progress, listener);

        // 4) Deep Link 図 (SVG) — VIEW + BROWSABLE intent-filter の URI 入口を可視化
        progress.step("[4/8] Generating deeplink-diagram.svg");
        PlantUmlDeepLinkDiagram.Options dlOpts = new PlantUmlDeepLinkDiagram.Options();
        if (Boolean.FALSE.equals(legendOverride)) {
            dlOpts.includeLegend = false;
        }
        File dlFile = new File(fileOut, "deeplink-diagram.svg");
        renderSvgOrFallback(PlantUmlDeepLinkDiagram.generate(analysis, dlOpts),
                dlFile, progress, listener);

        // 5) 依存グラフ (SVG)
        progress.step("[5/8] Generating dependency-graph.svg");
        PlantUmlGradleDependencyGraph.Options depOpts = new PlantUmlGradleDependencyGraph.Options();
        if (Boolean.FALSE.equals(legendOverride)) {
            depOpts.includeLegend = false;
        }
        File depFile = new File(fileOut, "dependency-graph.svg");
        renderSvgOrFallback(PlantUmlGradleDependencyGraph.generate(analysis, depOpts),
                depFile, progress, listener);

        // 6) クラス図 (SVG)。UmlGenerator は内部で再走査するが、manifest 連携のため別経路。
        progress.step("[6/8] Generating class-diagram.svg (scanning Java/AIDL)");
        java.util.List<padtools.core.formats.uml.JavaClassInfo> infos =
                UmlGenerator.extractFromProject(fileIn, null, listener, mergeManifest);
        padtools.core.formats.uml.PlantUmlClassDiagram.Options clsOpts =
                new padtools.core.formats.uml.PlantUmlClassDiagram.Options();
        if (Boolean.FALSE.equals(legendOverride)) {
            clsOpts.includeLegend = false;
        }
        if (overrides != null) {
            overrides.applyTo(clsOpts);
        }
        File clsFile = new File(fileOut, "class-diagram.svg");
        String clsPuml = padtools.core.formats.uml.PlantUmlClassDiagram.generate(infos, clsOpts);
        try {
            PlantUmlRenderer.renderSvg(clsPuml, clsFile);
            progress.wrote(clsFile, "(" + infos.size() + " class(es))");
            listener.onError(null, -1, "wrote " + clsFile.getPath());
        } catch (padtools.core.formats.uml.PlantUmlRenderFailedException ex) {
            File clsPumlFallback = siblingPumlFor(clsFile);
            writeText(clsPumlFallback, clsPuml);
            System.err.println("[padtools]     -> " + clsFile.getName()
                    + " FAILED: " + ex.getMessage());
            System.err.println("[padtools]        Saved " + clsPumlFallback.getName()
                    + " -- render externally with: plantuml -tsvg "
                    + clsPumlFallback.getName());
        }

        // 7) シーケンス図の起点候補一覧
        progress.step("[7/8] Generating methods.txt (sequence diagram entry candidates)");
        java.util.List<padtools.core.formats.uml.PlantUmlSequenceDiagram.Candidate> candidates =
                padtools.core.formats.uml.PlantUmlSequenceDiagram.listCandidates(infos);
        StringBuilder methodsBuf = new StringBuilder();
        for (padtools.core.formats.uml.PlantUmlSequenceDiagram.Candidate c : candidates) {
            methodsBuf.append(c.getEntry())
                    .append("\t(").append(c.callCount).append(" call")
                    .append(c.callCount == 1 ? "" : "s")
                    .append(", ").append(c.visibility.name().toLowerCase()).append(")\n");
        }
        File methodsFile = new File(fileOut, "methods.txt");
        writeText(methodsFile, methodsBuf.toString());
        progress.wrote(methodsFile, "(" + candidates.size() + " method(s))");
        listener.onError(null, -1, "wrote " + methodsFile.getPath());

        // 8) Android ライフサイクルメソッドを自動的に起点としたシーケンス図
        progress.step("[8/8] Generating sequence-diagrams/ (Android lifecycle entry points)");
        File seqDir = new File(fileOut, "sequence-diagrams");
        if (!seqDir.exists() && !seqDir.mkdirs()) {
            System.err.println("[padtools]     Skipping sequence-diagrams (cannot create dir)");
        } else {
            int seqCount = generateLifecycleSequenceDiagrams(infos, seqDir, legendOverride,
                    overrides, progress, listener);
            progress.wrote(seqDir, "(" + seqCount + " diagram(s), .puml + .svg)");
        }

        long elapsedMs = System.currentTimeMillis() - startMs;
        progress.done(fileOut, elapsedMs);
    }

    /**
     * Activity/Service のライフサイクルメソッドを起点にシーケンス図を一括生成し、
     * 各起点ごとに {@code Class.method.puml} と {@code Class.method.svg} の両方を出力する。
     */
    private static int generateLifecycleSequenceDiagrams(
            java.util.List<padtools.core.formats.uml.JavaClassInfo> infos,
            File outDir,
            Boolean legendOverride,
            UmlOverrides overrides,
            ProgressLogger progress,
            ErrorListener listener) throws IOException {
        padtools.core.formats.uml.PlantUmlSequenceDiagram.Options sqOpts
                = new padtools.core.formats.uml.PlantUmlSequenceDiagram.Options();
        if (Boolean.FALSE.equals(legendOverride)) {
            sqOpts.includeLegend = false;
        }
        if (overrides != null) {
            overrides.applyTo(sqOpts);
            if (overrides.seqDepth != null) {
                sqOpts.maxDepth = overrides.seqDepth;
            }
        }
        java.util.List<LifecycleSequenceDiagrams.Entry> entries =
                LifecycleSequenceDiagrams.generateAll(infos, sqOpts);
        for (LifecycleSequenceDiagrams.Entry e : entries) {
            writeText(new File(outDir, e.baseName() + ".puml"), e.puml);
            File svgFile = new File(outDir, e.baseName() + ".svg");
            try {
                PlantUmlRenderer.renderSvg(e.puml, svgFile);
            } catch (padtools.core.formats.uml.PlantUmlRenderFailedException ex) {
                System.err.println("[padtools]     -> " + svgFile.getName()
                        + " FAILED: " + ex.getMessage()
                        + " (.puml is preserved)");
            }
        }
        return entries.size();
    }

    /**
     * {@code --all} 専用の進捗ロガー。{@code -v} の有無に関わらず常に stderr に出力する。
     */
    private static final class ProgressLogger {
        void step(String msg) {
            System.err.println("[padtools] " + msg);
        }

        void wrote(File f) {
            wrote(f, null);
        }

        void wrote(File f, String suffix) {
            long size = f.exists() ? f.length() : 0L;
            StringBuilder sb = new StringBuilder("[padtools]     -> ");
            sb.append(f.getName()).append(" (").append(formatBytes(size)).append(')');
            if (suffix != null && !suffix.isEmpty()) {
                sb.append(' ').append(suffix);
            }
            System.err.println(sb.toString());
        }

        void done(File outDir, long elapsedMs) {
            System.err.println("[padtools] Done in " + elapsedMs + " ms. "
                    + "Output: " + outDir.getAbsolutePath());
        }

        private static String formatBytes(long n) {
            if (n < 1024) {
                return n + "B";
            }
            if (n < 1024 * 1024) {
                return (n / 1024) + "KB";
            }
            return String.format("%.1fMB", n / 1024.0 / 1024.0);
        }
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
        System.err.println("  input: Java/AIDL file or Gradle/Android project directory.");
    }
}
