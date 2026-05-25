// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package padtools.app.cli;

import padtools.core.aaos.AidlBinding;
import padtools.core.aaos.AidlBindingResolver;
import padtools.core.aaos.MarkdownAidlBindingReport;
import padtools.core.aaos.MarkdownVhalReport;
import padtools.core.aaos.PlantUmlVhalFlowDiagram;
import padtools.core.aaos.VehiclePropertyCatalog;
import padtools.core.aaos.VhalAccess;
import padtools.core.aaos.VhalAnalyzer;
import padtools.core.aosp.AndroidBpModule;
import padtools.core.aosp.AndroidBpParser;
import padtools.core.aosp.MarkdownRroReport;
import padtools.core.aosp.MarkdownSelinuxReport;
import padtools.core.aosp.MarkdownSoongReport;
import padtools.core.aosp.PlantUmlSoongDependencyDiagram;
import padtools.core.aosp.RroOverlay;
import padtools.core.aosp.RroOverlayDetector;
import padtools.core.aosp.SelinuxPolicyParser;
import padtools.core.aosp.SelinuxRule;
import padtools.core.formats.uml.UmlGenerator;

import java.io.File;
import java.io.IOException;

/**
 * AOSP / AAOS 系の CLI モード
 * ({@code --vhal-flow} / {@code --aidl-binding} / {@code --android-bp} /
 * {@code --selinux} / {@code --rro-overlays}) のハンドラ群。
 */
public final class AospCommands {

    private AospCommands() {
    }

    /**
     * {@code --vhal-flow}: プロジェクトを走査して
     * {@link padtools.core.aaos.CarPropertyManager} 系呼び出しを検出し、
     * Property 別 GET/SET/SUBSCRIBE フローを Markdown + PlantUML で出力する。
     */
    public static void handleVhalFlow(CliContext ctx) throws IOException {
        File fileIn = ctx.fileIn;
        File fileOut = ctx.fileOut;
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
        CliOutput.writeImpactOutput(fileOut, md, puml);
    }

    /**
     * {@code --aidl-binding}: プロジェクト内の AIDL インタフェースと、その
     * {@code Stub} を継承する実装クラスとの対応表を Markdown で出力する。
     */
    public static void handleAidlBinding(CliContext ctx) throws IOException {
        File fileIn = ctx.fileIn;
        File fileOut = ctx.fileOut;
        if (fileIn == null || !fileIn.isDirectory()) {
            System.err.println("--aidl-binding requires a project directory as input.");
            System.exit(1);
            return;
        }
        UmlGenerator.ProjectParseResult result =
                UmlGenerator.extractFromProjectDetailed(fileIn, null, ctx.listener,
                        null, null, false, UmlGenerator.ParseMode.FULL);
        java.util.Map<String, java.util.List<AidlBinding>> bindings =
                new AidlBindingResolver().resolve(result.getClasses());
        String md = MarkdownAidlBindingReport.render(bindings);
        CliOutput.writeText(fileOut, md);
    }

    /**
     * {@code --android-bp}: プロジェクト下を再帰的に走査して {@code Android.bp}
     * (Soong Blueprint) を解析し、モジュール依存図 (PlantUML) と Markdown レポートを出力する。
     */
    public static void handleAndroidBp(CliContext ctx) throws IOException {
        File fileIn = ctx.fileIn;
        File fileOut = ctx.fileOut;
        if (fileIn == null || !fileIn.isDirectory()) {
            System.err.println("--android-bp requires a project directory as input.");
            System.exit(1);
            return;
        }
        java.util.List<AndroidBpModule> modules =
                new AndroidBpParser().analyzeProject(fileIn);
        String md = MarkdownSoongReport.render(modules);
        String puml = PlantUmlSoongDependencyDiagram.render(modules);
        CliOutput.writeImpactOutput(fileOut, md, puml);
    }

    /**
     * {@code --selinux}: プロジェクト下の {@code *.te} を再帰走査し、
     * type 宣言と allow/neverallow ルールを Markdown レポートにまとめる。
     */
    public static void handleSelinux(CliContext ctx) throws IOException {
        File fileIn = ctx.fileIn;
        File fileOut = ctx.fileOut;
        if (fileIn == null || !fileIn.isDirectory()) {
            System.err.println("--selinux requires a project directory as input.");
            System.exit(1);
            return;
        }
        java.util.List<SelinuxRule> rules =
                new SelinuxPolicyParser().analyzeProject(fileIn);
        String md = MarkdownSelinuxReport.render(rules);
        CliOutput.writeText(fileOut, md);
    }

    /**
     * {@code --rro-overlays}: プロジェクト下の {@code AndroidManifest.xml} から
     * {@code &lt;overlay targetPackage="..."&gt;} を検出し、RRO 一覧を Markdown 出力する。
     */
    public static void handleRroOverlays(CliContext ctx) throws IOException {
        File fileIn = ctx.fileIn;
        File fileOut = ctx.fileOut;
        if (fileIn == null || !fileIn.isDirectory()) {
            System.err.println("--rro-overlays requires a project directory as input.");
            System.exit(1);
            return;
        }
        java.util.List<RroOverlay> overlays =
                new RroOverlayDetector().analyzeProject(fileIn);
        String md = MarkdownRroReport.render(overlays);
        CliOutput.writeText(fileOut, md);
    }
}
