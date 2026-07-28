# BondMail v0.2.33.0

- `versionCode`: `52`
- `versionName`: `0.2.33.0`
- Room database: `8`
- MIME parser: `8`
- HTML prepared-document cache: `layout-v25`
- Upgrade baseline: `v0.2.32.0`

## 首次打开和缓存显示

- 首次打开邮件时，正文完成下载、HTML 准备和 Chromium 可见帧提交后，继续保持页面背景 300ms。
- 300ms 稳定期结束后，再使用 300ms 单次渐显显示最终邮件内容。
- 网络等待和 WebView 准备阶段只显示静态背景/骨架，不再播放快速移动的加载条。
- 同一份已展示内容再次打开时，优先复用内存中的 Prepared HTML 和最后一个已完整渲染的 WebView 页面；不再重复等待 300ms，也不重播渐显。
- WebView 的 generation 检查覆盖延迟显示任务，快速返回或切换邮件不会让旧页面延迟覆盖新页面。

## 带附件邮件 MIME 修复

真机日志确认 Outlook 与 163 都已完整取得数百 KiB 的 RFC822 数据，但 JavaMail 将根节点识别为 `multipart/mixed` 后没有暴露任何可显示子正文。

本版新增两层兼容：

1. JavaMail MIME 解析使用更宽容的参数、Base64 和 multipart 边界规则，并尝试读取 `MimeBodyPart.rawInputStream`。
2. JavaMail 仍返回空 multipart 时，使用受限的原始 RFC822 解析器：
   - 解析折行 Header 和 boundary；
   - 支持 multipart 递归；
   - 支持 Base64、Quoted-Printable、7bit/8bit；
   - 支持 UTF-8、GB18030、Big5、Windows-1252 回退；
   - 只保留正文和附件元数据，不在数据库中保存附件二进制。

附件元数据现在包含文件名、MIME 类型和大小，随正文缓存在 Room。邮件详情会在发件人区域显示回形针，并在正文后显示附件卡片。旧版只有 `hasAttachments=true` 而没有文件名的邮件，会显示通用“附件”项；重新解析后自动补全真实文件名和大小。

## 已发送重复行修复

- `正在发送` 本地占位行不再与服务器 `已发送` 行同时长期存在。
- 服务器 Sent UID 确认后，在同一个 Room 事务中：
  1. 创建/合并正式远端行；
  2. 继承本地正文和附件元数据；
  3. 删除 `outbox:*` 占位行。
- Gmail、Outlook、Microsoft 365、163/126 在 SMTP 接收后可能稍后生成服务器 Sent 副本。本版按标准化 Message-ID 进行多次短轮询，再决定是否执行 IMAP APPEND，避免生成真正的远端重复邮件。
- Message-ID 比较会忽略尖括号、空白和大小写差异。
- APPEND 后无法立即确认 UID 时，不再错误地把 Sent 文件夹最后一封邮件当作当前邮件；先刷新 Sent，再按标准化 Message-ID 把服务器行与本地占位行合并，并同时结束发送任务。

## 删除邮件修复

- 正式 Sent 行现在保存真实正 UID 和真实远端文件夹，因此删除会提交到服务器，而不是只删除本地占位行。
- UID 发生变化时，会使用 Message-ID 在当前文件夹内再次定位；服务器端已不存在则按删除成功处理，不会恢复过期本地行。
- 尚未取得远端 UID 的 Sent 占位行删除时，会按稳定 Message-ID 清理服务器 Sent 副本，防止下一次刷新重新出现。
- Debug 日志新增远端删除成功/已不存在、按 Message-ID 删除、Sent 占位替换、Sent 副本复用和附件解析数量。

## 数据库迁移

Room `7 → 8`：

```sql
ALTER TABLE messages ADD COLUMN attachmentsJson TEXT NOT NULL DEFAULT '[]';
```

存在附件或正文尚未加载的旧邮件，其 MIME parser version 会重置为 `0`，首次再次打开时用 parser v8 重新获取。账户、凭证、邮件正文、已读/星标状态和草稿数据不会清除。

## 本机验证重点

1. Outlook 向 163 发送一封“主题 + 正文 + 图片附件”的邮件。
2. 已发送页应由一条“正在发送”平滑转换成一条正式邮件，不应出现两条同内容记录。
3. 打开 Outlook 已发送和 163 收件箱中的同一封邮件，都应看到正文、回形针和附件文件名。
4. 删除已发送邮件，刷新及重新进入后不应恢复。
5. 第一次打开未缓存邮件：内容就绪后稳定 300ms，再渐显 300ms。
6. 返回后再次打开同一封邮件：直接显示缓存内容，不重复首次动画。
