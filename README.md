# BondMail

一款使用 Kotlin 与 Jetpack Compose 开发的 Android 邮件客户端，支持多账户、
IMAP/SMTP、OAuth、后台同步，以及 Material 3 / MIUIX 双界面样式。

## 下载

- 最新版本：[BondMail v1.3.0.9](https://github.com/GGBond-xxg/BondMail/releases/tag/v1.3.0.9)
- 安装包：[BondMail-v1.3.0.9.apk](https://github.com/GGBond-xxg/BondMail/releases/download/v1.3.0.9/BondMail-v1.3.0.9.apk)
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
- 后台任务由 WorkManager 调度；短周期使用可持续的一次性任务链。

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

## 许可证

BondMail 使用 [MIT License](LICENSE)。第三方组件与许可证信息见
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) 和 [licenses/](licenses/)。
