# 公众号文章 — 接入完成 🎉

> 由于你的公众号是**个人未认证订阅号**,微信 API **没有草稿箱/群发权限**。
> 但**永久素材库权限是有的** —— 我已经把 4 张图上传到你的公众号素材库,生成了**带微信 CDN URL 的完整 HTML**,你只需要**复制 + 粘贴 + 群发** 3 步。

## 你的公众号账号信息

| 字段 | 值 |
|---|---|
| AppID | `wxbbc2012981032b42` |
| 账号昵称 | `Mr.kon` |
| 主体 | 钟文康 |
| 账号类型 | **订阅号(个人,未认证)** |
| 接入 IP | `223.73.241.211`(已加白名单) |

## 3 步发布(5 分钟内)

### Step 1:复制文章 HTML

打开文件:
```
/Users/spoon/.mavis/sessions/mvs_a3d2601f34ca403392a0604e8bf5dc45/workspace/PlateScanner/docs/wechat/article_publishable.html
```

> 文件已包含全部 4 张图(用微信 CDN,粘进去直接显示)
> 总长 13.5KB,完全在公众号限制内
> 全选(`Cmd+A`)→ 复制(`Cmd+C`)

### Step 2:在公众号后台粘贴

1. 打开 https://mp.weixin.qq.com
2. 左侧「内容与互动」→「图文素材」→ 「**新建图文**」
3. 点编辑器顶部「</>」图标切换到 **HTML 模式**
4. **粘贴**(`Cmd+V`)
5. 切回可视化模式
6. **4 张图已自动显示**(因为用的微信 CDN URL,不需要再上传)

### Step 3:填标题作者群发

| 字段 | 填什么 |
|---|---|
| **标题** | `从需求到上线 —— 一款 Android 车牌识别 App 的诞生记` |
| **作者** | `Mr.kon` |
| **封面** | 「封面图」位置点「从素材库选」→ 选刚上传的 `home.png` |
| **摘要** | `车牌管家 v0.5.0 正式开源,17 个文件 1200 行 Kotlin,从需求到上线的完整思路。` |
| **原创** | ✅ 勾选 |

→ 点「预览到自己微信」检查 → 点「保存并群发」

## 已上传到素材库的 4 张图

| 文件 | 用途 | 微信 CDN URL |
|---|---|---|
| `home.png` | 文章配图 1(主页) | `https://mmbiz.qpic.cn/sz_mmbiz_png/uWGicLR45HDtoxzgDf0WsK9eJCNSKMKuILHE5NatwpRgD` |
| `settings.png` | 文章配图 2(设置) | `https://mmbiz.qpic.cn/mmbiz_png/uWGicLR45HDsMeiankj07WYKicoIu4j65wjzKutORN3esKsX` |
| `records.png` | 文章配图 3(记录) | `https://mmbiz.qpic.cn/sz_mmbiz_png/uWGicLR45HDs1ZWicYXRY4hibwAQickmWZG83X91wgV1C` |
| `about.png` | 文章配图 4(关于) | `https://mmbiz.qpic.cn/sz_mmbiz_png/uWGicLR45HDvPShPMCz0LWlyciaxc2g9KqnPRBwYwibZY` |

> 如果你以后想修改文章(比如改个错别字),直接改 `article_publishable.html` 这个文件,不要去改 `article.html`(那个版本没有 CDN URL,粘进去图会变"图片加载失败")。

## 为什么不能"全自动发布"?

你的公众号是**个人未认证订阅号**,根据微信规定:
- ✅ 可以用 API 上传永久素材
- ❌ 不能用 API 创建草稿(需要"原创声明"+"认证")
- ❌ 不能用 API 群发(订阅号 + 个人 + 未认证 = 无权限)

如果想解锁 API 群发,需要:
1. **微信认证**(企业 / 个体工商户):300 元/年,认证后订阅号 + 个人也能调群发 API
2. **升级为服务号**:服务号认证后**默认有**草稿箱/群发/客服消息 API 权限

升级后告诉我,我可以**完全自动化**(生成文章 → 调 API 写草稿 → 调 API 群发 → 通知你结果)。

## 备用方案(你也可以做)

如果不想付费认证,**3 步手工发**也是很快的(我已经帮你省了 90% 的工作):

| 原来要做的 | 我已帮你做的 |
|---|---|
| 4 张图分别上传到素材库 | ✅ 上传完成 |
| 把每张图手动拖到文章对应位置 | ✅ HTML 已嵌入 CDN URL,直接粘 |
| 排版 / 字号 / 行距 | ✅ CSS 已内嵌,完美兼容公众号 |

> **实际你只需要做**:点开 → 粘 → 填标题 → 群发。**3 步,5 分钟**。

## 后续维护

- **改了文章?** 重新生成 `article_publishable.html` 即可
- **想加新图?** 告诉我图,我上传到素材库 + 替换 HTML
- **想加新文章?** 给我 markdown/HTML,我重新走一遍这个流程

公众号 API 的 access_token 每 2 小时失效,如需我重新接入,直接说"重新接入公众号"即可。
