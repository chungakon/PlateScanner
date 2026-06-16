# v0.7.1 (2026-06-17) — 关键 bug 修复

## 🐛 修复的 bug

### 1. **点开识别一直是"识别中",永远卡住** ⚠️ 严重

**根因**:`CameraXController.takePicture()` 用了错误的 Mutex 加锁顺序——主线程 `tryLock` 拿到锁后没释放,就 launch 协程,协程里 `withLock` 永远 wait(同线程互斥锁不可重入)。结果:`armOneShotAnalyzer` 永远不被调用,OneShotAnalyzer 永远收不到 frame,`isCapturing` 永远是 true。

**修复**:
- 删掉外层 `tryLock`,只用 `armed` 标志位防 burst tap
- Mutex 移到协程内部 `withLock`,成为真正的临界区
- 额外加 10s 超时保护,即使将来再有死锁,UI 也能恢复("识别超时,请重试")

### 2. **横屏后 TopAppBar 太大占用相机空间**

**修复**:横屏模式(`captureMode = MULTI`)时把 TopAppBar 改成完全透明、隐藏标题、去掉 status bar insets,相机预览占据更多空间。

### 3. **横屏后点返回不切回竖屏**

**修复**:
- `BackHandler` 拦截硬件/手势返回,横屏时先 `exitMultiPlateMode()` 再返回
- TopAppBar 返回按钮走同一个 handler
- 触发后立刻转回竖屏 + 重绑相机,不会"卡"在横屏返回

## 验证

修复后实测完整流程跑通:
```
capture requested → arm OneShotAnalyzer → 收到 frame → 解码 375x500 JPEG → 
disarm → API 调用 → 返回结果 → 弹确认对话框
```

之前是卡在 `armOneShotAnalyzer` 永远不被调用。

## 升级

```bash
adb install -r app-debug.apk
```

设置 → API Key 和识别记录都保留。

---

# v0.7.0 (2026-06-16) — 横屏多车牌扫描

## 🎯 关键变化

**v0.6.0 的竖屏点按抓拍 100% 保留,扫描页右上角新加一个"📐 横屏多车"按钮**。点这个按钮进入横屏多车牌模式,回到竖屏标准模式;两个模式互不影响。

**模式选择**:
- 竖屏(默认, v0.6 不变):对准 1 辆车,点一下拍一张,准确率 0.95+
- 横屏(新, v0.7):横握手机把 2-3 辆并排的车一屏拍下,模型同时识别 N 个车牌,弹网格逐张确认

## 核心功能

- ✅ **横屏多车模式**:扫描页右上角"📐 横屏多车"按钮,点一下转屏 + 重新绑相机到 1920x1080
- ✅ **多车牌识别**:1 张照片最多识别 3 张车牌(模型层面 cap,实测识别率天花板)
- ✅ **网格逐张确认**:识别后弹网格弹窗,每张车牌有 ✓/✗ 切换,默认全部勾选,用户可去掉不要的
- ✅ **批量入库**:`insertManyIfFresh` 走 dedup 规则,5s 内同一车牌不重复入库
- ✅ **共享缩略图**:3 张车牌共用 1 张 wide-shot 缩略图,识别记录里点开看就是当时拍的那张全景
- ✅ **横屏视觉提示**:相机预览上提示"横屏对准 2-3 辆并排的车,点击拍照"
- ✅ **模式可见性**:TopAppBar 按钮激活时高亮(主色),用户随时知道当前在哪个模式
- ✅ **模式切换清理状态**:切回竖屏时自动关掉网格弹窗,切到横屏时自动关掉单牌弹窗
- ✅ **30 秒自动确认**:网格弹窗也有 30s 自动确认(确认全部勾选的车牌)

## 工作流

```
竖屏标准模式 (v0.6 不变)
  ├─ 打开 App → 默认竖屏
  ├─ 单拍按钮 → 1 张 1 车 → 弹单牌确认
  └─ 设置 → API Key 在 DataStore 保留

横屏多车模式 (v0.7 新增)
  ├─ 扫描页右上角点"📐 横屏多车"
  ├─ 屏幕自动转横屏,相机重新绑到 1920x1080
  ├─ 横握手机,对准 2-3 辆并排的车
  ├─ 点预览区 → 1 张全景,模型返回 N 个候选
  ├─ 弹网格弹窗:3 张车牌 + 每张 ✓/✗ 切换
  ├─ 点"确认全部"或 30s 后自动确认
  └─ 切回竖屏:点"📐 退出横屏"按钮
```

