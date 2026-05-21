# PadTools ドキュメント

PadTools 2.0 のドキュメント置き場です。各ファイルは GitHub 上でそのまま閲覧できます。

## GUI 操作マニュアル

- [gui-manual.md](gui-manual.md) — 起動・プロジェクトを開く・ツールバー・左右ペイン・各図種の詳細操作・エクスポート・キーボードショートカット一覧までを網羅した GUI の使い方ガイド。

## Claude Code 連携バンドル（AOSP / AAOS 解析）

Claude Code から PadTools を使って AOSP / AAOS を解析するためのエージェント・スキル定義です。
`~/.claude/` への導入手順は [SETUP_CLAUDE_AGENTS_SKILLS.md](SETUP_CLAUDE_AGENTS_SKILLS.md) を参照してください。

### エージェント

- [agents/aosp-padtools-explorer.md](agents/aosp-padtools-explorer.md) — AOSP（Android Open Source Project）解析戦略の設計担当。
- [agents/aaos-padtools-explorer.md](agents/aaos-padtools-explorer.md) — AAOS（Android Automotive OS）解析担当（CarService / VHAL / CarSystemUI など）。

### スキル

- [skills/aosp-padtools-analyzer/SKILL.md](skills/aosp-padtools-analyzer/SKILL.md) — AOSP 解析スキル。
  cheatsheet: [build](skills/aosp-padtools-analyzer/cheatsheet-build.md) /
  [hal](skills/aosp-padtools-analyzer/cheatsheet-hal.md) /
  [sepolicy](skills/aosp-padtools-analyzer/cheatsheet-sepolicy.md) /
  [partition](skills/aosp-padtools-analyzer/cheatsheet-partition.md)
- [skills/aaos-padtools-analyzer/SKILL.md](skills/aaos-padtools-analyzer/SKILL.md) — AAOS 解析スキル。
  cheatsheet: [carservice](skills/aaos-padtools-analyzer/cheatsheet-carservice.md) /
  [vhal](skills/aaos-padtools-analyzer/cheatsheet-vhal.md) /
  [security](skills/aaos-padtools-analyzer/cheatsheet-security.md) /
  [ui-rro](skills/aaos-padtools-analyzer/cheatsheet-ui-rro.md)
