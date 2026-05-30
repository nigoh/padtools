---
name: aaos-juml-analyzer
description: Android Automotive OS (AAOS) ソースツリーを Juml で UML 図化する手順集。CarService/CarManager、Vehicle HAL (VHAL)、CarSystemUI/RRO、MultiUser/権限分離の解析・可視化依頼があるとき自動ロード。
---

# AAOS × Juml スキル: 自動車向け図化と解析

## 前提環境

### Juml (汎用 AOSP と同じ)

- **jar**: `/home/user/juml/build/libs/Juml.jar`
- **Java**: 17 以上
- **メモリ**: `-Xmx4g` 小規模, `-Xmx8g` 推奨

### AAOS ソース

- **パス**: `~/AOSP/` (汎用と同じ慣例)
- **モジュール**:
  - `packages/services/Car/` (CarService + CarManager)
  - `packages/apps/CarSystemUI/` (システムUI)
  - `packages/apps/CarLauncher/` (ホームランチャー)
  - `hardware/interfaces/automotive/vehicle/` (VHAL)
  - `system/carservice/` (AAOS 統合)

---

## AAOS → Juml 入力対応表

| 目標 | 図種 | オプション | 入力パス | 焦点 |
|---|---|---|---|---|
| **CarService 構成** | クラス図 | `-c` | `packages/services/Car/service/src` | CarLocalServices, Manager 各種 |
| **VHAL IVehicle** | クラス図 | `-c` | `hardware/interfaces/automotive/vehicle/aidl` | AIDL interface + VehicleProperty |
| **CarSystemUI コンポーネント** | Component図 | `-M` | `packages/apps/CarSystemUI` | Activity/Service, permissions |
| **Car モジュール依存** | Gradle図 | `-G` | `packages/services/Car` | module dependencies |
| **CarPropertyManager フロー** | シーケンス図 | `-q CarPropertyManager.getProperty` | `packages/services/Car/service/src` | HAL call chain |

---

## ワークフロー 1: CarService アーキテクチャを理解する

**目的**: CarService が CarLocalServices 経由で各 CarManager を起動・管理する構造を可視化。

### ステップ 1: CarService モジュール構成確認

```sh
ls -la ~/AOSP/packages/services/Car/
find ~/AOSP/packages/services/Car -name "Android.bp" | head -10
```

### ステップ 2: CarService クラス図

```sh
java -Xmx8g -jar /home/user/juml/build/libs/Juml.jar \
  -c -o /tmp/aaos-out/carservice/carservice-impl.svg \
  ~/AOSP/packages/services/Car/service/src
```

**読取ポイント**:
- CarServiceImpl / CarLocalServices
- Manager 各種 (CarPropertyManager, CarAudioManager, CarOccupantZoneManager など)
- 継承階層, 依存関係

### ステップ 3: CarService ライフサイクル

```sh
# メソッド候補抽出
java -Xmx8g -jar /home/user/juml/build/libs/Juml.jar \
  --list-methods ~/AOSP/packages/services/Car/service/src | grep -i "onSystemBootPhase\|onCarServiceReady"

# onCreate / onUserLifecycle など AAOS エントリ
java -Xmx8g -jar /home/user/juml/build/libs/Juml.jar \
  -q "CarService.onCreate" -o /tmp/aaos-out/carservice/onCreate-seq.svg \
  ~/AOSP/packages/services/Car/service/src
```

**読取**: 起動フロー、Manager 初期化の順序。

### ステップ 4: Car モジュール依存

```sh
java -jar /home/user/juml/build/libs/Juml.jar \
  -G -o /tmp/aaos-out/carservice/car-deps.svg \
  ~/AOSP/packages/services/Car
```

**読取**: service, car-lib, aidl modules の関係。

---

## ワークフロー 2: Vehicle HAL (VHAL) を理解する

**目的**: IVehicle AIDL インタフェース、VehicleProperty enum、CarPropertyService との通信を可視化。

### ステップ 1: Vehicle HAL AIDL 構成

```sh
find ~/AOSP/hardware/interfaces/automotive/vehicle/aidl -name "*.aidl" | head -10
```

### ステップ 2: IVehicle インタフェース図化

```sh
java -Xmx4g -jar /home/user/juml/build/libs/Juml.jar \
  -c -o /tmp/aaos-out/vhal/ivehicle-aidl.svg \
  ~/AOSP/hardware/interfaces/automotive/vehicle/aidl
```

