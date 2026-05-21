package padtools.app.cli;

import padtools.UmlOverrides;
import padtools.util.ErrorListener;

import java.io.File;

/**
 * CLI 各モードのハンドラが共通で必要とする入力・出力・描画設定をまとめた不変オブジェクト。
 *
 * <p>従来は各 {@code handleXxx} がこれら 6 つの値を個別引数として受け渡していたが、
 * 1 つにまとめることで引数渡しの重複を排し、{@code ParameterNumber} を抑える。</p>
 */
public final class CliContext {

    /** 入力ファイルまたはプロジェクトディレクトリ。引数なしなら null。 */
    public final File fileIn;
    /** 出力先。{@code -o} 未指定なら null (標準出力)。 */
    public final File fileOut;
    /** パース警告などの通知先 ({@code -v} で stderr、既定は no-op)。 */
    public final ErrorListener listener;
    /** 凡例の強制 ON/OFF。null なら各図のデフォルトに従う。 */
    public final Boolean legendOverride;
    /** クラス図の Manifest 自動マージを行うか。 */
    public final boolean mergeManifest;
    /** UML 系出力の上書きオプション束。 */
    public final UmlOverrides overrides;

    public CliContext(File fileIn, File fileOut, ErrorListener listener,
                      Boolean legendOverride, boolean mergeManifest,
                      UmlOverrides overrides) {
        this.fileIn = fileIn;
        this.fileOut = fileOut;
        this.listener = listener;
        this.legendOverride = legendOverride;
        this.mergeManifest = mergeManifest;
        this.overrides = overrides;
    }
}
