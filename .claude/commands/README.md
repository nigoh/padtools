# PadTools Claude Code Slash Commands

PadTools × AOSP/AAOS 解析の統合 slash command セット。
エージェント・スキルを活用した戦略設計から、クイック図化まで。

## コマンド一覧

### 1. `/padtools-explore` — 統合解析コマンド 🎯

**用途**: AOSP/AAOS の戦略設計が必要なとき

AOSP または AAOS ソースツリーについて「理解したい」「分析したい」という**ゴール**を入力。
エージェント（`aosp-padtools-explorer` / `aaos-padtools-explorer`）が解析ロジックを設計し、
どこを見るか、どのオプション使うか、期待される出力は何かを日本語で提案します。

```
# AOSP: Soong について知りたい
/padtools-explore aosp --build

# AAOS: CarService アーキテクチャを理解したい
/padtools-explore aaos --carservice

# AAOS: CarPropertyManager のフロー
/padtools-explore aaos --manager property
```

**特徴**:
- ✅ エージェント自動ロード
- ✅ 複雑な解析は戦略設計で対応
- ✅ 実行コマンドも提案
- ⏱ 時間がかかる可能性 (深く考える)

---

### 2. `/padtools-quick` — クイック図化 ⚡

**用途**: とにかく図を出したいとき

パスと図種を指定して、PadTools コマンドラインを即座に生成。
エージェント・スキルは使わず、直接実行コマンドのみ。

```
# クラス図
/padtools-quick class ~/AOSP/frameworks/base/services/core

# 全種類一括出力
/padtools-quick all ~/AOSP/packages/services/Car 8g

# Gradle 依存図
/padtools-quick deps ~/AOSP/packages/services/Car
```

**特徴**:
- ✅ 高速 (1 秒以内に返答)
- ✅ コマンド確認→コピペで実行可能
- ⏱ 解析ロジック設計なし

---

### 3. `/aosp-help` — AOSP リファレンス 📚

**用途**: AOSP 知識を得たい、リファレンスを引きたい

Soong / partition / HAL / sepolicy の各トピック別クイックリファレンス。
スキル (`aosp-padtools-analyzer`) のチートシートから関連セクションを引用。

```
# Soong とは？
/aosp-help build

# Treble partition 分離について詳しく知りたい
/aosp-help partition "Treble の利点"

# avc denied デバッグ方法
/aosp-help sepolicy "avc denied が出たときはどうする？"
```

**特徴**:
- ✅ 即時回答 (高速)
- ✅ リファレンス形式
- ✅ スキル自動ロード
- ⏱ 実行コマンドは含まない

---

### 4. `/aaos-help` — AAOS リファレンス 📚

**用途**: AAOS アーキテクチャを学びたい、リファレンスを引きたい

CarService / VHAL / UI / Security の各トピック別クイックリファレンス。
スキル (`aaos-padtools-analyzer`) のチートシートから関連セクションを引用。

```
# CarPropertyManager のアーキテクチャ
/aaos-help carservice "CarPropertyManager"

# VHAL subscribe パターン
/aaos-help vhal "subscribe で値変更受け取り"

# MultiUser ロール分離
/aaos-help security "driver vs passenger の権限"
```

**特徴**:
- ✅ 即時回答 (高速)
- ✅ AAOS 特化リファレンス
- ✅ スキル自動ロード
- ⏱ AOSP 基礎知識も必要 (→ `/aosp-help` 併用)

---

## 使い分けガイド

### 「○○ を理解したい」 → `/padtools-explore`

```
Q: "CarService の起動シーケンスを理解したい"
→ /padtools-explore aaos --carservice
↓ (エージェント考える)
A: "仮説 A: …, 仮説 B: … を検証するため、以下のステップを提案:
   1. CarService クラス図を生成 (-c で CarService.java)
   2. ライフサイクルシーケンス図 (-Q で onCreate)
   3. 期待される出力: CarLocalServices 登録順序が見える"
```

### 「とにかく図にして」 → `/padtools-quick`

```
Q: "frameworks/base のクラス図"
→ /padtools-quick class ~/AOSP/frameworks/base/services/core
↓ (即時返答)
A: "java -Xmx8g -jar ... -c -o class.svg ~/AOSP/... (コピペで OK)"
```

### 「仕組みを知りたい」 → `/aosp-help` or `/aaos-help`

```
Q: "Soong って何？"
→ /aosp-help build
↓ (スキル参照)
A: "[Soong は…] [Android.bp とは…] [PadTools との連携は…]"
```

---

## ワークフロー例

### AAOS: CarService の Audio フロー理解

```
ユーザ: "/padtools-explore aaos --carservice"
  ↓
エージェント: "CarAudioService と CarPropertyService の連携を確認するため、
  step 1: CarService クラス図 (-c)
  step 2: CarAudioManager.setVolume の呼び出しチェーン (-q)
  を提案"
  ↓
ユーザ: "コマンドコピペ + 実行"
  ↓
図が生成される
  ↓ (図から習得したら)
ユーザ: "/aaos-help carservice 'CarAudioService の仕組み'"
  ↓
スキル: "CarAudioService は…" (詳細リファレンス)
```

---

## エージェント・スキル リファレンス

| 要素 | 場所 | 用途 |
|---|---|---|
| **Agent: AOSP** | `/root/.claude/agents/aosp-padtools-explorer.md` | 汎用 AOSP 戦略設計 |
| **Agent: AAOS** | `/root/.claude/agents/aaos-padtools-explorer.md` | AAOS 戦略設計 |
| **Skill: AOSP** | `/root/.claude/skills/aosp-padtools-analyzer/` | Soong / Partition / HAL / SELinux |
| **Skill: AAOS** | `/root/.claude/skills/aaos-padtools-analyzer/` | CarService / VHAL / UI / Security |

---

## トラブルシューティング

### Q: PadTools jar が見つからない

```sh
cd /home/user/padtools && ./gradlew jar
```

### Q: AOSP path が見つからない

コマンド実行時、エージェントが `~/AOSP/` を想定しています。
別の場所なら、slash command で full path 指定:

```
/padtools-quick class /mnt/aosp-r/frameworks/base/services/core
```

### Q: 出力ファイル どこに生成される？

デフォルト: `/tmp/aosp-out/` または `/tmp/aaos-out/`

コマンド内で `-o` オプションで出力先変更可能。

### Q: OOM (Out of Memory)

メモリ指定を増やす:

```
/padtools-quick class <path> 16g
```

---

## 出力規約

すべての応答は `/home/user/padtools/CLAUDE.md` に準拠:

```
## 変更サマリー
- **English phrase**: 日本語説明
  目的: なぜこれをしたか
```

---

**Created**: 2026-05-18  
**Last Updated**: 2026-05-18
