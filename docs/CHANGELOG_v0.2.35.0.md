# BondMail v0.2.35.0

- `versionCode`: `55`
- `versionName`: `0.2.35.0`
- Room: `v8`（无数据库结构变化）
- MIME parser: `v8`
- HTML prepared-document cache: `layout-v28`
- Upgrade baseline: `v0.2.34.0`

## 1. 第一封未读邮件偶发打不开

录屏中的第一封未读邮件同时触发了三类工作：列表侧立即已读、详情侧正文读取、后台正文预取。旧实现会让“正文读取”和 `\Seen` 操作各自建立任务，并且 `markSeen=false` 的高优先级正文读取仍错误进入预取 Store 通道；在预取尚未退出时，首封未读邮件可能竞争同一个 JavaMail Store、重复 BODY 请求，失败后详情页又会立即再请求一次。

本版改为单一的 `prepareMessageForOpen()` 打开事务：

- 同一 `messageId` 的列表点击和详情页共享一个 in-flight BODY 结果；
- 正文使用 `BODY.PEEK` 高优先级读取，不再把“读取正文”和“写入已读标记”绑定在一次 IMAP 调用中；
- 高优先级 BODY 即使 `markSeen=false` 也固定使用 interactive Store 通道；
- 交互打开与后台预取同时使用独立的“账户通道 + 单邮件通道”，即使阻塞式 JavaMail 预取没有及时响应取消，用户点击也不会继续排在它后面；
- 正文写回 Room 时在同一事务内读取并保留最新已读、星标与远程图片设置，后台预取不会把刚刚打开的邮件重新写成未读；
- 本地 Room 立即标记已读，远端 `\Seen` 等正文成功并释放交互通道后再异步提交；
- BODY 失败时保留原生摘要和重试入口，不再马上消费下一条交互通道重复提交 flag-only 请求；
- 打开未读邮件会登记带 token 的 pending `\Seen` 意图；同步期间服务器仍返回旧 unread FLAGS 时，Room 继续保留本地已读，不会在正文加载过程中闪回未读；
- 同一邮件的远端 `\Seen` 任务去重；BODY 或 flag 提交失败时 pending token 保留，恢复网络后重试/重开会继续补交；用户主动重新标记未读会先清除 token，因此旧打开动作不会覆盖这个新选择；
- 从系统通知或深链直接进入详情、没有列表快照时，会在首个 Room 实体到达后只捕获一次初始未读状态，仍能正确执行“打开即已读”；
- 所有同时需要 BODY 与账户同步锁的路径统一按 `BODY -> accountSync` 顺序取得，移除预取与延迟 `\Seen` 之间的反向锁等待。

## 2. 标题和发件人区域不再随 HTML 切换变形

正文“先显示可读摘要、再显示最终 HTML”的方式保留，但主题、头像、发件人、地址、收件账户、日期不再由 Compose 和 Chromium 各绘制一遍后互相交叉淡化。

现在详情页采用稳定头部层：

- 从导航第一帧冻结当前邮件的可见头部文本；
- 主题和发件人信息始终只由 Compose 绘制一次；
- HTML 中保留完全等高的隐藏占位结构，让正文起点和滚动高度保持一致；
- WebView 滚动时，原生头部使用同一物理像素 `scrollY` 同步移动；
- 附件图标位置始终预留，正文解析完成后出现回形针也不会挤压日期或发件人；
- HTML 占位字体尺寸加入系统 `fontScale`，大字体模式下也尽量保持与 Compose 相同的换行和高度；
- 原生层与隐藏 HTML 占位都显式使用零字距，避免 Material 默认字距和 CSS 字距不同造成换行边界偏移；
- HTML 缓存键包含 `fontScale`，系统字体大小变化后不会复用旧几何。

这使正文可以从摘要平滑变成完整 HTML，而顶部文字的字重、换行、宽度和卡片位置保持不变。