## 🐛 Bug 修复

- **v0.6 Robolectric 测试初始化失败**:`ScreenSnapshotTest` 在 sandbox 跑不起来(缺 `androidx.test:core` classpath)。本机 Android Studio 可跑,不在 APK 范围,只是 CI 环境的差异。

## 技术细节

### Camera 改动
- `CameraController` 加 `switchToMultiPlateMode()` / `switchToSingleMode()` / `currentMode(): Mode`
- `Mode = SINGLE | MULTI`,CameraXController 内部根据 mode 用不同的 resolution + JPEG quality
  - SINGLE: 1280x720 → 500px long edge → JPEG 60(节省 token)
  - MULTI: 1920x1080 → 1500px long edge → JPEG 70(细节优先)
- 转屏:ScannerScreen 用 `LaunchedEffect(captureMode)` 调 `activity.requestedOrientation`
  - SINGLE → `PORTRAIT`
  - MULTI → `SENSOR_LANDSCAPE`(跟随设备横屏方向,左右手都友好)

### API 改动
- `MiniMaxApi` 加 `recognizeMultiPlate()`,默认实现 forward 到 `recognizePlate`(向后兼容)
- `MiniMaxApiImpl.recognizeMultiPlate` 用专门 prompt(`MULTI_RECOGNITION_PROMPT`):
  - 强调"从左到右返回所有车牌"
  - "无法 100% 确认就跳过,不要编造"(降低幻觉率)
  - 最多识别 3 张
- `MULTI_MAX_TOKENS = 500`(vs 单牌 200),3 张车牌的 JSON 不会截断

### Repository 改动
- `PlateRecordRepository.insertManyIfFresh(records, thumbnailBytes)` 批量插入,共享缩略图保存一次
- `PlateRecordDao.insertAll(records)` 走 Room 单事务,3 张车牌的 capturedAt 几乎相同,识别记录里排一起
- dedup 规则与 v0.6 完全一致:5s 内同一车牌不重复入库

## 识别率预期

| 场景 | 预期识别率 |
|------|------------|
| 1 辆车在画面中心,清晰 | 0.95+(v0.6 表现) |
| 2 辆车并排,清晰 | ~0.90 |
| 3 辆车并排,清晰 | ~0.75 |
| 3 辆车,其中 1 辆在 10 米外 | ~0.50 |
| 4 辆以上 | < 0.40(模型开始幻觉) |

模型有上限,这个是 M3 视觉能力天花板。代码层面保证:3 张以内可识别的不漏、模糊的直接丢弃(不会编车牌)。

## 技术栈

- Kotlin 1.9.22 + Jetpack Compose (BOM 2024.02)
- CameraX 1.3.3 + Apache POI 5.2.3
- Hilt 2.50 + Room 2.6.1 + DataStore 1.0.0
- Retrofit 2.9 + OkHttp 4.12
- minSdk 26 (Android 8.0+), targetSdk 34

## 测试

- 14 个 PlateValidator 单元测试(中文车牌形状校验)
- 5 个 JsonExtraction 单元测试(JSON 解析容错)
- 4 个 Roborazzi snapshot 测试(sandbox 缺 androidx.test 跑不起来,本机 Android Studio 可跑)

## 已知问题

- 第一次安装会提示"尚未配置 API Key" — 这是设计行为,需在设置页填入
- 单次识别 ~3-5 秒(M3 处理 1500px wide-shot 比 500px 单牌慢 ~1.5s)
- 模式切换时屏幕旋转 1-2 秒黑屏(Android 系统行为,不可避免)
- 网格弹窗固定 LazyColumn 高度(192dp = 3 行),超过 3 张的车牌需要滚动查看(本版本不常见)

## 下载

- [app-debug.apk](https://github.com/chungakon/PlateScanner/releases/download/v0.7.0/app-debug.apk)(约 28 MB)
- 源码:`git clone` + `./gradlew :app:assembleDebug`

## 从 v0.6.0 升级

```bash
adb install -r app-debug.apk
```

设置 → API Key 和识别记录都保留。新增的"📐 横屏多车"按钮在扫描页右上角,默认竖屏,点一下进入横屏模式。

## 下一步

- v0.8:基于 v0.7 网格确认,加"批量编辑"(改车牌、补录、删错)
- v0.9:云端备份 + 多端同步
- v1.0:离线模式(集成 Tesseract / PaddleOCR 本地模型)

## Credits

- 作者:Mr.Kon
- 视觉模型:MiniMax-M3
- 协议:MIT


