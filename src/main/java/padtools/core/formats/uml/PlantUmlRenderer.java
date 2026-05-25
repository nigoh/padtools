// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package padtools.core.formats.uml;

import net.sourceforge.plantuml.FileFormat;
import net.sourceforge.plantuml.FileFormatOption;
import net.sourceforge.plantuml.SourceStringReader;

import padtools.util.JapaneseFontSupport;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.function.BiConsumer;

/**
 * PlantUML テキストを SVG 等の画像形式に変換するレンダラ。
 *
 * <p>同梱の PlantUML jar を使って描画するため、外部 graphviz/dot は不要。
 * Graphviz が必要な diagram (クラス図 / コンポーネント図) では Smetana レイアウトを
 * 自動指定するため、追加インストールなしで動作する。</p>
 *
 * <p>{@link #renderSvg(String, OutputStream)} は内部でレンダリング結果を一旦バッファし、
 * PlantUML がフォールバックで返す「An error has occured」SVG を検出した場合は
 * {@link PlantUmlRenderFailedException} を投げる。これにより呼び元 (CLI / GUI) は
 * 壊れた SVG を保存・表示することを避けられる。</p>
 */
public final class PlantUmlRenderer {

    /**
     * 現在のスタイル設定。{@link #injectLayout(String)} 経由で挿入される。
     * 起動時に設定ファイルから読み込んだ値で上書きされる想定。GUI から変更可能。
     */
    private static volatile DiagramStyle currentStyle = DiagramStyle.defaults();

    /**
     * スタイルでフォント未指定時に既定として補う日本語対応フォントファミリ名。
     * 実行環境に存在する日本語フォントを {@link JapaneseFontSupport} で解決した値を持つ
     * （見つからなければ空文字 = 補わない）。これにより図内の日本語が文字化け（豆腐 □）
     * しないようにする。ユーザがスタイルで明示的にフォントを指定した場合はそちらが優先される。
     */
    private static volatile String fallbackFontName = JapaneseFontSupport.defaultFontFamily();

    /**
     * verbose モード。true なら同梱 Smetana 由来の stderr (UNSURE_ABOUT 等) を素通し、
     * false なら {@link #renderSvg(String, OutputStream)} 実行中だけ捨てる。
     */
    private static volatile boolean verbose = false;

    /**
     * Graphviz dot が利用可能かどうか。{@link GraphvizLocator#init(java.io.File)} が
     * dot を発見した場合に true になる。true のとき {@link #injectLayout(String)} は
     * {@code !pragma layout smetana} を挿入しない。
     */
    private static volatile boolean graphvizAvailable = false;

    /**
     * テスト用のレンダラ差し替えフック。null でなければ {@link SourceStringReader#outputImage}
     * の代わりに使われる。本番経路では常に null。
     */
    private static volatile BiConsumer<String, OutputStream> rendererImplForTest;

    /** PlantUML フォールバック エラー SVG に必ず含まれるマーカー。 */
    private static final String[] ERROR_MARKERS = {
            "An error has occured",
            "I love it when a plan comes together"
    };

    /** PlantUML のキャンバスサイズ上限を制御するシステムプロパティ / 環境変数名。 */
    private static final String LIMIT_SIZE_PROP = "PLANTUML_LIMIT_SIZE";

    /**
     * PNG ラスタライズ時の 1 辺あたり最大ピクセル数の既定値。PlantUML 既定の 4096 では
     * 巨大なクラス図/シーケンス図が PNG エクスポートで切り詰められてしまうため、より大きく取る。
     * 16384²×4byte ≒ 1GB が上限の目安なので、実用上ほとんどの図を収めつつ極端な OOM を避ける値。
     */
    public static final int DEFAULT_IMAGE_LIMIT = 16384;

    private PlantUmlRenderer() {
    }

    /**
     * PlantUML のキャンバスサイズ上限 ({@code PLANTUML_LIMIT_SIZE}) を {@link #DEFAULT_IMAGE_LIMIT}
     * に引き上げる。ユーザが {@code -DPLANTUML_LIMIT_SIZE=...} または環境変数で明示指定済みの
     * 場合は尊重して何もしない。アプリ起動時 ({@code Main}) に一度だけ呼ぶ想定。
     */
    public static void configureImageLimit() {
        if (System.getProperty(LIMIT_SIZE_PROP) == null
                && System.getenv(LIMIT_SIZE_PROP) == null) {
            System.setProperty(LIMIT_SIZE_PROP, Integer.toString(DEFAULT_IMAGE_LIMIT));
        }
    }

