# BondMail v0.2.33.1

- `versionCode`: `53`
- `versionName`: `0.2.33.1`
- Room: `8`
- HTML cache: `layout-v25`

## 修复

- 将 `SmtpClient.describeAttachments()` 改为 `internal`。
- 解决公开函数暴露 `internal MailAttachmentInfo` 导致的 Debug 与 Performance Kotlin 编译失败。
- 不改变 SMTP、IMAP、MIME 附件解析、Sent 去重、删除、缓存与详情动画逻辑。
