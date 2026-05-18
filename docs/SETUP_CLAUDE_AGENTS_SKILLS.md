# Claude Code エージェント・スキルセットアップ手順

PadTools で AOSP/AAOS 解析を行う際に使用する Claude Code エージェント・スキルをセットアップします。

## セットアップ内容

以下をユーザホームの `~/.claude/` に配置:

```
~/.claude/
├── agents/
│   ├── aosp-padtools-explorer.md      (汎用 AOSP 解析エージェント)
│   └── aaos-padtools-explorer.md      (AAOS 専門エージェント)
└── skills/
    ├── aosp-padtools-analyzer/        (AOSP リファレンス + チートシート)
    │   ├── SKILL.md
    │   ├── cheatsheet-build.md
    │   ├── cheatsheet-partition.md
    │   ├── cheatsheet-hal.md
    │   └── cheatsheet-sepolicy.md
    └── aaos-padtools-analyzer/        (AAOS リファレンス + チートシート)
        ├── SKILL.md
        ├── cheatsheet-carservice.md
        ├── cheatsheet-vhal.md
        ├── cheatsheet-ui-rro.md
        └── cheatsheet-security.md
```

## 手動セットアップ

### 1. ディレクトリ作成

```sh
mkdir -p ~/.claude/agents
mkdir -p ~/.claude/skills/aosp-padtools-analyzer
mkdir -p ~/.claude/skills/aaos-padtools-analyzer
```

### 2. エージェント配置

PadTools リポジトリから下記をコピー:

```sh
# AOSP agent
cp /home/user/padtools/docs/agents/aosp-padtools-explorer.md \
   ~/.claude/agents/

# AAOS agent
cp /home/user/padtools/docs/agents/aaos-padtools-explorer.md \
   ~/.claude/agents/
```

### 3. AOSP スキル配置

```sh
# SKILL.md
cp /home/user/padtools/docs/skills/aosp-padtools-analyzer/SKILL.md \
   ~/.claude/skills/aosp-padtools-analyzer/

# Cheatsheets
cp /home/user/padtools/docs/skills/aosp-padtools-analyzer/cheatsheet-*.md \
   ~/.claude/skills/aosp-padtools-analyzer/
```

### 4. AAOS スキル配置

```sh
# SKILL.md
cp /home/user/padtools/docs/skills/aaos-padtools-analyzer/SKILL.md \
   ~/.claude/skills/aaos-padtools-analyzer/

# Cheatsheets
cp /home/user/padtools/docs/skills/aaos-padtools-analyzer/cheatsheet-*.md \
   ~/.claude/skills/aaos-padtools-analyzer/
```

### 5. 確認

```sh
ls -la ~/.claude/agents/
ls -la ~/.claude/skills/aosp-padtools-analyzer/
ls -la ~/.claude/skills/aaos-padtools-analyzer/
```

## 自動セットアップスクリプト (推奨)

リポジトリにセットアップスクリプトが含まれている場合:

```sh
bash /home/user/padtools/scripts/setup-claude-agents.sh
```

## 検証

Claude Code を起動して以下を試す:

```
# AOSP 汎用
/padtools-explore aosp --build

# AAOS
/padtools-explore aaos --carservice

# リファレンス
/aosp-help build
/aaos-help carservice
```

エージェント・スキルが自動ロードされることを確認。

## トラブルシューティング

### エージェント・スキルが見つからない

```sh
# 配置を確認
ls ~/.claude/agents/aosp-padtools-explorer.md
ls ~/.claude/skills/aosp-padtools-analyzer/SKILL.md

# 見つからない場合は上記セットアップを再実行
```

### Slash command が動作しない

```sh
# .claude/commands/ の slash command は PadTools プロジェクト固有
# → PadTools ディレクトリで Claude Code を起動
cd /home/user/padtools
code .
```

## 環境変数 (オプション)

AOSP パスがデフォルト `~/AOSP/` でない場合:

```sh
export AOSP_PATH="/mnt/custom/aosp"
```

エージェントが起動時にこれを参照。

---

**Note**: このセットアップにより、PadTools を複数プロジェクトで活用できるようになります。
