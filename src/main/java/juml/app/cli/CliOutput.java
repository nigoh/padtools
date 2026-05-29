// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.cli;

import juml.core.formats.uml.PlantUmlRenderFailedException;
import juml.core.formats.uml.PlantUmlRenderer;
import juml.util.ErrorListener;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

/**
 * CLI 各モード共通の成果物書き出しヘルパ。テキスト/PlantUML/SVG の出力先判定と、
 * SVG レンダリング失敗時のサイドカー {@code .puml} フォールバックを集約する。
 */
public final class CliOutput {

    private CliOutput() {
    }

    /** テキストをファイルへ UTF-8 で書き出す。{@code f} が null なら標準出力。 */
    public static void writeText(File f, String content) throws IOException {
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
    public static void writeUmlOutput(File fileOut, String puml) throws IOException {
        if (fileOut != null && fileOut.getName().toLowerCase().endsWith(".svg")) {
            try {
                PlantUmlRenderer.renderSvg(puml, fileOut);
            } catch (PlantUmlRenderFailedException ex) {
                File pumlFallback = siblingPumlFor(fileOut);
                writeText(pumlFallback, puml);
                System.err.println("[juml] " + fileOut.getName()
                        + " FAILED: " + ex.getMessage());
                System.err.println("[juml]    Saved " + pumlFallback.getPath()
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
    public static File siblingPumlFor(File svgFile) {
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
    public static boolean renderSvgOrFallback(String puml, File svgFile,
                                               ProgressLogger progress,
                                               ErrorListener listener) throws IOException {
        try {
            PlantUmlRenderer.renderSvg(puml, svgFile);
            progress.wrote(svgFile);
            listener.onError(null, -1, "wrote " + svgFile.getPath());
            return true;
        } catch (PlantUmlRenderFailedException ex) {
            File pumlFallback = siblingPumlFor(svgFile);
            writeText(pumlFallback, puml);
            System.err.println("[juml]     -> " + svgFile.getName()
                    + " FAILED: " + ex.getMessage());
            System.err.println("[juml]        Saved " + pumlFallback.getName()
                    + " -- render externally with: plantuml -tsvg "
                    + pumlFallback.getName());
            return false;
        }
    }

    /**
     * Impact レポートの書き出し。出力先の拡張子を見て .md / .puml / 両方を切り替える。
     */
    public static void writeImpactOutput(File fileOut, String markdown, String puml)
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
}
