# Juml ドキュメント

Juml 2.0 のドキュメント置き場です。各ファイルは GitHub 上でそのまま閲覧できます。

## GUI 操作マニュアル

- [gui-manual.md](gui-manual.md) — 起動・プロジェクトを開く・ツールバー・左右ペイン・各図種の詳細操作・エクスポート・キーボードショートカット一覧までを網羅した GUI の使い方ガイド。
- [gui-manual.pdf](gui-manual.pdf) — 上記マニュアルの PDF 版（日本語フォント埋め込み）。

> **PDF の再生成**: `gui-manual.pdf` は `gui-manual.md` から生成された成果物です。マニュアルを
> 更新したら以下で再生成してください（日本語が文字化けしないよう日本語フォントを CSS で指定）。
>
> ```sh
> pip install weasyprint markdown
> python3 docs/build-pdf.py
> ```

## Claude Code 連携バンドル（AOSP / AAOS 解析）

Claude Code から Juml を使って AOSP / AAOS を解析するためのエージェント・スキル定義です。
`~/.claude/` への導入手順は [SETUP_CLAUDE_AGENTS_SKILLS.md](SETUP_CLAUDE_AGENTS_SKILLS.md) を参照してください。

### エージェント

- [agents/aosp-juml-explorer.md](agents/aosp-juml-explorer.md) — AOSP（Android Open Source Project）解析戦略の設計担当。
- [agents/aaos-juml-explorer.md](agents/aaos-juml-explorer.md) — AAOS（Android Automotive OS）解析担当（CarService / VHAL / CarSystemUI など）。

### スキル

- [skills/aosp-juml-analyzer/SKILL.md](skills/aosp-juml-analyzer/SKILL.md) — AOSP 解析スキル。
  cheatsheet: [build](skills/aosp-juml-analyzer/cheatsheet-build.md) /
  [hal](skills/aosp-juml-analyzer/cheatsheet-hal.md) /
  [sepolicy](skills/aosp-juml-analyzer/cheatsheet-sepolicy.md) /
  [partition](skills/aosp-juml-analyzer/cheatsheet-partition.md)
- [skills/aaos-juml-analyzer/SKILL.md](skills/aaos-juml-analyzer/SKILL.md) — AAOS 解析スキル。
  cheatsheet: [carservice](skills/aaos-juml-analyzer/cheatsheet-carservice.md) /
  [vhal](skills/aaos-juml-analyzer/cheatsheet-vhal.md) /
  [security](skills/aaos-juml-analyzer/cheatsheet-security.md) /
  [ui-rro](skills/aaos-juml-analyzer/cheatsheet-ui-rro.md)
