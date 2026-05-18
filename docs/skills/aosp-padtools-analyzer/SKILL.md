---
name: aosp-padtools-analyzer
description: AOSP (Android Open Source Project) ソースツリーを PadTools で UML 図化する手順集。Soong/Android.bp、AIDL/HAL、partition (system/vendor/product/odm/boot/vbmeta)、SELinux sepolicy (.te) の解析・可視化依頼や、Treble/GKI/VINTF 関連の質問があるとき自動ロード。
---

# AOSP × PadTools スキル: 図化と解析

## 前提環境

### PadTools

- **jar ファイル**: `/home/user/padtools/build/libs/PadTools.jar`
  - 未ビルド場合: `cd /home/user/padtools && ./gradlew jar`
- **Java**: 17 以上 (JRE/JDK)
- **メモリ**: `-Xmx4g` (小規模), `-Xmx8g` 推奨, `-Xmx16g` (大規模)

### AOSP チェックアウト

- **慣例的パス**: `~/AOSP/` (ユーザ環境に応じて調整)
- **スペース確保**: 100GB 以上推奨 (フルツリー)
- **API レベル**: 2026 年時点での最新 (通常 API 35+)

---

## AOSP → PadTools 入力対応表

| 目標 | 図種 | オプション | 入力パス | 想定サイズ |
|---|---|---|---|---|
| **モジュール内クラス構造** | クラス図 | `-c` | `~/AOSP/frameworks/base/services/core` | 数千クラス |
| **Android ライフサイクル起点** | シーケンス図 | `-Q` | `~/AOSP/packages/services/Car` | 複数ファイル |
| **特定メソッドのコール chain** | シーケンス図 | `-q CarService.onStartUser` | `~/AOSP/packages/services/Car` | 指定エントリ |
| **パッケージ間依存** | パッケージ図 | (クラス図から自動生成) | クラス図と同じ | 俯瞰表示 |
| **Manifest コンポーネント** | Manifest 図 | `-M` | `~/AOSP/packages/apps/Settings` | Activity/Service |
| **モジュール依存** | Gradle 依存図 | `-G` | `~/AOSP/packages/services/Car` | 10～100 モジュール |
| **全量出力** | 全種類 | `--all` | 上記いずれか | 所要時間: 数分～数十分 |

---

## ワークフロー 1: Soong ビルドシステムを理解する

**目的**: Soong (`.bp` ファイル) で定義されたモジュール構成とその依存関係を可視化する。

### ステップ 1: モジュール候補一覧

```sh
find ~/AOSP/packages/services/Car -name "Android.bp" | head -20
```

### ステップ 2: モジュールの src をクラス図化

例: `packages/services/Car/service` の main module

```sh
java -Xmx8g -jar /home/user/padtools/build/libs/PadTools.jar \
  -c -o /tmp/aosp-out/build/car-service.svg \
  ~/AOSP/packages/services/Car/service/src
```

### ステップ 3: Gradle 依存図

```sh
java -jar /home/user/padtools/build/libs/PadTools.jar \
  -G -o /tmp/aosp-out/build/car-deps.svg \
  ~/AOSP/packages/services/Car
```

**出力読取ポイント**: モジュール間 `project(':x')` 依存。外部 Maven ライブラリ。

---

## ワークフロー 2: Partition / Image 構成を可視化する

**目的**: `system/`, `vendor/`, `product/`, `odm/` パーティション配置と APK/JAR 構成を理解する。

### ステップ 1: 各パーティション向けコンポーネント探索

```sh
# system partition の Settings app
find ~/AOSP/packages/apps/Settings/src -name "*.java" | wc -l

# vendor partition の HAL daemon 候補
find ~/AOSP/hardware/interfaces -name "*Impl.java" | head -10
```

### ステップ 2: パーティション別 APK/JAR を図化

例: system partition 向け Settings App

```sh
java -Xmx4g -jar /home/user/padtools/build/libs/PadTools.jar \
  -c -o /tmp/aosp-out/partition/settings-system.svg \
  ~/AOSP/packages/apps/Settings
```

### ステップ 3: Manifest から權限要件を把握

```sh
java -jar /home/user/padtools/build/libs/PadTools.jar \
  -M -o /tmp/aosp-out/partition/settings-manifest.svg \
  ~/AOSP/packages/apps/Settings
```

**出力読取**: コンポーネント (Activity/Service), exported 属性, uses-permission リスト。

---

## ワークフロー 3: HAL / AIDL / HIDL 境界を図にする

