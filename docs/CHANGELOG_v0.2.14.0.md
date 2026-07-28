# BondMail v0.2.14.0 变更说明

本版针对与 Thunderbird Android/K-9 Mail 的真机滑动和首次打开邮件体验差距进行专项优化。重点不是继续强制显示“120Hz”，而是减少列表每帧工作量、避免同步阻塞正文打开，并把 HTML 解析和 WebView 生命周期改成更接近成熟邮件客户端的分阶段流水线。

## 1. 远程图片按钮

- “显示远程图片”改为只有一个图片图标的悬浮按钮。
- 仅当当前邮件确实包含被阻止的远程图片时出现。
- 按钮从右上角移动到右下角、底部操作 Dock 上方，不再遮挡邮件顶部内容。
- 点击后直接加载远程图片并隐藏图标。

## 2. 首页滑动性能

- 邮件列表不再从 Room 读取每封邮件的大段 `bodyHtml/bodyText`，改为只查询列表需要的九个轻量字段。
- LazyColumn 滚动时不再复制、比较几十封邮件的完整 HTML 正文。
- 邮件卡片移除普通状态阴影及多余裁剪层。
- 品牌匹配、头像色调和时间文本增加 `remember` 缓存。
- 下拉刷新不再给整张列表增加一个持续存在的 GPU `graphicsLayer`，仅在实际拖动时使用布局偏移。
- 详情路由取消全屏滑动动画，避免创建 Chromium 的同时还绘制两张移动中的完整页面。

## 3. 刷新率策略

旧版本强制请求设备最高刷新率。真机帧统计显示 BondMail 的典型列表帧约为 11ms，而 120Hz 每帧预算只有 8.33ms，结果是不断错过 120Hz 截止时间，主观感受反而不如 Thunderbird 稳定的 90Hz。

本版清除应用的强制 120Hz 模式，让 Android/OEM 根据负载在 60/90/120Hz 之间动态选择。目标是稳定帧间隔，而不是只追求状态栏显示的刷新率数字。

## 4. 邮件打开和加载逻辑

参考 Thunderbird Android 的分阶段思路，并按 BondMail 架构重新实现：

- 邮件元数据和本地缓存先显示；缺失正文再后台下载。
- Jsoup 的 HTML 清理、宽度修复和主题注入从 Compose 主线程移到 `Dispatchers.Default`。
- 邮件正文页面复用一个 WebView，不再每次进入/退出都创建并销毁 Chromium。
- WebView 使用 `loadDataWithBaseURL("about:blank", ...)`、内嵌滚动条、无强制硬件层、宽视口和 overview 模式。
- 切换远程图片时保留旧正文，直到新页面提交，不再闪回整页加载状态。
- 当前邮件的 HTML 处理结果使用 LRU 缓存。

## 5. IMAP 并发和正文预取

- 同步邮件头、用户主动打开正文、后台正文预取使用独立的 IMAP 连接通道。
- 用户打开邮件不再等待同一账户长达十几秒的收件箱同步结束。
- 主动打开正文和后台预取分别串行，互不阻塞不同邮件。
- 最新两封缺失正文的邮件会在首页稳定后低优先级预取，且预取不会把邮件标记为已读。
- 用户主动打开时优先级高于预取；后台预取使用独立连接，避免抢占交互连接。
- JavaMail 开启 partial fetch，优先获取正文和内联资源，不为打开邮件下载普通大附件。

## 6. 第三方来源与许可证

本版研究并适配了 Thunderbird Android/K-9 Mail 中 `MessageWebView`、本地正文加载与部分下载的设计思路。相关项目使用 Apache License 2.0。BondMail 中的实现根据当前 Compose/Room/JavaMail 架构重新编写；许可证和署名见：

- `THIRD_PARTY_NOTICES.md`
- `licenses/Apache-2.0.txt`

## 编译

```powershell
.\gradlew.bat clean installDebug
```

性能比较建议使用：

```powershell
.\gradlew.bat clean installPerformance
```

Debug 包包含 `BondMail-Perf`、`BondMail-IMAP` 和 `BondMail-Web` 日志；Performance 包关闭调试开销，更适合和商店版 Thunderbird 对比滚动帧耗时。
