// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package padtools.util;

import java.awt.Font;
import java.awt.GraphicsEnvironment;

/**
 * PlantUML 図で日本語（JavaDoc コメント・日本語クラス名など）が
 * 文字化け（豆腐 □）しないよう、実行環境に実在する日本語対応フォント
 * ファミリ名を解決するユーティリティ。
 *
 * <p>{@code skinparam defaultFontName} に渡すための既定フォント名を返す。
 * 解決結果は一度だけ計算してキャッシュする。日本語対応フォントが
 * 見つからない場合は空文字を返し、呼び出し側はフォント指定を省略する
 * （= 従来どおり PlantUML 既定フォントにフォールバック）。</p>
 */
public final class JapaneseFontSupport {

    private JapaneseFontSupport() {
    }

    /** 優先的に採用したい日本語フォントファミリ（OS 横断）。先頭ほど優先。 */
    private static final String[] PREFERRED = {
        "Noto Sans CJK JP", "Noto Sans JP", "Source Han Sans JP", "Source Han Sans",
        "Yu Gothic UI", "Yu Gothic", "Meiryo", "MS PGothic", "MS Gothic",
        "Hiragino Sans", "Hiragino Kaku Gothic ProN", "Hiragino Kaku Gothic Pro",
        "IPAexGothic", "IPAGothic", "TakaoGothic", "VL PGothic", "VL Gothic"
    };

    /** 日本語表示可否の判定に使うサンプル文字（ひらがな・漢字・カタカナ）。 */
    private static final char[] SAMPLE = {'あ', '日', 'ア'};

    private static volatile boolean resolved;
    private static volatile String cached = "";

    /**
     * 日本語を表示できる既定フォントファミリ名を返す。
     * 見つからない場合は空文字。結果はキャッシュされる。
     */
    public static String defaultFontFamily() {
        if (!resolved) {
            synchronized (JapaneseFontSupport.class) {
                if (!resolved) {
                    cached = resolve();
                    resolved = true;
                }
            }
        }
        return cached;
    }

    private static String resolve() {
        String[] families;
        try {
            families = GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getAvailableFontFamilyNames();
        } catch (Throwable t) {
            // ヘッドレス例外など、フォント環境を取得できない場合は指定なし。
            return "";
        }
        if (families == null || families.length == 0) {
            return "";
        }
        // 1) 優先リストにある実在フォントを順に探す（大文字小文字無視）。
        for (String pref : PREFERRED) {
            for (String fam : families) {
                if (pref.equalsIgnoreCase(fam) && canDisplayJapanese(fam)) {
                    return fam;
                }
            }
        }
        // 2) インストール済みフォントから日本語を表示できるものを探す。
        for (String fam : families) {
            if (canDisplayJapanese(fam)) {
                return fam;
            }
        }
        return "";
    }

    private static boolean canDisplayJapanese(String family) {
        try {
            Font f = new Font(family, Font.PLAIN, 12);
            for (char c : SAMPLE) {
                if (!f.canDisplay(c)) {
                    return false;
                }
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
