# /padtools-quick

PadTools のシンプルな直接実行コマンド。エージェント・スキル不要。

## 説明

AOSP/AAOS パスを指定して、直接 PadTools を実行したいときに使用。
図化コマンドを素早く生成します。

## 使用方法

```
/padtools-quick <diagram-type> <path> [memory]
```

### パラメータ

| パラメータ | 値 | 説明 |
|---|---|---|
| `<diagram-type>` | `class`, `seq`, `manifest`, `deps`, `summary`, `all`, `impact`, `ref-find` | 図/レポートの種類 |
| `<path>` | ファイル/ディレクトリ | AOSP 内のパス (相対 or 絶対) |
| `[memory]` | `4g`, `8g`, `16g` | メモリ設定 (デフォ 8g) |

### diagram-type の説明

| Type | オプション | 説明 |
|---|---|---|
| `class` | `-c` | クラス図 |
| `seq` | `-Q` | Android ライフサイクルシーケンス図 |
| `manifest` | `-M` | Manifest / コンポーネント図 |
| `deps` | `-G` | Gradle 依存図 |
| `summary` | `--summary` | Markdown サマリー |
| `all` | `--all` | 全種類一括出力 |
| `impact` | `--impact SYMBOL` | 逆参照影響レポート (Markdown + PlantUML)。SYMBOL は FQN もしくは FQN.method |
| `ref-find` | `--ref-find SYMBOL` | シンボルへの参照箇所 grep 互換テキスト出力 |

## 例

```
# 相対パス (カレント AOSP 下)
/padtools-quick class frameworks/base/services/core

# 絶対パス
/padtools-quick class ~/AOSP/packages/services/Car/service/src

# メモリ指定
/padtools-quick all ~/AOSP/hardware/interfaces/automotive/vehicle/aidl 16g

# Manifest 図
/padtools-quick manifest ~/AOSP/packages/apps/CarSystemUI

# Gradle 依存図
/padtools-quick deps ~/AOSP/packages/services/Car

# 影響範囲解析 (このクラスを消すと何が壊れる?)
/padtools-quick impact android.car.hardware.CarPropertyManager ~/AOSP/packages/services/Car

# 影響範囲解析 (メソッド単位)
/padtools-quick impact android.car.hardware.CarPropertyManager.getProperty ~/AOSP/packages/services/Car

# 参照箇所列挙 (grep 互換)
/padtools-quick ref-find android.car.hardware.CarPropertyManager.getProperty ~/AOSP/packages/services/Car
```

## 出力

生成されたコマンドラインをコピーして実行:

```sh
java -Xmx8g -jar /home/user/padtools/build/libs/PadTools.jar \
  -c -o /tmp/aosp-out/frameworks-core.svg \
  ~/AOSP/frameworks/base/services/core
```

---

**Note**: エージェント・スキルのような解析ロジック設計は含みません。
「とにかく図にしたい」「コマンドを教えてほしい」というときに使ってください。
