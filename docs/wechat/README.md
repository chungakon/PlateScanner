# 公众号文章 — 5 步发布指南

> 5 分钟内把这篇《从需求到上线 —— 一款 Android 车牌识别 App 的诞生记》发到你的公众号。

## 1 分钟准备

打开这些素材,放在手边:

```
docs/wechat/
├── article.html         ← 主文章(286 行 HTML,16KB)
├── article.md           ← Markdown 备用版(某些编辑器支持)
├── home.png             ← 文章配图 1(主页)
├── settings.png         ← 文章配图 2(设置页)
├── records.png          ← 文章配图 3(记录页)
├── about.png            ← 文章配图 4(关于页)
└── wechat_article_bundle.zip  ← 上面所有文件打包,方便传输
```

## 5 步发布(总共 5-8 分钟)

### Step 1:登录公众号后台
打开 https://mp.weixin.qq.com → 扫码登录 → 左侧菜单「内容与互动」→「图文素材」→ 「新建图文」

### Step 2:选择编辑器模式
点击编辑器顶部工具栏的「HTML」或「</>」图标,切换到 HTML 源码模式。

### Step 3:粘贴文章
打开 `article.html` → 全选(`Cmd+A`)→ 复制 → 粘到编辑器空白处。

### Step 4:插入 4 张图片
返回可视化模式,你会看到正文里有 **4 处 `<!-- IMAGE: xxx.png -->` 注释标记**。
- 点那行 → 删除注释 → 工具栏「图片」→ 上传对应的 png → 居中、宽度 100%

插入位置(按文章顺序):
1. `home.png` — 在「一、需求」小节末尾
2. `settings.png` — 在「② Prompt 设计」代码块后
3. `records.png` — 在「五、完整工作流」末尾
4. `about.png` — 在「五、完整工作流」末尾(在 records 之后)

### Step 5:标题、作者、封面 + 群发

| 字段 | 填什么 |
|---|---|
| **标题** | `从需求到上线 —— 一款 Android 车牌识别 App 的诞生记` |
| **作者** | `Mr.Kon` |
| **封面** | 任选一张图(建议 `home.png` 主页图,辨识度高) |
| **摘要** | 留空或填:`车牌管家 v0.5.0 正式开源,17 个文件 1200 行 Kotlin,从需求到上线的完整思路。` |
| **原创** | ✅ 勾选「原创」+ 「赞赏」「留言」可同时勾 |
| **赞赏作者** | 「Mr.Kon」|

→ 点「保存」→ 点「群发」(注意:**群发每天只有 1 次机会,先「预览到自己微信」检查**再发)

## 备用方案:第三方编辑器(样式更好看)

公众号后台样式很基础,如果你想排版更专业:

1. 打开 https://135editor.com(免费)或 https://xiumi.us(秀米)
2. 注册登录 → 「导入文章」→ 选「HTML 导入」→ 粘贴 `article.html` 内容
3. 第三方会自动渲染样式(标题、引用、代码块、表格)
4. 在 4 个 IMAGE 注释位置插入对应图片
5. 点「复制」→ 切到公众号后台 → 粘贴(自动同步第三方样式)

> 提示:第三方编辑器样式更花哨,但 HTML 体积大可能影响加载,先预览再发。

## 备用方案:Markdown 版(适合极简)

如果你的公众号后台支持 Markdown 粘贴:

1. 直接打开 `article.md`
2. 全选复制 → 粘到公众号后台的「Markdown 编辑器」
3. 手动插入 4 张图

## 发布后

- 把 GitHub release URL 放在**阅读原文**链接里:
  `https://github.com/chungakon/PlateScanner`
- 在公众号菜单栏「Plate Scanner」→「源码下载」指向:
  `https://github.com/chungakon/PlateScanner/releases/tag/v0.5.0`
- 鼓励读者点「在看」+「转发」,这是公众号文章传播的关键

## 版权声明(已写在文末)

> © 2026 Mr.Kon · 保留所有权利
> 项目地址:GitHub 搜索 `PlateScanner`
> 邮箱反馈:mr.kon@platescanner.app

## 需要修订?

如果发布后要改某个地方(比如加/删一节、改配图说明),告诉我具体哪里,我重生成 HTML + 配图 + 这份 README 一起更新。
