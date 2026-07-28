# BondMail v0.2.25.0

## 邮件正文恢复

- 修复 `layout-v18` 新增的 CSS 规则解析正则在部分 Android ICU 实现上初始化失败的问题。
- 旧表达式把最后一个 `}` 当作未转义的量词结束符，导致 `MailWebViewCache` 首次使用时抛出 `PatternSyntaxException`；类初始化一旦失败，同一进程内后续任何邮件都会一直停在骨架加载页。
- 正则改为显式匹配 `\{ ... \}`，HTML 准备缓存升级为 `layout-v19`，旧准备结果自动失效。
- 正文下载、MIME 解析和 Room 缓存逻辑保持不变；日志已证明正文能够成功下载，失败发生在本地 HTML 准备阶段。

## 详情加载保护与顶部连续性

- 邮件内容顶部不再额外叠加 12dp 空白，骨架和最终 HTML 从真实详情顶栏底部直接开始，减少顶栏与发件人区域之间的断层感。
- 发件人区域与正文间距由 10dp 收紧为 8dp。
- HTML 准备或 WebView 主文档加载失败时，不再永久显示加载骨架；页面显示“邮件内容加载失败”和“重试”。
- 重试会生成新的正文渲染 key，重新执行 HTML 准备和 WebView 提交，不修改已下载正文或邮件状态。

## 账户编辑文案

- “更新客户端授权码 / App 专用密码”缩短为“更改授权码/密码”。
- 原有安全逻辑不变：新凭证必须同时通过 IMAP 与 SMTP 验证后才替换旧值。

## Performance 构建

- 为 Nimbus JOSE 的可选 Tink/Bouncy Castle 算法引用增加 R8 `dontwarn` 规则。
- BondMail 的 Android OAuth public-client 流程不使用这些可选私钥/PEM/Ed25519/X25519 实现；无需把完整 Tink 或 Bouncy Castle 打进 APK。
- 修复 `minifyPerformanceWithR8` 因缺少可选类而中止的问题，同时继续保留 R8 和资源压缩。

## 版本

```text
versionCode = 41
versionName = 0.2.25.0
Room 数据库版本 = 6
HTML 文档缓存 = layout-v19
```
