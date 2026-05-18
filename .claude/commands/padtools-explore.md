# /padtools-explore

PadTools と AOSP/AAOS を組み合わせた UML 図化・解析コマンド。

## 説明

AOSP または AAOS ソースツリーの構造を PadTools で可視化・解析するための統合コマンドです。
エージェント（戦略設計）とスキル（知識ベース）を自動的にロードして、ユーザゴールをサポートします。

## 使用方法

### 基本形

```
/padtools-explore <layer> <topic> [options]
```

### パラメータ

| パラメータ | 必須 | 値 | 説明 |
|---|---|---|---|
| `<layer>` | ○ | `aosp`, `aaos` | AOSP 汎用か AAOS 自動車向けか |
| `<topic>` | ○ | 下記参照 | 解析対象の領域 |
| `[--help]` | - | - | ヘルプ表示 |

### layer: AOSP (汎用)

```
/padtools-explore aosp --help
```

利用可能な topic:

| Topic | 用途 |
|---|---|
| `--build` | Soong / Android.bp / Gradle 依存 |
| `--partition` | system / vendor / product / odm 配置 |
| `--hal` | AIDL / HAL / HIDL インタフェース |
| `--sepolicy` | SELinux / sepolicy / 権限 |
| `--module <path>` | 任意 module をクラス図化 (パス指定) |
| `--method <Class.method>` | 特定メソッドのシーケンス図 |

### layer: AAOS (自動車向け)

```
/padtools-explore aaos --help
```

利用可能な topic:

| Topic | 用途 |
|---|---|
| `--carservice` | CarService / CarManager アーキテクチャ |
| `--vhal` | Vehicle HAL (IVehicle AIDL) |
| `--ui` | CarSystemUI / CarLauncher / RRO |
| `--security` | MultiUser / permissions / SELinux |
| `--property <propId>` | 特定 property のフロー (e.g., PERF_VEHICLE_SPEED) |
| `--manager <type>` | 特定 Manager (e.g., audio, occupant, property) |

## 例

### AOSP

```
# Soong / Android.bp について知りたい
/padtools-explore aosp --build

# frameworks/base のクラス図を出したい
/padtools-explore aosp --module frameworks/base/services/core

# CarService.onCreate の呼び出しチェーンを見たい
/padtools-explore aosp --method CarService.onCreate

# sepolicy のルールを調べたい
/padtools-explore aosp --sepolicy
```

### AAOS

```
# CarService 全体のアーキテクチャを理解したい
/padtools-explore aaos --carservice

# VHAL と CarPropertyService の通信フローを知りたい
/padtools-explore aaos --vhal

# MultiUser / ロール分離を理解したい
/padtools-explore aaos --security

# 特定 Vehicle Property (速度) の get/set フロー
/padtools-explore aaos --property PERF_VEHICLE_SPEED

# CarAudioManager のアーキテクチャ
/padtools-explore aaos --manager audio
```

## 動作フロー

1. **Layer と Topic を解析**: AOSP / AAOS、build / partition / HAL など
2. **関連エージェント・スキルを自動ロード**:
   - AOSP → `aosp-padtools-explorer` agent + `aosp-padtools-analyzer` skill
   - AAOS → `aaos-padtools-explorer` agent + `aaos-padtools-analyzer` skill
3. **ユーザゴールを親エージェントに伝達**: 「carservice のアーキテクチャを理解したい」など
4. **エージェントが解析ロジック設計を提案**:
   - どこを読むか (Grep / Read)
   - どの仮説を立てるか
   - PadTools のどのオプション (−c, −Q, −M, −G) をなぜ使うか
   - 期待される図・出力
5. **ユーザが親エージェントのコマンド提案を実行**: SVG / PNG / PUML 生成

## 出力規約

すべての応答は `/home/user/padtools/CLAUDE.md` に準拠:

```
## 変更サマリー
- **English phrase**: 日本語説明
  目的: なぜこれをしたか
```

## 関連スキル・エージェント

- **Agent**: `/root/.claude/agents/aosp-padtools-explorer.md`
- **Agent**: `/root/.claude/agents/aaos-padtools-explorer.md`
- **Skill**: `/root/.claude/skills/aosp-padtools-analyzer/`
- **Skill**: `/root/.claude/skills/aaos-padtools-analyzer/`

## トラブルシューティング

### "PadTools jar が見つからない"

```sh
cd /home/user/padtools && ./gradlew jar
```

### "AOSP path が見つからない"

エージェントが `~/AOSP/` を想定しています。別の場所の場合、コマンド実行時に path を指定してください。

### "jar メモリ不足 (OOM)"

エージェントが `-Xmx` オプション調整を提案します。

---

## 設計思想

**実行と設計の分業**:
- このコマンド + エージェント = 「何をするか」を設計
- 親エージェント / ユーザ = 実際に PadTools を「実行」

エージェントが提案するコマンドをコピペして実行することで、
AOSP/AAOS の複雑な構造を段階的に理解できます。
