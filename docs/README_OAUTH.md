# BondMail Gmail / Microsoft OAuth 配置

BondMail 可以在 `app/src/main/assets/oauth/` 中随 APK 提供 Gmail 与 Microsoft 的 public-client JSON。内置配置适合项目维护者发布可直接登录的安装包；没有内置配置的自行构建版本仍可在“添加邮箱”页面底部导入自己的配置。

手动导入的配置会保存到应用私有目录 `files/oauth_clients/`，优先级高于 APK 内置配置，且不会写入 Room、DataStore 或日志。Access Token 仍只用于当前 IMAP/SMTP 操作，不作为邮箱密码保存。

## Gmail

1. 在 Google Cloud 项目中启用 Gmail API。
2. 配置 OAuth 同意屏幕；测试状态下自行加入测试用户。
3. 为 `com.bond.mail` 和实际 APK 签名 SHA-1 创建 Android OAuth 客户端。
4. 下载 Android/installed 客户端 JSON。
5. 发布者可把 JSON 保存为 `app/src/main/assets/oauth/gmail.json`；或者在 BondMail 的 Gmail 添加页面底部粘贴或选择 JSON。此模式不需要域名。

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
5. 发布者可把 JSON 保存为 `app/src/main/assets/oauth/outlook.json`；或者在 Outlook 添加页面底部粘贴或选择 JSON。

MSAL 从“手动导入配置 → APK 内置配置”的优先级链创建客户端。Manifest 仅限定 `msauth://com.bond.mail/`，不再写死某个维护者的签名路径。

请求范围：

- `https://outlook.office.com/IMAP.AccessAsUser.All`
- `https://outlook.office.com/SMTP.Send`

## 安全约束

- APK 可以内置移动端 public-client 所需的 Client ID、包名、签名哈希和重定向 URI；这些标识本身不是客户端密钥。
- 不在仓库或 APK 中写入 Client Secret、私钥、Access Token、Refresh Token、授权码或邮箱密码。
- 不记录 Access Token、Refresh Token、授权码或完整邮箱正文。
- 不把 OAuth Token 存为普通邮箱密码。
- 新增或重新授权账户时，必须先用短期令牌通过 IMAP 与 SMTP 验证，再更新本地账户。
