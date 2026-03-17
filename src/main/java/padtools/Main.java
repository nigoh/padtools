package padtools;

import padtools.converter.Converter;
import padtools.editor.Editor;
import padtools.util.Option;
import padtools.util.OptionParser;
import padtools.util.Messages;
import padtools.util.UnknownOptionException;

import java.io.File;
import java.io.IOException;

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

    /**
     * エントリポイント
     * @param args 引数
     */
    public static void main(String[] args) throws IOException {
        // SettingManager を初期化
        SettingManager.initialize();

        //オプション定義
        final Option optHelp = new Option("h", "help", false);
        final Option optOut = new Option("o", "output", true);
        final Option optScale = new Option("s", "scale", true);

        final OptionParser optParser = new OptionParser(new Option[]{
                optHelp, optOut, optScale});

        try {
            optParser.parse(args, 1);
        } catch (UnknownOptionException ex) {
            System.err.println("Unknown option: " + ex.getOption());
            System.exit(1);
        }

        if (optHelp.isSet()) {
            System.err.println("Arguments: [-o result_file] [-s scale] [-h] [spd_file]");
            System.err.println("  -o result_file: Save to result_file.");
            System.err.println("        -s scale: Image scale(available when result_file is set).");
            System.err.println("              -h: Show this help.");
            System.err.println("        spd_file: Open spd file.");
            System.exit(1);
        }

        File file_in;
        if (optParser.getArguments().isEmpty()) {
            file_in = null;
        } else {
            file_in = new File(optParser.getArguments().getFirst());
        }

        File file_out;
        if (optOut.getArguments().isEmpty()) {
            file_out = null;
        } else {
            file_out = new File(optOut.getArguments().getLast());
        }

        Double scale;
        if (optScale.getArguments().isEmpty()) {
            scale = null;
        } else {
            try {
                scale = Double.parseDouble(optScale.getArguments().getLast());
            } catch (NumberFormatException ex) {
                System.err.println(Messages.get("error.invalidScale"));
                System.exit(1);
                return;
            }
        }

        if (file_out == null) {
            Editor.openEditor(file_in);
        } else {
            Converter.convert(file_in, file_out, scale);
        }
    }
}
