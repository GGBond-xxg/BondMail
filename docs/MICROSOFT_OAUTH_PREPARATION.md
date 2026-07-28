# BondMail Microsoft OAuth 配置与回归清单

BondMail 当前 Android 包名：

```text
com.bond.mail
```

目标范围：

- Outlook.com、Hotmail、Live 等个人 Microsoft 账户；
- Microsoft 365 工作或学校账户；
- OAuth 访问 IMAP；
- OAuth 发送 SMTP 邮件。

> v0.2.24.0 已接入 MSAL 与 IMAP/SMTP XOAUTH2。本文件保留控制台配置、签名与真机回归要求。

## 1. 先取得 Microsoft Entra 目录

应用必须注册在一个 Microsoft Entra 租户/目录中。个人 Microsoft 账号如果在“应用注册”页面看到“目录外部创建应用程序的功能已被弃用”，说明当前账号还没有可用目录。

可先完成 Azure 免费账户注册，随后在右上角“目录 + 订阅”中切换到 Azure 创建的默认目录，再进入：

```text
Microsoft Entra ID
→ 应用注册
→ 新注册
```

不要为了 Android 客户端创建虚拟机、数据库或公网 IP 等无关付费资源。

## 2. 注册应用

名称建议：

```text
BondMail Android
```

支持的账户类型选择：

```text
任何组织目录中的帐户和个人 Microsoft 帐户
```

英文界面通常显示：

```text
Accounts in any organizational directory and personal Microsoft accounts
```

首次注册页面的 Redirect URI 可以先留空。注册完成后保存：

```text
Application (client) ID
Directory (tenant) ID
```

Client ID 和 Tenant ID 不是密码，可以提供给开发侧。

## 3. 生成 Android 签名哈希

Entra Android 平台需要 Base64 编码的 SHA-1 证书哈希，不是带冒号的普通 SHA-1 文本。

### Debug 签名（Windows PowerShell，无需 OpenSSL）

```powershell
$certFile = "$env:TEMP\bondmail-debug.cer"

keytool -exportcert `
  -alias androiddebugkey `
  -keystore "$env:USERPROFILE\.android\debug.keystore" `
  -storepass android `
  -keypass android `
  -file $certFile

$sha1Hex = (Get-FileHash $certFile -Algorithm SHA1).Hash
$sha1Bytes = for ($i = 0; $i -lt $sha1Hex.Length; $i += 2) {
  [Convert]::ToByte($sha1Hex.Substring($i, 2), 16)
}
[Convert]::ToBase64String([byte[]]$sha1Bytes)

Remove-Item $certFile
```

如果不存在 `debug.keystore`，先在项目根目录执行一次：

```powershell
.\gradlew.bat assembleDebug
```

### Release 签名

使用正式发布 keystore 和别名重复上面的证书导出/哈希步骤。Release keystore、密码和私钥只由项目方本地保存，不要上传聊天或代码仓库。

## 4. 添加 Android 平台

进入刚创建的 Entra 应用：

```text
身份验证
→ 添加平台
→ Android
```

填写：

```text
Package name: com.bond.mail
Signature hash: 上一步得到的 Base64 字符串
```

配置完成后门户会生成类似：

```text
msauth://com.bond.mail/<签名哈希>
```

请分别为 Debug 和 Release 签名添加 Android 平台，并保存：

```text
Debug Redirect URI
Release Redirect URI
MSAL Configuration JSON
```

## 5. 启用 Public Client Flow

进入：

```text
身份验证
→ 高级设置
→ 允许公共客户端流
```

设置为：

```text
是
```

Android 是 public client。不要创建或把 Client Secret 写入 APK，因为移动端无法安全保管 Secret。

## 6. 邮件 Scope 与 Exchange Online 权限说明

BondMail 登录个人 Outlook、Hotmail、Live 邮箱时，会在 MSAL 交互登录中直接请求下面两个用户委托 Scope：

```text
https://outlook.office.com/IMAP.AccessAsUser.All
https://outlook.office.com/SMTP.Send
```

对于个人 Microsoft 账户，基本登录流程可以由用户在 Microsoft 登录/同意页面动态授权，**不要求先在 Entra 门户手动添加 Office 365 Exchange Online API 权限**。因此，门户里暂时找不到 Exchange Online API，不会阻止本版先测试 Outlook、Hotmail、Live 的用户登录与 XOAUTH2。

组织版 Microsoft 365 租户可能通过管理员策略禁止用户自行同意。遇到这种租户时，管理员可再进入：

```text
API 权限
→ 添加权限
→ 我的组织使用的 API
→ Office 365 Exchange Online
→ 委托的权限
```

预先添加：

```text
IMAP.AccessAsUser.All
SMTP.Send
```

这里始终使用**委托权限**。不要选择 `IMAP.AccessAsApp`、`SMTP.SendAsApp`，也不要创建 Client Secret；应用权限和客户端凭据流用于无人值守服务器，不适合 Android 邮件客户端。

MSAL 还会由 SDK 处理登录身份与长期会话所需的标准授权信息。BondMail 不把 Access Token、Refresh Token 或邮箱密码写入自己的数据库。

## 7. 最终提供给项目的资料

```text
应用名称：BondMail Android
包名：com.bond.mail
Application (client) ID：...
Directory (tenant) ID：...
支持账户类型：组织目录 + 个人 Microsoft 账户
Debug Redirect URI：msauth://com.bond.mail/...
Release Redirect URI：msauth://com.bond.mail/...
Delegated permissions：
- IMAP.AccessAsUser.All
- SMTP.Send
MSAL Configuration JSON：门户生成的完整 JSON
```

不要提供：

```text
Client Secret
邮箱密码
应用专用密码
Access Token
Refresh Token
Debug/Release keystore
keystore 密码
```

## 8. 当前代码状态与真机回归

已实现：

1. MSAL Android 多账户 public-client；
2. `common` 受众，支持个人 Microsoft 账户与组织账户；
3. Outlook/Hotmail/Live 的 IMAP/SMTP XOAUTH2；
4. 按 MSAL 账户 authority 静默获取短期 Token；
5. 静默授权失效后的原地重新授权；
6. Token 不写入 Room、普通凭证存储或日志。

仍需真机验证：

- Outlook.com、Hotmail、Live、Outlook.jp 各至少一个账户；
- 收信、发信、Token 过期后的静默续期；
- 用户撤销授权后的重新授权；
- Debug/performance 签名以及未来 Release 签名的 Redirect URI；
- Microsoft Authenticator broker 已安装和未安装两种环境。
