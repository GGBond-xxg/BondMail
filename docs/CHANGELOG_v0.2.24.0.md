# BondMail v0.2.24.0

## Gmail 与 Microsoft 安全登录

- Gmail 账户入口改为 Google Identity Services `AuthorizationClient`：请求 `https://mail.google.com/`、`openid`、`email` 与 `profile`，通过系统 Google 账户授权页面取得短期 Access Token。
- Outlook、Hotmail、Live 与 Microsoft 个人/组织账户入口接入 MSAL Android，多账户模式使用 `common` 受众；交互登录后由 MSAL 管理账户与令牌缓存。
- Microsoft 邮件访问请求以下委托 Scope：
  - `https://outlook.office.com/IMAP.AccessAsUser.All`
  - `https://outlook.office.com/SMTP.Send`
- IMAP 与 SMTP 均新增 XOAUTH2 会话配置，明确禁用 OAuth 邮箱的 LOGIN/PLAIN 回退；SMTP 发件改为显式建立 `Transport` 并把当前短期令牌交给 XOAUTH2。
- OAuth Access Token 不写入 Room、DataStore、普通凭证存储或应用日志；后续同步时由 Google Identity Services 或 MSAL 重新取得短期令牌。
- 新增 `BondMail-OAuth` Debug 标签，只记录服务商、阶段和脱敏邮箱提示，便于定位登录回调与静默续期，不输出 Token、授权码或服务商账户 ID。
- AndroidManifest 已注册 MSAL `BrowserTabActivity` 回调；项目包含门户生成的 Microsoft public-client 配置和 Google Android OAuth 客户端配置。APK 中没有 Client Secret。
- Outlook 服务商后缀补充 `outlook.jp`、`outlook.co.jp`、`hotmail.co.jp` 与 `live.jp`，个人 Microsoft 邮箱仍统一通过同一个 Outlook 入口登录。

## OAuth 账户重新授权

- 邮箱抽屉的编辑窗口会根据账户认证类型显示不同操作：
  - 授权码/App 专用密码账户可更换凭证；
  - Gmail/Microsoft 账户可直接“重新授权邮箱”。
- 重新授权会验证登录结果仍对应当前本地邮箱；Microsoft 同时使用稳定的 MSAL 账户 ID 与邮箱地址判断，兼容服务商返回大小写或主别名变化。
- 新令牌必须先通过 IMAP 收件和 SMTP 发件双重验证，验证成功后才更新本地 OAuth 账户关联。
- 重新授权不会删除账户、历史邮件、正文缓存、联系人、星标、已读状态、发件箱或设置。
- OAuth 失效后的同步错误会提示从邮箱编辑窗口重新授权，不再要求删除并重新添加账户。

## 163/126 等授权码更新

- 邮箱抽屉里的铅笔入口不再只能修改显示名称；现在可重新填写“客户端授权码 / App 专用密码”。
- 新凭证保存前会清除旧 IMAP 连接池，再分别验证 IMAP 与 SMTP，避免旧连接让错误的新授权码被误判为可用。
- 只有两项验证均成功才替换 Android Keystore 中的旧凭证；失败时继续保留原凭证与全部本地邮件。
- 更新成功后立即对该邮箱执行一次安静刷新，不必等待下一次后台同步。

## 数据库无损迁移

- Room 数据库从 v5 升级到 v6。
- `accounts` 新增可空的 `oauthAccountId`，用于关联 Google/Microsoft 服务商账户；5→6 迁移不重建表、不删除邮件或账户数据。
- 旧版曾通过通用密码表单添加的 Gmail、Outlook 与 Microsoft 365 账户会保留全部本地邮件，但认证类型迁移为 OAuth。这样旧密码不会被误传给 XOAUTH2；用户只需在邮箱编辑窗口原地重新授权，不必删除账户。
- 旧版遗留的加密 App Password 只在 OAuth 双协议验证成功后删除；授权取消或失败不会破坏旧账户、本地缓存或服务商账户关联。
- 旧的 QQ、163、126、iCloud、Yahoo 与其他授权码账户保持原认证方式，不会被强制转换为 OAuth。

## HTML 邮件排版 v18

- 文档缓存升级为 `layout-v18`，确保旧版错误分类和缩放结果不会继续复用。
- 网易等手机邮件即使带有 600px Outlook 兼容外框，也会根据内部 300–479px 主内容卡片、正文覆盖率和响应式标记保持 `FLUID` 手机布局，不再整封缩成居中的小卡片。
- Cloudflare 等桌面 Newsletter 的画布检测范围扩展到 480–1200px；除 HTML `width` 和内联样式外，也会检查实际命中正文节点的 `<style>` 规则，修复宽度只写在 `.wrapper/.container/.content` CSS 中时未进入桌面缩放的问题。
- CSS 规则只有在选择器对应当前邮件中的有效正文节点时才参与评分，避免未使用的兼容样式或小图标规则误把手机邮件分类为桌面模板。
- 整体缩放下限由 40% 放宽到 20%，超宽模板仍可完整落入手机可用宽度。
- `DESKTOP_SCALED` 模式关闭 WebView 的 `useWideViewPort/loadWithOverviewMode` 二次总览缩放，并使用 `NORMAL` 排版算法；`FLUID` 模式继续启用手机宽度总览与 `TEXT_AUTOSIZING`。
- 这样可以避免 CSS 已经整体缩放后又被 WebView 再次缩放/裁切，重点修复宽 Newsletter 右侧内容缺失。
- GitHub 短邮件在 v0.2.23.0 中加入的完整触摸序列、拖动余量和滚动诊断继续保留。

## 配置与安全

- Microsoft 配置为 Android public client，多账户受众为 `AzureADandPersonalMicrosoftAccount/common`。
- Google 配置对应包名 `com.bond.mail` 的 Android OAuth 客户端；实际可登录签名必须与 Google Cloud 和 Microsoft Entra 中登记的 SHA-1/Base64 签名哈希一致。
- Debug 与 performance 构建均使用本地 Debug 签名，因此可共用当前测试配置；正式 Release 签名需要在两个服务商控制台分别增加对应配置。
- Gmail 使用受限邮件 Scope，公开发布前需要按 Google 要求完成测试用户/同意屏幕与必要的应用验证。

## 版本与数据

```text
versionCode = 40
versionName = 0.2.24.0
Room 数据库版本 = 6
HTML 文档缓存 = layout-v18
```

本版覆盖安装保留 v0.2.23.0 的账户、邮件、正文缓存、联系人、发件箱、附件任务和设置。旧 Gmail/Microsoft 账户需要一次原地 OAuth 重新授权。服务商同意页面、真实 IMAP/SMTP XOAUTH2 与不同签名构建仍需在联网 Android 真机上完成回归。
