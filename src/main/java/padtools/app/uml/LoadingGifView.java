// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package padtools.app.uml;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.net.URL;

/**
 * ローディング表示の中身 (動く GIF + ステータス文言) を 1 か所にまとめた再利用パネル。
 *
 * <p>Swing は {@link ImageIcon} にアニメーション GIF を渡すと自動でループ再生する
 * (内部の {@code ImageObserver} が再描画を駆動する) ため、フレーム分解などは不要。
 * 起動スプラッシュ ({@link SplashWindow}) と解析中オーバーレイ
 * ({@link LoadingGlassPane}) の両方からこのパネルを使い、見た目を統一する。</p>
 *
 * <p>GIF リソース ({@code /images/loading.gif}) が見つからない場合は GIF ラベルを
 * 出さずステータス文言だけ表示する (リソース欠落でも例外で落とさない)。</p>
 */
final class LoadingGifView extends JPanel {

    private static final String GIF_RESOURCE = "/images/loading.gif";

    private final JLabel statusLabel = new JLabel("", SwingConstants.CENTER);

    LoadingGifView(String initialStatus) {
        setOpaque(false);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        URL gifUrl = LoadingGifView.class.getResource(GIF_RESOURCE);
        if (gifUrl != null) {
            JLabel gifLabel = new JLabel(new ImageIcon(gifUrl));
            gifLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            add(gifLabel);
            add(Box.createVerticalStrut(12));
        }

        statusLabel.setForeground(Color.WHITE);
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD, 14f));
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        add(statusLabel);

        setStatus(initialStatus);
    }

    /** ステータス文言を更新する (stock_controller の {@code update_status} 相当)。 */
    void setStatus(String message) {
        statusLabel.setText(message != null ? message : "");
    }
}
