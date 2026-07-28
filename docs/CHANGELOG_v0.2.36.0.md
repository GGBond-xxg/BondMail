# BondMail v0.2.36.0

- `versionCode`: `57`
- `versionName`: `0.2.36.0`
- Room: `v8`（无数据库结构变化）
- MIME parser: `v8`
- HTML prepared-document cache: `layout-v31`
- Upgrade baseline: `v0.2.35.1`

## 1. 长主题不再压住头像和发件人卡片

上一版虽然把主题、头像和发件人固定为 Compose 原生层，但 HTML 内部仍按另一套固定字号估算隐藏占位。主题较长时，Compose 可能实际换成三行，而 HTML 占位仍只有两行高度，正文卡片就会提前开始并与标题、头像重叠。

本版改为由同一份测量结果决定原生层和 HTML 占位：

- 按当前手机真实可用宽度预先测量主题；
- 一行主题使用 `22sp / 28sp`，两行主题使用 `20sp / 26sp`，更长主题使用 `18sp / 24sp`；
- 最多显示三行，超出后省略，不再让超长交易通知无限把正文向下推；
- Compose 计算出的主题块实际高度直接写入 HTML 隐藏主题占位；
- 发件人块按系统 `fontScale` 计算固定高度，同一高度同时用于 Compose 和 HTML；
- 头像由 `52dp` 缩小为 `46dp`，发件人字号改为 `16sp`，邮箱与收件账户各保持一行；
- 日期和附件槽位仍提前保留，正文解析后出现回形针也不会让发件人名称重新换行；
- 内容卡圆角统一调整为 `22dp`。

这样正文从摘要切换成最终 HTML 时，主题、头像、发件人、邮箱、日期及正文起点使用同一套几何，不再出现标题压住卡片或顶部瞬间变形。

## 2. 从标题、头像和发件人区域也可以拖动邮件

主题和发件人是覆盖在 WebView 上方的 Compose 稳定层，旧版手势从该区域开始时不会进入 Chromium，所以用户会感觉头像区域“粘住”，只有从正文开始拖动才会滚动。

本版为稳定头部增加独立的垂直拖动桥接：

- 手指在主题、头像、发件人、邮箱或日期处拖动时，直接驱动当前 WebView 的 `scrollBy()`；
- 松手速度传给 WebView `flingScroll()`，保留惯性滚动；
- WebView 的真实 `scrollY` 继续作为头部位置的唯一来源；
- 头部改用布局 `offset` 跟随滚动，视觉位置和触摸命中区域一起离开屏幕，不会在滚走后继续挡住正文链接；
- 正文内链接、图片与缩放手势仍由 Chromium 自己处理。

## 3. 重复打开同一封邮件不再丢掉完整正文

重复打开仍然闪烁的一个直接原因，是返回列表后再次点击时执行了 `MessageListRow.toInitialMessage()`。该转换只保留列表字段，会主动丢弃已经加载完成的 HTML、纯文本、附件信息、解析版本和内容哈希，于是同一封邮件又被当成“只有标题、没有正文”的新邮件，重新经历摘要、WebView 挂载和 HTML 显示。

本版增加两级复用：

### 完整 MIME 快照

- 最近 `16` 封详情邮件保留完整 `MessageEntity`；
- 再次点击时仅用列表中的最新未读、星标、主题、时间等状态更新快照；
- 已解析的 `bodyHtml`、`bodyText`、附件 JSON、parser 版本和内容哈希继续保留；
- 正文打开事务返回后立即保存快照，不等待 Room 异步 invalidation 才生效；
- 通知/深链进入详情的邮件同样会在正文可用后写入快照。

### 两槽 WebView 保留池

- WebView 池从单实例改为最多两个已脱离页面的实例；
- 获取时优先选择内容键完全相同的 WebView；
- 第一次打开 A 后再打开 B，会创建第二个有界渲染器而不是立即覆盖 A；
- A → B → A 时可重新挂载 A 已经视觉提交的页面；
- 只有 `onPageFinished`、`VisualStateCallback` 和稳定绘制帧都完成后，页面才会登记为可复用；
- 同一已提交页面重新挂载时直接显示，不重播摘要到 HTML 的动画；
- 页面曾经显示过但对应 WebView 已被淘汰时，使用 `120ms` 轻交叉渐显，避免新栅格页面硬切造成单帧闪动。

