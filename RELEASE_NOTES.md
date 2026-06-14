# v0.5.0 (2026-06-15) — First public release

## 核心功能

- ✅ 实时车牌识别:CameraX 后置摄像头持续抓帧,1.7 秒/次
- ✅ 智能提示:MiniMax 视觉模型识别 + bbox 坐标叠加
- ✅ 自动确认:识别后 30 秒内自动入库
- ✅ 去重防抖:5 秒内同一车牌不重复提示
- ✅ 一键导出:识别结果导出为 Excel 报表(带缩略图)
- ✅ 多服务商:支持 MiniMax、OpenAI、通义千问 3 家识别服务
- ✅ 全中文 UI:Material 3 主题 + 停车场渐变背景
- ✅ 关于页:创作者 Mr.Kon、版本号、主要功能

## 技术栈

- Kotlin 1.9.22 + Jetpack Compose (BOM 2024.02)
- CameraX 1.3.3 + Apache POI 5.2.3
- Hilt 2.50 + Room 2.6.1 + DataStore 1.0.0
- Retrofit 2.9 + OkHttp 4.12
- minSdk 26 (Android 8.0+), targetSdk 34

## 测试

- 13 个 PlateValidator 单元测试(中文车牌形状校验)
- 5 个 JsonExtraction 单元测试(JSON 解析容错)
- 4 个 Roborazzi snapshot 测试(JVM 渲染 @Preview 到 PNG)

## 已知问题

- 第一次安装会提示"尚未配置 API Key" — 这是设计行为,需在设置页填入
- 429 rate limit:Coding Plan 套餐对每分钟 token 有限制,1.7s/次扫描时大约 1 分钟后会被限速。手动巡场场景不影响
- 港澳车牌识别率低(训练数据少),如有需要可优化 prompt

## 下载

- [app-debug.apk](https://github.com/&lt;your-username&gt;/PlateScanner/releases/download/v0.5.0/app-debug.apk)(约 28 MB)
- 源码:`git clone` + `./gradlew :app:assembleDebug`

## 下一步

- v0.6:多车同时识别(模型返回 plates 数组时支持多结果)
- v0.7:云端备份 + 多端同步
- v0.8:离线模式(集成 Tesseract / PaddleOCR 本地模型)

## Credits

- 作者:Mr.Kon
- 视觉模型:MiniMax-Text-01
- 协议:MIT