    /**
     * 現在有効な PNG キャンバス上限 (1 辺あたりピクセル数) を返す。プロパティ → 環境変数の順で
     * 参照し、未設定・不正値なら {@link #DEFAULT_IMAGE_LIMIT}。
     */
    public static int imageLimit() {
        String v = System.getProperty(LIMIT_SIZE_PROP);
        if (v == null) {
            v = System.getenv(LIMIT_SIZE_PROP);
        }
        if (v != null) {
            try {
                int n = Integer.parseInt(v.trim());
                if (n > 0) {
                    return n;
                }
            } catch (NumberFormatException ignored) {
                // 不正値は既定にフォールバック
            }
        }
        return DEFAULT_IMAGE_LIMIT;
    }

    /** 現在のスタイルを取得する。null 安全 (常に非 null を返す)。 */
    public static DiagramStyle getStyle() {
        DiagramStyle s = currentStyle;
        return s != null ? s : DiagramStyle.defaults();
    }

    /**
     * 現在のスタイルを更新する。以降の {@link #renderSvg} 系および
     * {@link #injectLayout(String)} 呼び出しに即時反映される。
     * null を渡すと既定スタイルにリセットする。
     */
    public static void setStyle(DiagramStyle style) {
        currentStyle = style != null ? style.copy() : DiagramStyle.defaults();
    }

    /**
     * verbose モードを設定する。CLI で {@code -v} が指定されたら true、それ以外は false。
     * false の間、{@link #renderSvg(String, OutputStream)} 実行中の {@link System#err}
     * は一時的に捨てられ、同梱 Smetana が直接 stderr へ書き込むデバッグ ログを抑制する。
     */
    public static void setVerbose(boolean v) {
        verbose = v;
    }

    /**
     * Graphviz dot の利用可否を設定する。{@link GraphvizLocator} が呼び出す想定。
     * テストから明示的に false を渡してデフォルト動作 (Smetana 挿入) を強制できる。
     */
    public static void setGraphvizAvailable(boolean available) {
        graphvizAvailable = available;
    }

    /** Graphviz dot が利用可能かどうか返す。 */
    public static boolean isGraphvizAvailable() {
        return graphvizAvailable;
    }

    /**
     * フォント未指定時に補う既定フォント名を返す。日本語対応フォントが見つからない
     * 環境では空文字。
     */
    public static String getFallbackFontName() {
        String s = fallbackFontName;
        return s != null ? s : "";
    }

    /**
     * フォント未指定時に補う既定フォント名を設定する。主にテストで決定的な挙動を
     * 得るために使用する。null は空文字（補わない）として扱う。
     */
    public static void setFallbackFontName(String name) {
        fallbackFontName = name != null ? name : "";
    }

    /**
     * テスト専用 ({@code @VisibleForTesting} 相当): {@link SourceStringReader} の代わりに
     * 使うレンダラ実装を差し込む。null を渡すと本番経路に戻る。本番コードから呼ばないこと。
     * <p>JUnit テストが他パッケージ ({@code padtools}) からアクセスする必要があるため
     * {@code public} になっているが、設計上は package-private 想定。</p>
     */
    public static void setRendererImplForTest(BiConsumer<String, OutputStream> impl) {
        rendererImplForTest = impl;
    }

