# Claude Code エージェント・スキルセットアップ手順

Juml で AOSP/AAOS 解析を行う際に使用する Claude Code エージェント・スキルをセットアップします。

## セットアップ内容

以下をユーザホームの `~/.claude/` に配置:

```
~/.claude/
├── agents/
│   ├── aosp-juml-explorer.md      (汎用 AOSP 解析エージェント)
│   └── aaos-juml-explorer.md      (AAOS 専門エージェント)
└── skills/
    ├── aosp-juml-analyzer/        (AOSP リファレンス + チートシート)
    │   ├── SKILL.md
    │   ├── cheatsheet-build.md
    │   ├── cheatsheet-partition.md
    │   ├── cheatsheet-hal.md
    │   └── cheatsheet-sepolicy.md
    └── aaos-juml-analyzer/        (AAOS リファレンス + チートシート)
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
mkdir -p ~/.claude/skills/aosp-juml-analyzer
mkdir -p ~/.claude/skills/aaos-juml-analyzer
```

### 2. エージェント配置

Juml リポジトリから下記をコピー:

```sh
# AOSP agent
cp /home/user/juml/docs/agents/aosp-juml-explorer.md \
   ~/.claude/agents/

# AAOS agent
cp /home/user/juml/docs/agents/aaos-juml-explorer.md \
   ~/.claude/agents/
```

### 3. AOSP スキル配置

```sh
# SKILL.md
cp /home/user/juml/docs/skills/aosp-juml-analyzer/SKILL.md \
   ~/.claude/skills/aosp-juml-analyzer/

# Cheatsheets
cp /home/user/juml/docs/skills/aosp-juml-analyzer/cheatsheet-*.md \
   ~/.claude/skills/aosp-juml-analyzer/
```

### 4. AAOS スキル配置

```sh
# SKILL.md
cp /home/user/juml/docs/skills/aaos-juml-analyzer/SKILL.md \
   ~/.claude/skills/aaos-juml-analyzer/

# Cheatsheets
cp /home/user/juml/docs/skills/aaos-juml-analyzer/cheatsheet-*.md \
   ~/.claude/skills/aaos-juml-analyzer/
```

### 5. 確認

```sh
ls -la ~/.claude/agents/
ls -la ~/.claude/skills/aosp-juml-analyzer/
ls -la ~/.claude/skills/aaos-juml-analyzer/
```

## 自動セットアップスクリプト (推奨)

リポジトリにセットアップスクリプトが含まれている場合:

```sh
bash /home/user/juml/scripts/setup-claude-agents.sh
```

## 検証

Claude Code を起動して以下を試す:

```
# AOSP 汎用
/juml-explore aosp --build

# AAOS
/juml-explore aaos --carservice

# リファレンス
/aosp-help build
/aaos-help carservice
```

エージェント・スキルが自動ロードされることを確認。

## トラブルシューティング

### エージェント・スキルが見つからない

```sh
# 配置を確認
ls ~/.claude/agents/aosp-juml-explorer.md
ls ~/.claude/skills/aosp-juml-analyzer/SKILL.md

# 見つからない場合は上記セットアップを再実行
```

### Slash command が動作しない

```sh
# .claude/commands/ の slash command は Juml プロジェクト固有
# → Juml ディレクトリで Claude Code を起動
cd /home/user/juml
code .
```

## 環境変数 (オプション)

AOSP パスがデフォルト `~/AOSP/` でない場合:

```sh
export AOSP_PATH="/mnt/custom/aosp"
```

エージェントが起動時にこれを参照。

---

**Note**: このセットアップにより、Juml を複数プロジェクトで活用できるようになります。
