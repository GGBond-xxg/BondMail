# BondMail v0.2.31.0

版本：`0.2.31.0`
versionCode：`49`
Room：`v7`
HTML：`layout-v24`

## 打开邮件立即已读

- 点击邮件时先在 HomeViewModel 的全部内存快照中将该邮件设为已读，再开始详情导航。
- Room 尚未完成异步写入时，旧的 Flow 发射也会经过 optimistic-read 覆盖，不会在返回列表的一瞬间恢复未读样式。
- 每次打开远端邮件都会请求 IMAP `\\Seen`；即使本地此前已经显示已读，也可以补交上一次断网期间未完成的服务器状态。
- 打开操作继续推进账户 FLAGS generation，旧同步快照不能覆盖较新的已读状态。

## Telegram 风格前进、返回与预测性返回

- 详情、服务商选择和邮箱配置统一使用同一套不透明空间转场。
- 前进页面从右侧短距离进入；下层页面保持稳定，不再缩放或淡出。
- 返回页面完整跟随手势向右离开，取消手势时恢复，完成时快速收尾。
- 移除会暴露下层文字的路由透明度叠加，降低返回拖拉感和双层文字闪烁。
- 工程继续启用 `android:enableOnBackInvokedCallback=true`，并使用 Navigation Compose 的 pop transition 映射预测性返回进度。

## 邮件正文显示

- WebView 完成可见帧后以 130ms 透明度和轻微缩放显示，骨架保持在下方直到正文完全覆盖，消除直接切换造成的单帧闪烁。
- 不重新引入 Compose/Chromium 两套真实标题和发件人文字，因此不会恢复“粗 → 细”问题。
- Cloudflare、GitHub、网易、Bybit 的 `layout-v24` 排版规则保持不变。

## 未读邮件入口

- 在收件箱和星标之间新增“未读邮件”。
- 该入口是 INBOX 的本地投影，只显示 `unread=1` 的邮件；刷新时仍同步真正的远端收件箱。
- 首页横向文件夹栏和左侧抽屉共用同一个入口与数据源。

## 已发送

- “已发送”现在解析服务商的 `\\Sent` 属性和常见本地化文件夹名，读取服务器真实已发送邮件。
- 新邮件排队后立即在已发送列表显示“正在发送”。
- SMTP 接受后状态动画切换为“已发送”，不用等待下一次文件夹同步。
- SMTP 成功与 IMAP Sent 归档分离：归档失败只重试 Sent 对账，不会重复发送邮件。
- Gmail、Outlook 等自动保存 Sent 的服务商先按稳定 Message-ID 查重，避免重复副本。
- 最近 50 封 Sent 每次进入时做有界对账，可同步网页端新增、删除和服务器自动创建的已发送邮件。

## 草稿箱

- 草稿箱同时合并服务器 Drafts 与本地尚未上传的草稿，继续复用现有邮件列表卡片和 Gmail 分组圆角。
- 草稿行以红色“草稿”标识；点击后复用写邮件 Bottom Sheet 编辑。
- 写邮件存在收件人、抄送/密送、主题、正文或附件时返回，会显示保存/舍弃确认。
- 保存先写入本地 Room 并立即出现在草稿箱，然后由 WorkManager 上传到 IMAP Drafts。
- 编辑服务器草稿时，新版本 APPEND 成功后才删除旧 UID；稳定 Message-ID 可防止 Worker 中断重试产生重复草稿。
- 发送草稿后，SMTP 成功才删除服务商 Drafts 副本；失败时原草稿仍可恢复。
- 草稿在网页邮箱中新增或删除后，进入草稿箱会对最近 50 封做有界同步。

## 数据库

Room `v6 → v7` 无损迁移新增：

- `messages.deliveryState`：`REMOTE / QUEUED / SENDING / SENT / FAILED / DRAFT`
- `outbox.internetMessageId`
- `outbox.remoteFolder`
- `outbox.remoteUid`
- `outbox.sourceMessageId`

迁移只执行 `ALTER TABLE`，不重建账户、邮件或凭证数据。

## 构建

```powershell
.\gradlew.bat --stop
.\gradlew.bat clean compileDebugKotlin assembleDebug assemblePerformance --no-daemon
```