    /**
     * 与えられたバイト列が PlantUML のフォールバック エラー SVG かどうか判定する。
     * 先頭 8KB だけサンプリングするので巨大な正常 SVG でも安価。
     */
    static boolean isErrorSvg(byte[] svgBytes) {
        if (svgBytes == null || svgBytes.length == 0) {
            return true;
        }
        int len = Math.min(svgBytes.length, 8192);
        String head = new String(svgBytes, 0, len, StandardCharsets.UTF_8);
        for (String marker : ERROR_MARKERS) {
            if (head.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    /** PlantUML テキストを SVG として OutputStream に書き出す。
     *
     * <p>レンダリング結果が PlantUML のエラー画像にすり替わっていた場合は
     * {@link PlantUmlRenderFailedException} を投げる。</p>
     */
    public static void renderSvg(String puml, OutputStream out) throws IOException {
        if (out == null) {
            throw new IllegalArgumentException("out is null");
        }
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        PrintStream origErr = System.err;
        PrintStream sink = null;
        if (!verbose) {
            sink = new PrintStream(OutputStream.nullOutputStream(), false,
                    StandardCharsets.UTF_8);
            System.setErr(sink);
        }
        try {
            BiConsumer<String, OutputStream> stub = rendererImplForTest;
            if (stub != null) {
                stub.accept(puml, buf);
            } else {
                SourceStringReader reader = new SourceStringReader(injectLayout(puml));
                reader.outputImage(buf, new FileFormatOption(FileFormat.SVG));
            }
        } finally {
            if (sink != null) {
                System.setErr(origErr);
                sink.close();
            }
        }
        byte[] bytes = buf.toByteArray();
        if (isErrorSvg(bytes)) {
            throw new PlantUmlRenderFailedException(
                    "PlantUML layout error (likely Smetana). "
                    + "Use the .puml source and re-render externally.");
        }
        out.write(bytes);
    }

    /** PlantUML テキストを SVG として指定ファイルに書き出す。
     *
     * <p>失敗時は壊れた 0 byte ファイルを残さないよう、ファイルを削除した上で
     * 例外を再 throw する。</p>
     */
    public static void renderSvg(String puml, File outFile) throws IOException {
        try (OutputStream os = new FileOutputStream(outFile)) {
            renderSvg(puml, os);
        } catch (IOException e) {
            // 中身が無効な状態のファイルを残さない
            if (outFile.exists() && !outFile.delete()) {
                outFile.deleteOnExit();
            }
            throw e;
        }
    }

    /**
     * PNG エクスポート用に {@code @startuml} 直後へ {@code scale max <maxPx>*<maxPx>} を挿入する。
     * これにより {@code PLANTUML_LIMIT_SIZE} を超える巨大な図は「切り詰め」ではなく「縮小」されて
     * キャンバスに収まり、PNG が途中で切れたり壊れたりするのを防ぐ。
     *
     * <p>{@code scale max} は図が指定サイズより大きいときだけ縮小し、小さい図は拡大しないため
     * 通常サイズの図には影響しない。既に {@code scale} 指定がある図や {@code @startuml} を含まない
     * 文字列はそのまま返す ({@code maxPx <= 0} も同様)。</p>
     */
    public static String injectScaleMax(String puml, int maxPx) {
        if (puml == null) {
            return null;
        }
        int idx = puml.indexOf("@startuml");
        if (idx < 0 || maxPx <= 0 || hasScaleDirective(puml)) {
            return puml;
        }
        String line = "scale max " + maxPx + "*" + maxPx + "\n";
        int nl = puml.indexOf('\n', idx);
        if (nl < 0) {
            return puml + "\n" + line;
        }
        return puml.substring(0, nl + 1) + line + puml.substring(nl + 1);
    }

    /** 行頭 (前後空白除去後) が {@code scale} で始まる行があるか。ユーザ指定の scale を尊重するため。 */
    private static boolean hasScaleDirective(String puml) {
        for (String raw : puml.split("\n", -1)) {
            String t = raw.trim();
            if (t.equals("scale") || t.startsWith("scale ")) {
                return true;
            }
        }
        return false;
    }

    /**
     * {@code @startuml} の直後に {@code !pragma layout smetana} と、現在の
     * {@link #getStyle() スタイル} 由来の {@code !theme} / {@code skinparam} 行を挿入する。
     * Graphviz/dot 未インストール環境でもクラス図/コンポーネント図を描画できるようにする。
     * 既に {@code !pragma layout} 指定があれば layout 行は重複追加しない (スタイル行は追加する)。
     */
    public static String injectLayout(String puml) {
        return injectLayout(puml, getStyle());
    }

    /**
     * スタイルを明示指定する {@link #injectLayout(String)} のオーバーロード (テスト用に公開)。
     */
    public static String injectLayout(String puml, DiagramStyle style) {
        if (puml == null) {
            return null;
        }
        int idx = puml.indexOf("@startuml");
        if (idx < 0) {
            return puml;
        }
        boolean hasLayoutPragma = puml.contains("!pragma layout");
        String prelude = style != null ? style.toPlantUmlPrelude() : "";
        // スタイルでフォント未指定なら、日本語対応の既定フォントを補って文字化けを防ぐ。
        // !theme 由来のフォント指定より後に置くことで、こちらが優先される。
        String fontFallback = "";
        boolean noExplicitFont = style == null || style.getFontName().isEmpty();
        if (noExplicitFont && !getFallbackFontName().isEmpty()) {
            fontFallback = "skinparam defaultFontName " + getFallbackFontName() + "\n";
        }
        String styleLines = prelude + fontFallback;
        if (hasLayoutPragma && styleLines.isEmpty()) {
            return puml;
        }
        StringBuilder injected = new StringBuilder();
        if (!hasLayoutPragma && !graphvizAvailable) {
            injected.append("!pragma layout smetana\n");
        }
        injected.append(styleLines);
        if (injected.length() == 0) {
            return puml;
        }
        int nl = puml.indexOf('\n', idx);
        if (nl < 0) {
            return puml + "\n" + injected.toString();
        }
        return puml.substring(0, nl + 1)
                + injected.toString()
                + puml.substring(nl + 1);
    }
}