**目的**: Hardware Abstraction Layer (HAL) インタフェースと実装を可視化。

### ステップ 1: AIDL インタフェース発見

```sh
find ~/AOSP/hardware/interfaces -name "*.aidl" | grep -i audio | head -10
```

### ステップ 2: AIDL インタフェース図化

```sh
java -Xmx8g -jar /home/user/padtools/build/libs/PadTools.jar \
  -c -o /tmp/aosp-out/hal/audio-aidl.svg \
  ~/AOSP/hardware/interfaces/audio/aidl
```

### ステップ 3: HAL 実装 (default implementation)

```sh
find ~/AOSP -path "*/audio/impl/*.java" -o -path "*/audio/default/*.java" | head -5

# default impl をクラス図化
java -Xmx4g -jar /home/user/padtools/build/libs/PadTools.jar \
  -c -o /tmp/aosp-out/hal/audio-impl.svg \
  ~/AOSP/hardware/interfaces/audio/default
```

**出力読取**: AIDL インタフェース (Parcelable, interface), 実装クラス継承構造。

---

## ワークフロー 4: SELinux sepolicy を調査する

**目的**: SELinux ポリシー (`system/sepolicy/`) とアクセス制御ルールを理解する。

### 注記: PadTools の制約

PadTools は `.te` (sepolicy 拡張言語) を **直接パースしません**。
代わりに、sepolicy の影響を受ける Java サービスを図化し、Grep で ルール類似を探します。

### ステップ 1: 関連する sepolicy ファイル確認

```sh
grep -r "carservice" ~/AOSP/system/sepolicy/public/ | head -10
grep -r "neverallow" ~/AOSP/system/sepolicy/ | head -5
```

### ステップ 2: Java サービス側のセキュリティチェック箇所

```sh
grep -r "checkPermission\|enforcePermission" ~/AOSP/packages/services/Car/service/src | head -10
```

### ステップ 3: Java サービス図化

```sh
java -Xmx8g -jar /home/user/padtools/build/libs/PadTools.jar \
  -c -o /tmp/aosp-out/sepolicy/carservice-perm.svg \
  ~/AOSP/packages/services/Car/service/src
```

**読取**: 権限チェック呼び出し位置、呼び出し元クラス。

---

## CLI オプション一覧 (PadTools)

より詳細は `java -jar PadTools.jar --help` を参照:

```
-c, --class-diagram       クラス図 (SVG/PUML/PNG)
-q, --sequence-diagram    特定メソッドからのシーケンス図
-Q, --sequence-diagrams   Android ライフサイクルエントリ全量シーケンス
-d, --component-diagram   Android コンポーネント図
-M, --manifest-diagram    AndroidManifest 図
-G, --dependency-graph    Gradle モジュール依存図
--summary                 Markdown プロジェクトサマリー
--all                     全種類一括出力 (ディレクトリ指定)
-o FILE                   出力先ファイル / ディレクトリ
--seq-depth N             シーケンス図トレース深さ (デフォ 5, 0=無制限)
--list-methods            メソッド候補一覧 (起点選択用)
-v, --verbose             警告を stderr 出力
```

---

## トラブルシューティング

### OOM (Out of Memory)

```sh
# 増加させる
java -Xmx16g -jar PadTools.jar ...

# または、スコープ絞り込み
# → cheatsheet-build.md の Scope オプション参照
```

### 図が複雑すぎて読めない

- 最初から `--scope` や `--seq-depth 2` で削減
- パッケージ単位に分割実行

### 起点メソッドが見つからない

```sh
# 全メソッド一覧出力、fzf 等で検索
java -Xmx8g -jar PadTools.jar --list-methods ~/AOSP/packages/services/Car
```

---

## 関連チートシート

詳細は以下を参照:

- **Soong/Bazel/make**: [`cheatsheet-build.md`](cheatsheet-build.md)
- **Partition/Image**: [`cheatsheet-partition.md`](cheatsheet-partition.md)
- **HAL/AIDL/HIDL**: [`cheatsheet-hal.md`](cheatsheet-hal.md)
- **SELinux/sepolicy**: [`cheatsheet-sepolicy.md`](cheatsheet-sepolicy.md)

---

## 出力規約 (日本語サマリー)

各図化実行後は、以下の形式でサマリーを記述:

```
## 変更サマリー

- **<English phrase>**: <日本語説明>
  目的: <なぜこれをしたか>
```

例:

```
- **Generated Car Service class diagram**: CarService の クラス構成を可視化
  目的: CarLocalServices に登録される各 Manager の継承関係と依存を理解するため
```