**読取ポイント**:
- IVehicle interface メソッド (get, set, subscribe, unsubscribe)
- VehicleProperty enum (各 property code)
- Parcelable 型 (VehiclePropertyValue, VehiclePropConfig など)

### ステップ 3: CarPropertyService との連携

```sh
# CarPropertyService 実装確認
find ~/AOSP/packages/services/Car -name "CarPropertyService.java"

# CarPropertyService のシーケンス図
java -Xmx8g -jar /home/user/juml/build/libs/Juml.jar \
  -q "CarPropertyService.getProperty" \
  -o /tmp/aaos-out/vhal/property-get-seq.svg \
  ~/AOSP/packages/services/Car/service/src
```

**読取**: getProperty → IVehicle.get() への call chain。

### ステップ 4: Property subscribe 流れ

```sh
java -Xmx8g -jar /home/user/juml/build/libs/Juml.jar \
  -q "CarPropertyService.registerListener" \
  -o /tmp/aaos-out/vhal/property-subscribe-seq.svg \
  ~/AOSP/packages/services/Car/service/src
```

**読取**: subscribe オブザーバパターン。

---

## ワークフロー 3: CarSystemUI と RRO をを理解する

**目的**: CarSystemUI コンポーネント、Runtime Resource Overlay (RRO) による UI カスタマイズ。

### ステップ 1: CarSystemUI 構成

```sh
ls -la ~/AOSP/packages/apps/CarSystemUI/
find ~/AOSP/packages/apps/CarSystemUI -name "*.java" | head -20
```

### ステップ 2: CarSystemUI コンポーネント図

```sh
java -Xmx4g -jar /home/user/juml/build/libs/Juml.jar \
  -M -o /tmp/aaos-out/ui/carsystemui-manifest.svg \
  ~/AOSP/packages/apps/CarSystemUI
```

**読取**:
- Activity / Service / Receiver / Provider
- exported 属性
- uses-permission リスト

### ステップ 3: CarSystemUI クラス図

```sh
java -Xmx4g -jar /home/user/juml/build/libs/Juml.jar \
  -c -o /tmp/aaos-out/ui/carsystemui-class.svg \
  ~/AOSP/packages/apps/CarSystemUI/src
```

**読取**: Fragment, View, Controller の構成。

### ステップ 4: RRO (Runtime Resource Overlay) 確認

```sh
# RRO manifest 確認
find ~/AOSP -path "*rro*" -name "AndroidManifest.xml" | head -5

# RRO リソース構成
find ~/AOSP/packages/apps/CarSystemUI -path "*/rro/*" -o -path "*/res/*" | grep "values-"
```

**RRO とは**: OEM がテーマ色・フォント等のリソースをオーバーレイして置き換え。

### ステップ 5: 複数 Display 対応確認

```sh
grep -r "getDisplayContext\|getDisplayId" \
  ~/AOSP/packages/apps/CarSystemUI/src | head -10

grep -r "DisplayManager\|VirtualDisplay" \
  ~/AOSP/packages/apps/CarSystemUI/src | head -10
```

**読取**: 複数 display (instrument, cluster) への UI 配置。

---

## ワークフロー 4: MultiUser / Permission を理解する

**目的**: Car.PERMISSION_* 権限体系、driver/passenger/guest ロール分離、CarOccupantZone。

### ステップ 1: Car Permission 定義確認

```sh
grep -r "Car.PERMISSION" ~/AOSP/packages/services/Car/service/src | head -10

# 定義ファイル
find ~/AOSP -name "*.aidl" -o -name "*.java" | xargs grep "PERMISSION_.*=" | grep -i "car\|occupant" | head -20
```

### ステップ 2: CarOccupantZone / User mapping

```sh
grep -r "CarOccupantZone\|getUserId\|getOccupantZone" \
  ~/AOSP/packages/services/Car/service/src | head -20

# CarOccupantZoneManager 確認
java -Xmx4g -jar /home/user/juml/build/libs/Juml.jar \
  -q "CarOccupantZoneManager.getOccupantZones" \
  -o /tmp/aaos-out/security/occupantzone-seq.svg \
  ~/AOSP/packages/services/Car/service/src
```

**読取**: User ID → Occupant Zone (driver/passenger/rear) マッピング。

### ステップ 3: Permission check 地点

```sh
grep -r "checkPermission\|enforcePermission" \
  ~/AOSP/packages/services/Car/service/src \
  | grep -i "property\|audio\|permission" | head -10
```

### ステップ 4: CarService 権限ポリシー図化

