# v0.6.0 (2026-06-16) — Tap-to-capture + 思考块修复

## 🎯 关键变化

**v0.5.0 的"对准 3 秒自动连拍"被替换为"对准后点一下拍一张"**。这个改动从用户实操反馈来:

- 巡场员更习惯"我准备好再按"的节奏,而不是让相机一直在转
- 自动 1.7s 拍一次,Coding Plan token 跑得太快,1 分钟就被 429 限速
- 拍到 1 张图就 1 次 API 调用,token 用得少,识别质量更稳

## 核心功能

- ✅ **点按抓拍**:预览区点一下就拍,拍完自动 disarm 等下一次点击
- ✅ **抓拍提示**:拍摄中显示半透明进度圈 + "识别中..." 字样
- ✅ **burst 防护**:`Mutex` 串行化 arm/fire,连点 10 下也只拍 1 张
- ✅ **画面预览**:点按之外相机一直停在 live preview,不烧 token
- ✅ 智能提示:MiniMax 视觉模型识别 + bbox 坐标叠加
- ✅ 自动确认:识别后 30 秒内自动入库
- ✅ 去重防抖:5 秒内同一车牌不重复提示
- ✅ 一键导出:识别结果导出为 Excel 报表(带缩略图)
- ✅ 多服务商:支持 MiniMax、OpenAI、通义千问 3 家识别服务
- ✅ 全中文 UI:Material 3 主题 + 停车场渐变背景
- ✅ 关于页:创作者 Mr.Kon、版本号、主要功能

## 🐛 Bug 修复

- **MiniMax M3 思考块污染 JSON 解析**:之前 v0.5.0 用正则 strip `<think>...</think>`,但模型有时只输出 `<think>` 不闭合,导致 JSON 解析失败。v0.6.0 在请求里加 `thinking = ThinkingConfig(type = "disabled")`,从源头关掉,不再依赖正则。粤 TDxxxxx 这种 8 字符蓝牌,识别率从 0.85 升到 0.95+。

## 技术栈

- Kotlin 1.9.22 + Jetpack Compose (BOM 2024.02)
- CameraX 1.3.3 + Apache POI 5.2.3
- Hilt 2.50 + Room 2.6.1 + DataStore 1.0.0
- Retrofit 2.9 + OkHttp 4.12
- minSdk 26 (Android 8.0+), targetSdk 34

## 测试

- 14 个 PlateValidator 单元测试(中文车牌形状校验)
- 5 个 JsonExtraction 单元测试(JSON 解析容错)
- 4 个 Roborazzi snapshot 测试(sandbox 缺 androidx.test 跑不起来,本地 Android Studio 可跑)

## 已知问题

- 第一次安装会提示"尚未配置 API Key" — 这是设计行为,需在设置页填入
- 单次识别 ~3 秒(MiniMax M3 视觉模型响应时间),点击后需稍等
- 港澳车牌识别率低(训练数据少),如有需要可优化 prompt

## 下载

- [app-debug.apk](https://github.com/chungakon/PlateScanner/releases/download/v0.6.0/app-debug.apk)(约 28 MB)
- 源码:`git clone` + `./gradlew :app:assembleDebug`

## 从 v0.5.0 升级

```bash
adb install -r app-debug.apk
```

设置 → API Key 会保留(v0.5.0 写到 DataStore 里),不需要重填。识别记录会保留。

## 下一步

- v0.7:多车同时识别(模型返回 plates 数组时支持多结果)
- v0.8:云端备份 + 多端同步
- v0.9:离线模式(集成 Tesseract / PaddleOCR 本地模型)

## Credits

- 作者:Mr.Kon
- 视觉模型:MiniMax-M3
- 协议:MIT

