# Settings 屏 + 动态 API Key + 三 Provider 支持 — 交付

完整交付见:[/Users/spoon/.mavis/plans/plan_5d5f55fe/outputs/settings-and-multimodel/deliverable.md](../../../../.mavis/plans/plan_5d5f55fe/outputs/settings-and-multimodel/deliverable.md)

## TL;DR

`gradle :app:assembleDebug` **BUILD SUCCESSFUL**,产物:
`/Users/spoon/.mavis/sessions/mvs_a3d2601f34ca403392a0604e8bf5dc45/workspace/PlateScanner/app/build/outputs/apk/debug/app-debug.apk`
(28 MB)

## 改动文件清单

### 修改 (7 个)
- `gradle/libs.versions.toml` — 加 `datastore = "1.0.0"` + library entry
- `app/build.gradle.kts` — `implementation(libs.androidx.datastore.preferences)`
- `app/src/main/java/com/platescanner/app/di/AppModule.kt` — provide DataStore + inject SettingsRepository into MiniMaxApi
- `app/src/main/java/com/platescanner/app/network/MiniMaxApiImpl.kt` — 读 SettingsRepository;OkHttp 拦截器内 `runBlocking` 拿最新 key
- `app/src/main/java/com/platescanner/app/network/ApiClient.kt` — `create()` 加 SettingsRepository 参数
- `app/src/main/java/com/platescanner/app/ui/PlateScannerApp.kt` — NavHost 加 `composable("settings")`
- `app/src/main/java/com/platescanner/app/ui/screen/HomeScreen.kt` — 齿轮 IconButton + 设置按钮
- `app/src/main/java/com/platescanner/app/ui/screen/ScannerScreen.kt` — MissingKeyBanner + AlertDialog + Settings EntryPoint
- `app/src/main/res/values/strings.xml` — 14 条新 strings

### 新增 (4 个)
- `app/src/main/java/com/platescanner/app/network/ProviderPreset.kt`
- `app/src/main/java/com/platescanner/app/data/SettingsRepository.kt`
- `app/src/main/java/com/platescanner/app/ui/screen/settings/SettingsViewModel.kt`
- `app/src/main/java/com/platescanner/app/ui/screen/settings/SettingsScreen.kt`

## DataStore key 名

| Key | Preferences Key |
|-----|-----------------|
| API Key | `api_key` |
| 模型名 | `model_id` |
| Base URL | `base_url` |

文件:`plate_scanner_settings`(`Context.filesDir/datastore/plate_scanner_settings.preferences_pb`)

## ProviderPreset

| Provider | baseUrl | modelId |
|----------|---------|---------|
| MINIMAX | `https://api.minimax.chat` | `MiniMax-Text-01` |
| OPENAI | `https://api.openai.com/v1` | `gpt-4o-mini` |
| QWEN_VL | `https://dashscope.aliyuncs.com/compatible-mode/v1` | `qwen-vl-plus` |

鉴权 header 三家相同:`Authorization: Bearer <key>`,走 `/v1/chat/completions` 协议。

## 编译命令

```bash
export JAVA_HOME=/tmp/sdkboot/zulu17.50.19-ca-jdk17.0.11-macosx_aarch64
export ANDROID_HOME=/tmp/sdkboot/android-sdk
export PATH=$JAVA_HOME/bin:/tmp/sdkboot/gradle-8.5/bin:$ANDROID_HOME/platform-tools:$PATH
cd /Users/spoon/.mavis/sessions/mvs_a3d2601f34ca403392a0604e8bf5dc45/workspace/PlateScanner
gradle :app:assembleDebug --no-daemon
```

## APK 路径

`/Users/spoon/.mavis/sessions/mvs_a3d2601f34ca403392a0604e8bf5dc45/workspace/PlateScanner/app/build/outputs/apk/debug/app-debug.apk`

## 沙箱 install 验证

**跳过** — 沙箱无 Android 设备/模拟器。真机验证交给 task 2。

## 三 Provider 切换步骤(用户视角)

1. **Home → 设置**:右上角齿轮 IconButton,或底部 OutlinedButton「设置」。
2. **选 Provider**:点顶部三张 Provider 卡片之一,自动填 Base URL + 模型名。
3. **填 API Key**:在 API Key 输入框粘贴对应 Provider 的 key(密码掩码)。
4. **保存**:底部「保存」→ Snackbar「已保存」,立即返回 Home,下次扫描即用新 Provider。
5. **未配置 key**:ScannerScreen 顶部红色 banner + AlertDialog「去设置」一键跳转,相机预览仍可见,识别接口短路返回空。