```sh
java -Xmx8g -jar /home/user/juml/build/libs/Juml.jar \
  -c -o /tmp/aaos-out/security/carservice-perm.svg \
  ~/AOSP/packages/services/Car/service/src
```

**読取**: 権限チェック呼び出し位置、関連クラス。

### ステップ 5: AAOS sepolicy 確認

```sh
# AAOS 固有ルール
find ~/AOSP/system/sepolicy -name "*carservice*" -o -name "*vehicle*"

grep -r "carservice\|vehicle_hal" ~/AOSP/system/sepolicy --include="*.te"

grep -r "Car.PERMISSION" ~/AOSP/vendor/sepolicy 2>/dev/null || echo "OEM カスタム sepolicy"
```

---

## CLI コマンド集 (AAOS 特化)

### CarService 全量出力

```sh
java -Xmx8g -jar /home/user/juml/build/libs/Juml.jar \
  --all -o /tmp/aaos-out/carservice-full \
  ~/AOSP/packages/services/Car/service/src
```

出力: class-diagram.svg, component-diagram.svg, manifest-diagram.svg, dependency-graph.svg, methods.txt。

### VHAL 全量出力

```sh
java -Xmx4g -jar /home/user/juml/build/libs/Juml.jar \
  --all -o /tmp/aaos-out/vhal-full \
  ~/AOSP/hardware/interfaces/automotive/vehicle/aidl
```

### 起点メソッド列挙

```sh
# CarService ライフサイクルエントリ
java -Xmx8g -jar /home/user/juml/build/libs/Juml.jar \
  --list-methods ~/AOSP/packages/services/Car/service/src | grep -i "onCreate\|onCarServiceReady\|onSystemBootPhase"
```

---

## AAOS トポロジ図 (脳内イメージ)

```
system/
  ├── frameworks/base/services/
  │   └── SystemServer (init CarService)
  ├── packages/services/Car/
  │   ├── CarService (main service)
  │   └── service/src/ (CarLocalServices + Manager 各種)
  └── sepolicy/
      └── carservice.te

vendor/
  └── hardware/interfaces/automotive/vehicle/
      └── aidl/ (IVehicle interface)

packages/
  ├── apps/CarSystemUI/  (UI layer)
  ├── apps/CarLauncher/
  └── services/Car/
      ├── car-lib/  (client API)
      └── service/  (daemon)
```

### 通信フロー

```
CarManagerClient (framework)
  ↓ (Binder / method call)
CarManager (various)
  ↓
CarLocalServices
  ↓
CarService
  ↓
CarPropertyService
  ↓ (AIDL)
VHAL daemon (hardware/interfaces/automotive/vehicle/aidl)
  ↓
Native HAL implementation (C++)
  ↓
Hardware (kernel driver)
```

---

## 関連チートシート

汎用 AOSP スキルも参照:

- **Build / Soong / Gradle**: [`../aosp-juml-analyzer/cheatsheet-build.md`](../aosp-juml-analyzer/cheatsheet-build.md)
- **Partition 配置**: [`../aosp-juml-analyzer/cheatsheet-partition.md`](../aosp-juml-analyzer/cheatsheet-partition.md)
- **HAL / AIDL / HIDL**: [`../aosp-juml-analyzer/cheatsheet-hal.md`](../aosp-juml-analyzer/cheatsheet-hal.md)
- **SELinux / sepolicy**: [`../aosp-juml-analyzer/cheatsheet-sepolicy.md`](../aosp-juml-analyzer/cheatsheet-sepolicy.md)

AAOS 固有 cheatsheet:

- **CarService / CarManager**: [`cheatsheet-carservice.md`](cheatsheet-carservice.md)
- **Vehicle HAL**: [`cheatsheet-vhal.md`](cheatsheet-vhal.md)
- **CarSystemUI / RRO**: [`cheatsheet-ui-rro.md`](cheatsheet-ui-rro.md)
- **MultiUser / Security**: [`cheatsheet-security.md`](cheatsheet-security.md)

---

## 出力規約 (日本語サマリー)

各図化実行後:

```
## 変更サマリー

- **<English phrase>**: <日本語説明>
  目的: <なぜこれをしたか>
```

例:

```
- **Generated CarService architecture diagram**: CarService の CarLocalServices + Manager 管理体制を可視化
  目的: 起動時の Manager 初期化順序と依存関係を理解するため
  
- **Generated VHAL IVehicle interface diagram**: IVehicle AIDL インタフェース と VehicleProperty の構成を把握
  目的: CarPropertyService からの get/set/subscribe フロー理解の前提
```
