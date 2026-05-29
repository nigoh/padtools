// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package padtools.app.uml;

import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.GridBagLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.MouseAdapter;

/**
 * プロジェクト解析中に {@link UmlMainFrame} の glass pane として全面に被せる
 * ローディングオーバーレイ。
 *
 * <p>stock_controller の {@code layer: overlay} 中央配置に相当する。半透明の暗幕を
 * 全面に描き、中央に {@link LoadingGifView} (動く GIF + ステータス) を載せる。
 * 解析中は背後の UI を誤操作させないよう、マウス/キーイベントを自身で消費する。</p>
 */
final class LoadingGlassPane extends JComponent {

    /** 背後 UI を覆う半透明の暗幕色。 */
    private static final Color SCRIM = new Color(0, 0, 0, 140);

    private final LoadingGifView view = new LoadingGifView("読み込み中...");

    LoadingGlassPane() {
        setOpaque(false);
        setVisible(false);
        setLayout(new GridBagLayout());
        add(view); // 制約なし = 中央配置
        // 解析中は背後 UI を触らせない: イベントを握りつぶす空リスナ。
        addMouseListener(new MouseAdapter() { });
        addMouseMotionListener(new MouseAdapter() { });
        addKeyListener(new KeyAdapter() { });
    }

    /** ステータス文言を更新する。 */
    void setStatus(String message) {
        view.setStatus(message);
    }

    /** オーバーレイを表示する。 */
    void showOverlay() {
        setVisible(true);
    }

    /** オーバーレイを隠す。 */
    void hideOverlay() {
        setVisible(false);
    }

    @Override
    protected void paintComponent(Graphics g) {
        g.setColor(SCRIM);
        g.fillRect(0, 0, getWidth(), getHeight());
        super.paintComponent(g);
    }
}
