# BondMail Gmail / Microsoft OAuth 配置

BondMail 不再在 APK 或源码仓库内附带维护者的 Google、Microsoft Client ID、签名哈希或 OAuth JSON。每个构建者在“添加邮箱”页面底部导入自己的配置。

配置文件会保存到应用私有目录 `files/oauth_clients/`，不会写入 Room、DataStore、日志或源码目录。Access Token 仍只用于当前 IMAP/SMTP 操作，不作为邮箱密码保存。

## Gmail

1. 在 Google Cloud 项目中启用 Gmail API。
2. 配置 OAuth 同意屏幕；测试状态下自行加入测试用户。
3. 为 `com.bond.mail` 和实际 APK 签名 SHA-1 创建 Android OAuth 客户端。
4. 下载 Android/installed 客户端 JSON。
5. 打开 BondMail 的 Gmail 添加页面，在底部“自定义 API 配置”中粘贴或选择该 JSON。此模式不需要域名。

Android 客户端由 Google Play services 根据包名和签名 SHA-1 自动匹配。BondMail 也兼容同项目的 Web 客户端 JSON；仅在 Web 模式下调用 `requestOfflineAccess(clientId)`。

请求范围：

- `https://mail.google.com/`
- `openid`
- `email`
- `profile`

## Outlook / Hotmail / Live

1. 在 Microsoft Entra 创建支持个人 Microsoft 账户的应用。
2. 添加 Android 平台，包名使用 `com.bond.mail`，签名哈希使用实际构建证书。
3. 下载或编写 MSAL JSON，保持 `account_mode` 为 `MULTIPLE`。
4. `redirect_uri` 必须以 `msauth://com.bond.mail/` 开头，并包含实际签名哈希。
5. 在 Outlook 添加页面底部粘贴或选择 JSON。

MSAL 直接从应用私有文件创建客户端。Manifest 仅限定 `msauth://com.bond.mail/`，不再写死某个维护者的签名路径。

请求范围：

- `https://outlook.office.com/IMAP.AccessAsUser.All`
- `https://outlook.office.com/SMTP.Send`

## 安全约束

- 不在仓库或 APK 中内置 Client ID、Client Secret、签名哈希或可用 OAuth JSON。
- 不记录 Access Token、Refresh Token、授权码或完整邮箱正文。
- 不把 OAuth Token 存为普通邮箱密码。
- 新增或重新授权账户时，必须先用短期令牌通过 IMAP 与 SMTP 验证，再更新本地账户。
