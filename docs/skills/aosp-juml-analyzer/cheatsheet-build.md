# AOSP Build System Cheatsheet (Soong, Bazel, make)

## envsetup.sh と lunch

```sh
# AOSP ルートから
source build/envsetup.sh

# ビルドターゲット一覧
lunch

# 特定ターゲット選択
lunch car_x86-userdebug
lunch aosp_car_arm64-userdebug
```

**ビルド環境変数設定**:
- `OUT_DIR`: ビルド出力ディレクトリ (デフォ `out/`)
- `TARGET_DEVICE`: ターゲット device (e.g., car_x86)
- `TARGET_BUILD_VARIANT`: userdebug, user, eng

---

## Soong (Android.bp)

### モジュール種別

| 種類 | 用途 | 出力 |
|---|---|---|
| `java_library` | 通常 Java ライブラリ | `.jar` |
| `android_library` | Android res/manifest 同梱 | `.aar` (ビルド時のみ、最終出力は jar) |
| `android_app` | APK (Activity/Service/etc.) | `.apk` |
| `cc_library` | C/C++ ライブラリ | `.so` |
| `cc_binary` | C/C++ 実行ファイル | 実行ファイル |
| `hidl_interface` | HIDL インタフェース (レガシ) | `.java` + stub |
| `aidl_interface` | AIDL インタフェース (現在推奨) | `.aidl` + Java lang binding |

### Android.bp 基本構文

```bp
// java_library 例
java_library {
    name: "framework-car",
    srcs: ["src/**/*.java"],
    libs: ["framework"],
    static_libs: ["androidx.car_car"],
}

// android_app 例
android_app {
    name: "CarSettings",
    package_name: "com.android.car.settings",
    srcs: ["src/**/*.java"],
    resource_dirs: ["res"],
    manifest: "AndroidManifest.xml",
    libs: ["framework"],
}
```

### Juml との連携

**重要**: `Android.bp` ファイル自体は Juml が解析しません。
**モジュールの src ディレクトリを入力に指定してください**:

```sh
# ✓ 正しい: src ディレクトリを指定
java -jar Juml.jar -c -o output.svg ~/AOSP/packages/services/Car/service/src

# ✗ 間違い: Android.bp ファイルを直接指定
# java -jar Juml.jar -c -o output.svg ~/AOSP/packages/services/Car/service/Android.bp
```

---

## Bazel (BUILD.bazel)

AOSP の一部モジュール (特に新規コンポーネント) は Bazel を採用しています。

### BUILD.bazel 基本例

```bazel
java_library(
    name = "car_lib",
    srcs = glob(["src/**/*.java"]),
    deps = [
        ":car_aidl",
        "//packages/services/Car/car-lib:car",
    ],
)

cc_library(
    name = "vehicle_hal",
    srcs = ["vehicle_hal.cpp"],
    header_libs = ["libhardware_headers"],
)
```

### Bazel → Juml

同様に、`srcs` で指定されたディレクトリを Juml に渡します。

---

## make (レガシ)

古い AOSP モジュールは `Android.mk` (make ベース) を使用。
API 34+ では大部分が Soong に移行済み。

### Android.mk 基本例

```makefile
LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := carservice
LOCAL_SRC_FILES := $(call all-java-files-under, src)
LOCAL_JAVA_LIBRARIES := framework android.car
include $(BUILD_JAVA_LIBRARY)
```

---

## Version Catalog (libs.versions.toml)

Gradle モジュール依存バージョン集約。

```toml
[versions]
androidx = "1.6.0"
kotlin = "1.8.0"

[libraries]
androidx-car = { group = "androidx.car", name = "car", version.ref = "androidx" }
kotlin-stdlib = { module = "org.jetbrains.kotlin:kotlin-stdlib", version.ref = "kotlin" }

[bundles]
androidx = ["androidx-car", "androidx-appcompat"]
```

### Juml: Gradle 依存図

```sh
java -jar Juml.jar -G -o deps.svg ~/AOSP/packages/services/Car
```

出力: モジュール間 `project(':x')` 依存 + Maven ライブラリ参照。

---

## ビルドコマンド

```sh
# モジュール単体ビルド
mm car_service

# モジュール + 依存
mma car_service

# フルビルド
m

# 出力確認
ls out/target/product/<device>/system/app/
ls out/target/product/<device>/vendor/lib/
```

---

## Soong モジュール探索 (Juml 入力候補)

```sh
# java_library / android_app 検索
find ~/AOSP -name "Android.bp" -exec grep -l "java_library\|android_app" {} \;

# 特定パッケージ配下のモジュール
grep -r "name.*:" ~/AOSP/packages/services/Car --include="Android.bp"

# AIDL/HIDL インタフェース
find ~/AOSP/hardware/interfaces -name "*.bp" -exec grep -l "aidl_interface\|hidl_interface" {} \;
```

---

## Juml --list-methods 活用

Soong モジュール内のメソッド候補を列挙:

```sh
java -Xmx8g -jar Juml.jar --list-methods ~/AOSP/packages/services/Car/service/src | head -20

# 出力例:
# CarService.onCreate
# CarService.onUserLifecycle
# CarServiceManager.setCarServiceReady
# ...
```

これらを `-q` で特定トレース。
