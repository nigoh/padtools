// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml;

import juml.app.cli.CliContext;
import juml.app.cli.CliDispatcher;
import juml.app.cli.CliOptions;
import juml.app.uml.UmlApp;
import juml.core.formats.uml.GraphvizLocator;
import juml.core.formats.uml.PlantUmlRenderer;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;

/**
 * エントリポイントクラス。
 *
 * <p>初期化のあと {@link CliOptions} で引数をパースし、{@link CliDispatcher} へ委譲する。
 * どの CLI モードにも該当しなければ UML 専用 GUI を起動する。各出力モードの実体は
 * {@code juml.app.cli} 配下のコマンドクラスにある。</p>
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

    /**
     * エントリポイント
     * @param args 引数
     */
    public static void main(String[] args) throws IOException {
        // 専用サブコマンドの早期分岐 (option parser を経由しない)。
        // 既存の "java -jar Juml.jar -c <path>" のような呼び出しは args[0] が "-c"
        // 等のオプション or 入力パスになるので、ここでは "index" だけを intercept する。
        if (args.length > 0 && "index".equals(args[0])) {
            int code = juml.app.cli.IndexCommand.execute(
                    args, juml.util.ErrorListener.stderr());
            if (code != 0) {
                System.exit(code);
            }
            return;
        }

        // SettingManager / ProjectRepository を初期化し、永続化されたスタイルをレンダラへ反映
        SettingManager.initialize();
        ProjectRepository.initialize();
        PlantUmlRenderer.setStyle(SettingManager.getInstance().getSetting().getStyle());
        PlantUmlRenderer.configureImageLimit();
        GraphvizLocator.init(detectJarDir());

        CliOptions options = new CliOptions();
        options.parse(args);
        if (options.helpRequested()) {
            options.printUsage();
            System.exit(1);
        }

        CliContext ctx = CliContext.from(options);
        if (ctx == null) {
            return; // 引数エラー: CliContext.from 内で System.exit 済み
        }

        if (CliDispatcher.dispatch(options, ctx)) {
            return;
        }

        if (ctx.fileOut != null) {
            System.err.println("-o requires one of: -c / -q / -d / -G / -g / -m / -M / -D"
                    + " / --summary / -A / -Q / --list-methods");
            System.exit(1);
            return;
        }
        // 既定: UML 専用 GUI を起動。引数があれば初期プロジェクトとして渡す。
        UmlApp.launch(ctx.fileIn);
    }
}