## 3. WebView 可见时机与失败状态

- `onPageCommitVisible` 只保留为超时兜底，不再作为主要显示时机；
- 正常路径等待 `onPageFinished`、`VisualStateCallback` 和连续两个绘制帧后再显示；
- 含远程图片且允许加载时增加很短的资源稳定窗口，避免先显示灰色/半排版页面再跳成最终样式；
- 首次正文只进行一次短渐显；已展示过或系统关闭动画时直接显示；
- 保留的 WebView 重新挂载后至少等待一个绘制帧，不暴露空白 Surface；
- IMAP 正文失败时不再把整页替换为中央错误页，而是在原有主题、发件人和摘要上方显示底部重试条。

## 4. Binance 与同类事务邮件补充兼容

- Binance 域名即使模板的 `600px` 宽度仅存在于 Outlook 条件注释中，也按已知桌面画布回退为 `600px` 整体缩放；
- 新增对 MJML/EDM 常见“每个社交图标一个独立小 table/link”的识别；
- 对图标单元追加高优先级 inline/table 约束，抵消发件人移动端 CSS 中的 `width:100%` 和块级堆叠；
- SVG 保留声明高度，避免部分 Android WebView 中 `height:auto` 导致图标高度塌陷；
- 网易等已知手机事务邮件仍走 FLUID 布局，算法深色模式继续保留。

## 5. CircularRevealSwitch 评估

已检查你上传的 `CircularRevealSwitch` 源码。它的截图、圆形裁剪和扩散/收缩方向可以实现你描述的视觉效果，但当前库的日夜模式切换强依赖 `AppCompatDelegate.setDefaultNightMode()` 与 `ActivityCompat.recreate()`；项目 README 也明确提示 Android 11 可能因 Activity 重建闪屏，`setSwitcher()` 还会接管目标 View 的触摸监听。

BondMail 是单 Activity + Compose Navigation + 复用 WebView 的结构，直接接入这个实现会重新创建 Activity、导航栈和邮件 WebView，反而可能把刚修复的邮件闪烁重新带回来。因此本版只完成评估，没有添加依赖，也没有改动主题切换。后续更适合在 BondMail 内实现“无 recreate”的快照覆盖层：

- 主动切到深色：以设置页主题按钮/触点为圆心向外扩散；
- 主动切到浅色：旧深色快照向按钮圆心收缩；
- 跟随系统浅色→深色：从右上角扩散到左下角；
- 跟随系统深色→浅色：从左下角扩散到右上角；
- 动画结束后再释放快照层，不重建当前导航和邮件正文。

## 修改文件

- `app/src/main/java/com/bond/mail/data/mail/ImapClient.kt`
- `app/src/main/java/com/bond/mail/data/repository/MailRepository.kt`
- `app/src/main/java/com/bond/mail/ui/ViewModels.kt`
- `app/src/main/java/com/bond/mail/ui/MailApp.kt`
- `app/src/main/java/com/bond/mail/ui/screens/DetailScreen.kt`
- `app/src/main/java/com/bond/mail/ui/components/MailWebViewCache.kt`
- `app/build.gradle.kts`

## 真机重点回归

1. 冷启动后首先点击一封从未打开过的未读邮件，连续测试 Gmail、Outlook 与 163。
2. 快速返回并再次打开同一封邮件，确认没有重复 BODY、空白页或旧正文残影。
3. 观察“原生摘要 → HTML”全过程，主题、发件人、日期和头像不得改变字重、换行或位置。
4. 正文加载时断网，页面应继续显示摘要并在底部提供重试；恢复网络后可成功打开。
5. 打开 Binance USDC 通知，检查浅色/深色、600px 整体缩放及 7 个社交图标横排。
6. 从系统通知直接打开一封未读邮件，确认首次即可加载且随后保持已读。
7. 将系统字体调整到大号，确认隐藏 HTML 占位与原生头部不会造成正文突然上移或下移。
