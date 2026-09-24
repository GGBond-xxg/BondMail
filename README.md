# BondMail

一款使用 Kotlin 与 Jetpack Compose 开发的 Android 邮件客户端，支持多账户、
IMAP/SMTP、OAuth、后台同步，以及 Material 3 / MIUIX 双界面样式。

## 下载

- 最新版本：[BondMail v1.5.5.5](https://github.com/GGBond-xxg/BondMail/releases/tag/v1.5.5.5)
- 安装包：[BondMail-v1.5.5.5.apk](https://github.com/GGBond-xxg/BondMail/releases/download/v1.5.5.5/BondMail-v1.5.5.5.apk)
- 最低系统：Android 8.0（API 26）

APK 的 SHA-256、版本代码和历史安装包见 [GitHub Releases](https://github.com/GGBond-xxg/BondMail/releases)。

## 主要功能

- 多邮箱账户收信、发信、草稿、已发送与联系人管理
- Gmail OAuth 2.0、Microsoft MSAL，以及通用 IMAP/SMTP 授权码登录
- 邮件正文、附件信息、内嵌图片与 HTML 移动端适配
- 本地 Room 缓存、增量同步、后台收信与新邮件通知
- Material 3 与 MIUIX 样式切换，支持浅色、深色和跟随系统
- 简体中文、繁体中文与英文 JSON 多语言
- 可选的自建 Cloudflare FCM 推送
- 原 HTML 内翻译标题和正文，保留图片、表格和链接；支持阿里云、有道、Google、Microsoft，自填密钥、加密缓存与一键原文切换
- 10 秒撤销发送、搜索筛选、账号签名/模板、通知分级
- 邮箱工具：同步诊断、附件索引、按回复标识聚合会话、稍后提醒及存储清理
- AI 邮件助手：摘要、重点/待办、回复草稿与当前邮件问答；提供 DeepSeek、Kimi、Xiaomi MiMo、OpenAI、Gemini 预设，多配置切换、模型列表选择与自定义接口，回复由用户审核发送

新功能入口与使用限制见 [邮箱效率功能](docs/PRODUCTIVITY.md)。

## 开始使用

1. 添加邮箱：Google / Microsoft 使用对应授权登录，其他邮箱按服务商要求使用客户端授权码或 App Password。
2. 同步邮件：正文按需下载并保存在本机；后台同步频率、可选 CF 推送在设置中调整。
3. 使用翻译：先在设置填写自己的服务密钥；邮件顶部选择语言和服务，右下方点击翻译，完成后可一键查看原文。
4. 使用 AI：在设置 → AI 服务配置地址、模型和 API Key；打开邮件 → 更多 → AI 邮件助手。详见 [AI 使用说明](docs/AI.md)。

邮件与账号凭据主要保存在本机，凭据和译文缓存使用 Android Keystore 加密。手动翻译会把标题及可翻译文字发送给选定服务商，图片和附件不参与翻译。详见 [翻译说明](docs/TRANSLATION.md)。

AI 只在主动操作时发送原始标题、正文文字、你的要求及连续问答上下文给配置的服务；不上传图片和附件。关闭助手清空本页对话，密钥加密保存在本机。服务可用性、数据保留与费用以服务商规则为准。

## 支持的邮箱

| 邮箱 | 登录方式 |
| --- | --- |
| Gmail | Google OAuth 2.0 + XOAUTH2 |
| Outlook / Hotmail / Live | Microsoft MSAL + XOAUTH2 |
| QQ 邮箱 | 客户端授权码 |
| 163 / 126 邮箱 | 客户端授权码 |
| iCloud Mail | App 专用密码 |
| Yahoo Mail | App Password |

其他提供标准 IMAP/SMTP 服务的邮箱可以通过通用配置接入。

## 构建

### 环境

- Android Studio 或 JDK 17
- Android SDK 36
- 已开启 USB 调试的 Android 设备（仅安装和真机测试需要）

### 常用命令

```powershell
# 构建 Debug APK
.\gradlew.bat assembleDebug

# 安装到已连接设备
.\gradlew.bat installDebug

# 单元测试与 Lint
.\gradlew.bat testDebugUnitTest lintDebug

# 构建经过 R8 和资源压缩的性能包
.\gradlew.bat assemblePerformance
```

Debug APK 输出位置：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## OAuth 配置

Gmail 和 Microsoft 登录需要各自平台的 OAuth 公共客户端配置。配置文件位于：

```text
app/src/main/assets/oauth/gmail.json
app/src/main/assets/oauth/outlook.json
```

控制台配置、签名指纹和回调 URI 说明见
[docs/README_OAUTH.md](docs/README_OAUTH.md)。不要把客户端密钥、服务账户文件或发布
签名文件提交到仓库。

## Cloudflare FCM 推送

CF FCM 是可选功能；不配置时，邮箱登录、手动刷新和 Android 后台定时收信仍可正常使用。
需要部署独立推送服务时，请参考
[BondMail Cloudflare Push](https://github.com/GGBond-xxg/BondMail-Cloudflare-Push)，
然后在应用设置中填写 Worker 域名和访问密钥。

## 同步与缓存

- 首次添加账户会验证 IMAP 与 SMTP，并先同步最近邮件头。
- 后续同步以 UID 增量获取新邮件，并轻量刷新已读和星标状态。
- 邮件正文按需下载并保存到 Room，已缓存内容不会重复连接服务器。
- HTML 预处理结果使用内存 LRU 缓存，`cid:` 图片会转换为本地 Data URI。
- 后台由 FCM 唤醒与 WorkManager 至少 15 分钟的周期任务共同调度；手动刷新和推送唤醒采用一次性任务。

## JSON 多语言

业务界面文案位于：

```text
app/src/main/assets/i18n/en.json
app/src/main/assets/i18n/zh.json
app/src/main/assets/i18n/zh-CHT.json
```

Compose 页面通过 `tr("key")` 读取文案。新增语言时复制英文 JSON、保持所有 key 一致，
并在 `ui/i18n/JsonI18n.kt` 的 `SupportedLanguages.options` 中注册。

## 项目结构

```text
app/src/main/java/com/bond/mail/
├─ background/       后台同步、通知与 FCM
├─ data/             OAuth、数据库、邮件协议与设置
└─ ui/               Compose 页面、组件、动效与主题

cloudflare-worker/   可选推送服务
docs/                更新日志、OAuth 与测试说明
```

## 文档

- [版本发布记录](https://github.com/GGBond-xxg/BondMail/releases)
- [OAuth 配置](docs/README_OAUTH.md)
- [测试清单](docs/TEST_CHECKLIST.md)
- [动效规范](docs/README_MOTION_SPEC.md)
- [实现状态](docs/IMPLEMENTATION_STATUS.md)

历史版本的详细变化保存在 `docs/CHANGELOG_*.md`，不再堆叠在项目首页。

## 关于这个项目

BondMail 是一个由作者提出构想、持续通过实际使用打磨的开源项目。产品方向、需求定义和使用反馈由作者主导，绝大部分代码实现、调试与文档编写在 GPT 的协助下完成。

欢迎通过 [Issues](https://github.com/GGBond-xxg/BondMail/issues) 提交问题和建议。反馈时请说明应用版本、Android 版本、复现步骤；截图和日志请去除邮箱地址、邮件内容、密码及 API 密钥等私人信息。

## 赞助支持

如果 BondMail 对你有帮助，欢迎自愿赞助，支持项目持续维护。感谢每一份支持；所有功能均不以赞助为前提。应用内入口：**设置 → 关于我们 → 更多 → 赞助支持**。

请选择对应网络并核对完整地址。ETH / ERC20 使用 Ethereum 网络，TRC20 使用 TRON 网络。

应用内可点击“二维码”供另一台设备扫描，也可查看仓库中的[七种网络二维码](app/src/main/assets/sponsorship)。二维码仅编码下列地址，不包含转账金额。

复制与二维码按钮左右并排。应用内二维码自动跟随当前主题配色。仓库 PNG 保留标准配色。

| 网络 | 收款地址 |
| --- | --- |
| Solana · SOL | `GseMb4yCgfhyMvkA7jP6QhMe4e5nMPnxgJqqrA8Aq7eJ` |
| Ethereum · ETH / ERC20 | `0xcB2f6fc5eF905e89cDeE7F2eB59faD9A93043324` |
| TON | `UQATTF8wVv_Q8x42OYyDOUcM1Ti0HA-VdZ0b5zUd78_lEYqj` |
| TRON · TRX / TRC20 | `TXDFyQKRSLt6dmbs3tcJbEJbcHsokbgKSn` |
| Sui · SUI | `0xd61db83d28fc0da34e55b9488d3927fc5b6515cc96c6109c0f8ed451980af4bb` |
| Bitcoin · BTC | `1BhMBUVLySJg3qNgFNxYPFMGbcd4KFxkrK` |
| Dogecoin · DOGE | `DEJ6MMqAjX55YfXXtFQVeYrCbadntYwZCs` |

## 许可证

BondMail 使用 [MIT License](LICENSE)。第三方组件与许可证信息见
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) 和 [licenses/](licenses/)。