## 4. 短邮件不再先占满屏幕再突然收缩

旧占位卡使用 `weight(1f)` 强制填满剩余屏幕。实际正文只有几行时，最终 HTML 卡片底边会从屏幕底部瞬间跳到正文下方，看起来像整个界面闪了一下。

现在：

- 移除占位卡的 `weight(1f)`；
- 占位卡按摘要或骨架内容的自然高度显示；
- 摘要最多七行，并使用更接近手机邮件客户端的 `14sp / 21sp`；
- 已知附件会在 HTML 完成前显示尺寸接近最终结果的附件条；
- 占位内容后续变化使用 `240ms` 尺寸缓动；
- 第一次 HTML 显示保留单次 `180ms` 渐显，并增加很小的 `6dp` 落位移动，使短正文的最终位置不会直接硬切。

## 5. Binance 与桌面邮件可读性

Binance 不同批次模板并不完全一致：部分模板只有 Outlook 的 `600px` 兼容外框，另一些模板在 `<style>` 中包含真正的手机 `max-width` 媒体查询。旧版统一把 Binance 当作桌面画布整体缩放，后者会被二次缩小，正文就像整页缩略图。

本版在替换 viewport 之前先保留模板的响应式信号：

- Binance 域名存在真实手机媒体查询时，采用 `FLUID` 手机布局；
- 只有固定桌面模板仍保留 `600px` 完整画布回退，避免表格、按钮和社交图标结构被拆散；
- 固定桌面回退的 WebView 文字缩放调整为 `118%`，改善无法安全重排的旧 Newsletter 可读性；
- X、Telegram、Facebook、LinkedIn、YouTube、Reddit、Instagram 等图标行及独立 MJML 小表格继续强制横排；
- 网易等已知手机事务邮件仍保持 FLUID；
- WebView 算法深色模式继续保留。

## 6. CircularRevealSwitch

本版仍未接入 CircularRevealSwitch。主题环形揭示需要在不重建 Activity、不中断 Compose 导航、也不销毁当前邮件 WebView 的前提下单独实现。先完成邮件详情页的长标题、滚动、正文复用与短邮件过渡，再进入主题动画阶段。

## 修改文件

- `app/build.gradle.kts`
- `app/src/main/java/com/bond/mail/data/mail/ImapClient.kt`
- `app/src/main/java/com/bond/mail/ui/MailApp.kt`
- `app/src/main/java/com/bond/mail/ui/components/MailWebViewCache.kt`
- `app/src/main/java/com/bond/mail/ui/components/MailWebViewPool.kt`
- `app/src/main/java/com/bond/mail/ui/screens/DetailScreen.kt`
- `README.md`
- `README_FIX.txt`
- `APPLY_PATCH.md`
- `VALIDATION_v0.2.36.0.txt`
- `docs/IMPLEMENTATION_STATUS.md`
- `docs/TEST_CHECKLIST.md`

## 本机构建命令

```powershell
.\gradlew.bat --stop
.\gradlew.bat clean compileDebugKotlin assembleDebug assemblePerformance --no-daemon
```

## 真机重点回归

1. 打开截图中的 Binance 长主题邮件，确认主题最多三行，发件人卡片从主题后方开始且完全不重叠。
2. 分别从主题、头像、发件人、邮箱和正文开始上滑，确认所有区域都能连续滚动并有惯性。
3. 打开 A、返回重开 A；再执行 A → B → A，对比 K-9，检查是否仍出现摘要重播、白帧或旧正文残影。
4. 打开正文仅一两行、只有附件或很短纯文本的邮件，观察卡片底边是否自然落位，而不是从整屏高度突然跳上来。
5. 检查 Binance 浅色/深色、按钮、反钓鱼码、免责声明和社交图标；同时回归 163、Neverless、GitHub 和普通纯文本邮件。
6. 将系统字体调为默认、大号和更大档位，确认主题与发件人占位仍不重叠。
