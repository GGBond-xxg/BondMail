# BondMail

## v1.1.1 未读邮件即时切换已读样式

点击未读邮件时，卡片底色现在与蓝色标记、圆点和文字字重一起立即切换为已读样式。详情页打开与返回动画不会再冻结颜色过渡中的蓝色帧。完整说明见 `docs/CHANGELOG_v1.1.1.md`。

## v1.1.0 OAuth 配置、稳定导航与交互动效

BondMail 1.1.0 支持在应用包中提供 Gmail 与 Outlook OAuth 公共客户端配置，并保留手动导入配置作为覆盖方式；同时修复冷启动和首次打开未缓存邮件时的空白闪帧。

二级页面统一使用覆盖式打开动画和 Telegram 风格的预测性返回，修复部分 ColorOS 设备一次手势触发两次返回的问题；进入页面前会先结束按压、水波纹和阴影反馈，返回后不会继续播放旧反馈。底部“邮件 / 联系人 / 设置”使用普通淡入淡出切换。完整说明见 `docs/CHANGELOG_v1.1.0.md`。

## v1.0.0 多账户收发信、后台同步、联系人头像与完整项目信息

BondMail 1.0.0 完成 Gmail、Microsoft OAuth 与通用 IMAP/SMTP 登录流程，支持 163、126、Outlook、Gmail 等邮箱来源图标，自定义账户/联系人头像、邮箱显示大小写、联系人添加编辑与删除。短周期同步可在应用退到后台后继续检查新邮件，并把新邮件通知与常驻同步提示拆分为独立频道。

首页搜索、邮件列表、联系人、设置与详情页统一了滚动导航、动态底部安全区、圆角点击反馈和品牌头像；长标题、Apple/Newsletter HTML、冷启动正文加载和邮件尾部留白也得到修正。设置页重新分组，并新增“关于 BondMail”、开源声明、MIT 应用许可、隐私条款和 GitHub 项目主页。完整说明见 `docs/CHANGELOG_v1.0.0.md`。

## v0.2.38.0 原始图标、前台静默刷新、通知恢复与全高邮件加载动画

本版真正使用用户提供的 `BondMailDefIcon(2).png` 与 `BondMailmonaIcon(1).png`：Manifest、圆形/方形 Adaptive Icon、Android 13+ monochrome 主题图标、旧版密度图标和启动页全部切换到新的 `ic_launcher_bondmail` 资源名，不再使用上一版重绘信封。

应用内下拉、点击刷新、前台恢复刷新和手动 WorkManager 同步现在只更新列表并静默消费通知 UID；后台同步仅在应用不在前台时弹出通知。新通知频道 `new_mail_alerts_v3` 使用 HIGH 重要级别、默认声音、振动和悬浮横幅条件，绕开旧静默频道无法通过升级代码修复的问题。

未加载邮件的正文卡默认填满顶栏与底部操作栏之间的区域，使用最多 7 行骨架与 40dp 圆形加载动画。Prepared HTML 增加保守的短/长内容提示：明确的短邮件先以阻尼弹簧收回到近似最终高度再渐显，长邮件保持展开后完整揭示；HTML 缓存升级到 `layout-v33`。抽屉额外邮箱使用顶部展开/压缩、渐显/渐隐和同一箭头旋转动画，后续入口连续移动。完整说明见 `docs/CHANGELOG_v0.2.38.0.md`。

## v0.2.37.0 图标、整页 Q 弹、HTML 渐显与发件人模板修复

启动图标改为更轻的圆角描边信封，缩进 adaptive icon 安全区域，并新增 Android 13+ 独立 monochrome 主题前景；全彩图标使用较柔和的蓝灰背景，不再像旧实心信封一样比 Gmail 明显更重。

邮件顶部下拉现在由详情页维护共享弹性位移，HTML、纯文本预览、主题、头像、发件人、邮箱和日期会作为同一张页面一起 Q 弹。HTML 揭示从 Compose 外层透明度改为 WebView 自身 `alpha/translationY`：首次在最终视觉提交后用 260ms 强调减速渐显，已看过但需重新栅格化时用 150ms，精确复用已提交页面时直接显示；加载纯文本卡取消外投影，最终 HTML 卡阴影同步降低。

Grab 的伪响应式 600px 表格改为保留完整画布一次缩放，修复绿色头图和正文右侧裁切；Facebook/Meta 邮件独立使用 132% 文字缩放，并只放大可确认的品牌 Logo。首页和联系人置顶改为分段动量滚动，先加速、跳过不可见长距离、再用强调减速曲线停在顶部。HTML 缓存升级为 `layout-v32`，Room 与 MIME parser 仍为 v8。完整说明见 `docs/CHANGELOG_v0.2.37.0.md`。

## v0.2.36.0 长标题、头部滚动、重复打开与手机正文密度

邮件主题现在会根据真实手机可用宽度在 `22sp / 20sp / 18sp` 三档中选择，最多三行；Compose 实际测量出的主题高度和随系统字号变化的发件人区域高度会原样写入 HTML 隐藏占位，用于避免长标题把头像、发件人或正文顶住。头像缩小为 46dp，发件人、地址和收件账户改为更接近手机邮件客户端的紧凑层级。主题和头像区域支持直接拖动及惯性滚动底下的 WebView，滚出屏幕后触摸区域也同步离开，不会继续挡住正文链接。

重复打开不再把完整 `MessageEntity` 降级成仅列表字段：最近 16 封完整 MIME 快照会保留正文、附件和内容哈希；两槽 WebView LRU 优先复用同一封已经视觉提交的页面，并保留 A→B→A 的最近页面。短邮件占位卡取消填满剩余屏幕，改为按摘要/附件自然高度显示并做 240ms 尺寸缓动；初次 HTML 仍为 180ms 单次揭示，已显示过但栅格页被淘汰时只做 120ms 轻交叉渐显。

Binance 等包含真实手机媒体查询的事务邮件不再强制把 600px Outlook 兼容画布整体缩成缩略图，而是采用 FLUID 手机布局；社交图标行和独立 MJML 小表格继续强制横排，算法夜间模式保持。HTML 缓存升级为 `layout-v31`，Room 与 MIME parser 均保持 v8。CircularRevealSwitch 主题动画仍暂缓，先完成邮件详情稳定性。完整说明见 `docs/CHANGELOG_v0.2.36.0.md`。

## v0.2.35.1 Compose 编译热修复

修复 `DetailScreen.kt:406` 中 `Modifier.padding(horizontal = 24.dp, bottom = 96.dp)` 没有对应 Compose 重载而导致 Debug/Performance 同时编译失败的问题。现改为 `padding(start = 24.dp, end = 24.dp, bottom = 96.dp)`，布局数值和视觉效果不变。完整说明见 `docs/CHANGELOG_v0.2.35.1.md`。

## v0.2.35.0 首封未读打开、稳定详情头部与 HTML 最终帧

首封未读邮件的列表点击和详情正文现在共享同一个 BODY 打开事务：正文固定走 interactive Store 通道，交互与后台预取使用独立的账户/单邮件通道，先成功取得内容，再通过 pending token 异步去重提交远端 `\Seen`；预取写回会事务化保留最新已读状态，服务器旧 FLAGS 也不会把正在打开的邮件恢复成未读。正文失败时继续保留主题、发件人与摘要，并在底部提供重试。

详情主题、头像、发件人、地址、日期改为唯一的原生稳定层；HTML 仅保留等高隐藏占位并跟随 WebView 滚动，所以“先显示摘要、再显示 HTML”时顶部字体、换行和卡片几何不再切换变形。WebView 等待最终可见帧后只做一次短渐显，HTML 缓存升级为 `layout-v28`。完整说明见 `docs/CHANGELOG_v0.2.35.0.md`。

## v0.2.34.0 邮件打开单次渐显与 Binance 排版修复

根据真机录屏移除正文打开路径中叠加的 300ms/450ms/300ms 延迟：详情第一帧立即显示主题、发件人和正文摘要原生壳层，冷 WebView 只保留 90ms 稳定帧，Chromium 可见后进行一次 180ms 交叉渐显；缓存重开直接显示，不再出现空白挂载帧或连续两次闪动。

Binance 事务邮件改为保留完整桌面表格画布并按屏幕整体缩放，同时识别社交/支付小图标行并保持横排。网易事务邮件的 FLUID 手机布局与 WebView 算法深色模式保持不变。HTML 缓存升级为 `layout-v26`，Room 仍为 v8，MIME parser 仍为 v8。完整说明见 `docs/CHANGELOG_v0.2.34.0.md`。

## v0.2.33.1 编译热修复

修复 `SmtpClient.describeAttachments()` 公开函数暴露 `internal MailAttachmentInfo` 导致的 Kotlin 编译失败。函数改为模块内可见，不改变附件解析、发送、已发送去重、删除或邮件展示逻辑。

# BondMail Kotlin

## v0.2.33.0 附件 MIME、已发送去重、删除与首次展示节奏

真机日志显示附件邮件的完整 RFC822 已成功下载，但 JavaMail 对部分 `multipart/mixed` 邮件返回空正文。本版加入原始 boundary/传输编码回退解析，并在 Room v8 持久化附件文件名、类型与大小；详情页显示回形针和附件卡。`正在发送` 完成后会在事务中替换成真实 Sent UID，不再与服务器记录重复；删除会真正作用于远端 UID。首次打开在可见帧完成后稳定 300ms，再渐显 300ms；同一内容再次打开直接复用缓存。HTML 缓存升级为 `layout-v25`。完整说明见 `docs/CHANGELOG_v0.2.33.0.md`。

## v0.2.32.0 选择工具栏、置顶按钮、正文渐显与附件邮件读取

长按选择模式现在会禁用下拉刷新，顶部动作按“全选/取消全选、删除、已读/未读”排列。首页和联系人列表离开顶部一定距离后，会在右下角显示带进入/退出动画的置顶按钮；点击或手动回到顶部后自动消失。

邮件详情首次打开先保持稳定背景，再以 300ms 渐显最终 WebView 内容；已展示过的邮件直接挂载，不重复播放加载动画。附件邮件打开路径改为对 8MiB 以内普通邮件执行一次完整 IMAP PEEK BODY 获取，并提供 20MiB 上限的原始 MIME 本地重解析回退，改善 163 等服务商多段 MIME 懒加载超时后已发送和收件箱都打不开的问题。Room 仍为 v7，HTML 仍为 `layout-v24`。完整说明见 `docs/CHANGELOG_v0.2.32.0.md`。

## v0.2.31.0 即时已读、Telegram 预测性返回、服务器已发送/草稿与未读入口

打开邮件现在会在导航前立即更新全部列表快照，并同步提交 IMAP `\Seen`；返回列表不会再短暂恢复未读。详情、服务商和邮箱配置页面统一为 Telegram 式不透明滑动返回，预测性返回时只让当前页面跟手向右移动，下层页面保持稳定。正文 WebView 使用短透明度/缩放揭示覆盖骨架，继续避免重复真实文字带来的字重变化。

首页在收件箱与星标之间新增未读邮件。已发送现在读取服务器 Sent 文件夹，并把本地排队邮件以“正在发送 → 已发送”动画展示；SMTP 成功后 Sent 对账失败不会重复投递。草稿箱合并服务器 Drafts 与本地草稿，写信返回可保存或舍弃，保存后立即显示并由 WorkManager 上传到上游邮箱。Room 升级为 v7，HTML 保持 `layout-v24`。完整说明见 `docs/CHANGELOG_v0.2.31.0.md`。

## v0.2.30.0 已读状态竞态、详情字体与重开首帧修复

真机日志确认，邮件打开后过一会重新变成未读，是周期同步先取得旧 FLAGS、用户随后标记已读、旧同步最后才写入 Room 的竞态，并非 Gmail 或 Outlook 单独问题。本版为每个账户增加 FLAGS generation：用户已读/未读与星标操作推进版本，过期同步快照不再覆盖较新的本地操作；远端失败会回滚本地状态。

详情加载阶段不再用 Compose 绘制一套与 WebView 相同的真实主题和发件人文字，而改为中性 Gmail 骨架，因此不会从 Compose 字体切换到 Chromium 字体时出现“粗 → 细”。重开缓存邮件时等待保留 WebView 完成一帧挂载再显示，并在导航前同步保存详情初始快照，避免一帧空页面。Room 仍为 v6，HTML 缓存仍为 `layout-v24`。完整说明见 `docs/CHANGELOG_v0.2.30.0.md`。

## v0.2.29.1 详情首帧不透明、缓存直出与字体稳定

根据真机录屏确认，详情文字“粗 → 细”和重新打开整页闪烁主要来自路由级淡入：详情内容与底下邮件列表短暂重叠。详情前进/返回现在只保留轻量水平位移，目的页面从第一帧起完全不透明。HTML LRU 与单实例 WebView 的已提交页面也可在首帧同步复用，同一封邮件重新打开不再先进入加载占位。Compose 占位与 WebView 生成头部统一使用 Android `sans-serif` 和 Gmail 常规主题字重，HTML 缓存升级为 `layout-v24`。完整说明见 `docs/CHANGELOG_v0.2.29.1.md`。

## v0.2.29.0 Gmail 内容卡、详情文字稳定与账户名称上限

邮件详情顶部栏现在显示当前账户的显示名称，返回、星标、更多按钮和底部 Dock 保持不变。账户显示名称上限统一为 12 个字符，添加、编辑、OAuth 和旧账户启动整理都使用同一规则。

详情页改成更接近 Gmail 的层级：主题保留在页面背景上，发件人信息和原始邮件正文合并到一张 24dp 圆角内容卡中，并显示邮件时间。WebView 不再与包含相同文字的 Compose 占位层整页交叉淡化，而是在 Chromium 完成可见提交后一次切换，消除主题和发件人文字“粗 → 细”的闪烁。HTML 缓存升级为 `layout-v23`。完整说明见 `docs/CHANGELOG_v0.2.29.0.md`。

## v0.2.28.1 Gmail 风格详情头部与通用正文预热

本版不按发件人决定正文下载速度。首页滚动停止后预热当前可见邮件及少量前后项，用户点击时当前邮件会切换到高优先级正文通道；首次同步预热窗口提高到最近 32 封小邮件，已有账户重启后约 1.8 秒开始预热最近 12 封。正文仍保存在 Room，HTML 清理结果缓存扩大到 64 份，单实例 WebView 会保留最后一封已经视觉提交的本地文档，因此返回后立即重开同一封邮件可直接复用已栅格化页面。

详情顶部栏改为与页面背景一致，返回、星标、更多按钮和底部 Dock 不变；邮件主题移到正文顶部，以 Gmail 风格多行大标题显示，主题下方继续显示头像和发件人信息。正文下载期间也会立即显示真实主题与发件人。HTML 缓存升级为 `layout-v22`。完整说明见 `docs/CHANGELOG_v0.2.28.1.md`。

## v0.2.27.0 网易首同步提速、手机卡片恢复与 WebView 自动恢复

本版根据 v0.2.26.0 的完整 Debug 录屏和真机回归继续修复：首次同步先用一次 FETCH 取得 UID/Envelope/Flags，再排序并渐进写入 Room，避免网易服务器在排序阶段逐封查询 UID；最近小正文预热由 12 封提高到 24 封，并拆成每批 8 封，改善 GitHub、网易安全邮件第一次打开仍需等待 8～15 秒的问题。

HTML 缓存升级为 `layout-v21`。网易事务邮件明确使用 FLUID 手机布局，并可从 HTML/CSS 或正文覆盖率识别内部手机卡片，避免 600px Outlook 外框把内容缩成屏幕中央小图；Cloudflare 的桌面 Newsletter 缩放路径保持不变。详情 WebView 增加渲染进程退出恢复，死亡实例不会回到复用池。通知拒绝动效移除重复位置动画，设置中的“允许”改为中性色。完整说明见 `docs/CHANGELOG_v0.2.27.0.md`。

## v0.2.26.0 通知权限动效、安全输入与 HTML 图标兼容

本版根据 v0.2.25.0 真机回归继续收尾通知权限、账户输入和邮件正文兼容性。添加邮箱后不再自动弹出 Android 权限窗口；首页先显示“拒绝通知 / 允许通知”自定义提示，只有主动允许才请求系统权限，拒绝后不再重复提示。提示卡、欢迎卡和邮件列表使用位置动画连续补位，权限窗口关闭后内容不会瞬间跳上去；设置页增加“权限 → 消息通知 → 允许/去授权”。

授权码和密码输入框改为 Password 键盘类型，并提供可隐藏输入法的“完成”动作；首页左上抽屉按钮移除圆形底色。HTML 准备缓存升级为 `layout-v20`，支持常见 lazy-load 图片属性、协议相对 URL 和小图标尺寸保护，同时只对确实过窄的 FLUID 主内容卡片做手机宽度扩展。K-9 式渐进首同步、Header/Body 分离、WebView 预热、163 双协议重新授权和 Newsletter 双模式均保留，正文预取开始时间与最近邮件数量进一步优化。完整说明见 `docs/CHANGELOG_v0.2.26.0.md`。

## v0.2.25.0 邮件正文恢复、详情失败保护与 Performance R8 修复

本版根据真机日志修复所有邮件正文永久停在加载骨架的问题。正文下载和 MIME 解析实际已成功，失败点是 `MailWebViewCache` 的 CSS 规则正则在 Android ICU 上抛出 `PatternSyntaxException`，并导致该类在当前进程中无法再次初始化。正则已改为显式匹配成对花括号，HTML 准备缓存升级为 `layout-v19`；详情页同时增加本地 HTML/WebView 失败提示和重试入口。

详情顶部移除额外 12dp 空白，发件人区域紧贴真实顶栏底部；账户编辑文案缩短为“更改授权码/密码”。Performance 构建补充 Nimbus JOSE 可选 Tink/Bouncy Castle 引用的 R8 忽略规则，解决 `minifyPerformanceWithR8` 中止。Room 仍为 v6，Gmail/Microsoft OAuth、K-9 式渐进首同步和授权码双协议验证保持不变。完整说明见 `docs/CHANGELOG_v0.2.25.0.md`。

## v0.2.24.0 Gmail/Microsoft OAuth、授权码重设与 HTML layout-v18

本版正式接入 Gmail 与 Microsoft 安全登录：Gmail 使用 Google Identity Services AuthorizationClient，Outlook/Hotmail/Live 使用 MSAL Android；IMAP 与 SMTP 均通过 XOAUTH2 使用短期 Access Token，Token 不写入 Room、DataStore、普通凭证存储或日志。邮箱编辑窗口新增 OAuth 重新授权，同时 163/126、iCloud、Yahoo 等授权码账户可以在不删除本地邮件的情况下重新填写客户端授权码/App 专用密码；新凭证只有在 IMAP 与 SMTP 均验证通过后才替换旧值。

Room 升级为 v6，给账户表增加可空的服务商账户 ID；旧版 Gmail/Microsoft 密码账户保留本地邮件并转为“待 OAuth 重新授权”，不会把旧密码误当作 XOAUTH2 Token。HTML 邮件缓存升级为 `layout-v18`：网易等带桌面兼容外框的手机邮件不再被整体缩成小卡片，Cloudflare 等 480–1200px Newsletter 会从 HTML、内联样式和实际命中正文的 `<style>` 规则识别完整桌面画布，关闭 WebView 二次 overview 后整体缩放，改善右侧裁切。完整变更见 `docs/CHANGELOG_v0.2.24.0.md`，OAuth 架构与控制台配置见 `docs/README_OAUTH.md`，真机回归见 `docs/TEST_CHECKLIST.md`。

## v0.2.23.0 动态详情安全区、桌面邮件缩放与 K-9 式渐进首同步

本版继续处理 Thunderbird/K-9 对比中的详情显示和首次同步体验。邮件详情顶部不再假设固定 96dp，而是读取真实状态栏高度并加上 64dp 顶栏和 12dp 安全间距；骨架与最终 HTML 共用相同几何。GitHub 等短邮件增加稳定滚动余量，WebView 持有完整触摸序列，并可通过手指累计位移驱动顶部栏和底部 Dock，因此正文不足一屏时也不会像拖不动。

HTML 缓存升级为 `layout-v16`。普通邮件继续使用手机宽度流式布局；检测到约 480–900px 的 Cloudflare/newsletter 桌面画布时，保留原始表格和列关系，并按当前 Android 窗口宽度整体缩放，发件人头部不参与缩放。首次同步继续使用一次批量邮件头 FETCH，但 Room/UI 改为渐进提交：第一条先出现，最近 8 封逐条进入，剩余邮件按小批次补齐；最近最多 8 封、128 KiB 以下的小邮件正文通过一次批量 BODY FETCH 预取，大邮件按需下载。中断恢复仍属于历史通知基线，不会把旧邮件批量通知。完整说明见 `docs/CHANGELOG_v0.2.23.0.md`，真机检查见 `docs/TEST_CHECKLIST.md`。

## v0.2.22.0 单一刷新反馈、Gmail 选中形变、正文滚动与前后台同步恢复

本版继续根据首页刷新、邮件正文滚动、Gmail 长按对比、写邮件展开、添加邮箱和前后台切换复现资料优化。首页下拉仍保留跟手阻尼与阈值触觉，但手势阶段不再绘制圆形加载器；松开触发同步后只显示顶栏底部原有的 3dp 进度线。邮件、联系人和搜索结果共用 `GroupedListSurface`，阴影、ripple 与裁切始终使用同一个实时 Shape；分组外圆角缩小为 12dp、内部圆角为 5dp，选中邮件会平滑变为四角 12dp，取消后恢复所在首/中/尾位置。

未读邮件增加动态主色底纹、左侧强调线、主色时间和未读圆点；时间与星标固定在 52dp 右侧列并统一右对齐。详情 WebView 持有完整触摸序列、关闭嵌套滚动交接，HTML 缓存升级为 `layout-v15`，进一步限制桌面固定宽度和深层表格，改善 GitHub、Cloudflare 等邮件的滚动与手机宽度适配。写邮件默认展开调整为 72%，全屏时停在状态栏下方 8dp；添加邮箱后缀列改为右对齐自适应宽度并恢复清晰主色光标。首次同步只建立历史邮件通知基线，前后台切换使用同步 generation 隔离旧任务，避免历史邮件批量通知、返回后加载不消失或后台失败误报。完整说明见 `docs/CHANGELOG_v0.2.22.0.md`。

## v0.2.21.0 选择退出稳定、文件夹缓存、Gmail 首尾圆角与统一动态主题

本版继续根据 `长按取消.mp4`、`暂无邮件.mp4` 和 Gmail 浅色/深色对比图调整交互与主题。取消最后一个邮件勾选时，离场中的选择工具栏会保留最后一次非零数量并平滑淡出，不再闪出 `0 已选择`；邮件选中背景也会短渐变恢复。已发送、草稿、垃圾邮件等文件夹新增“账户 + 文件夹”内存快照，切换前先读取内存或 Room 缓存，有内容直接显示内容，无内容直接稳定显示“暂无邮件”。

邮件、联系人和搜索结果改为 Gmail 分组圆角：第一条放大顶部两角，最后一条放大底部两角，中间条目保持低圆角，左右滑动背景使用同一 Shape。新增全局 `BondMailSurfacePalette`，把页面、顶部 Chrome、内容卡片、底部 Dock、输入框、抽屉、弹层和写信 Sheet 的 Surface 层级统一映射到浅色、深色与 Monet 动态取色；首页、联系人、设置、写邮件、抽屉、详情和添加邮箱均已接入。添加邮箱的用户名与 `@domain` 也合并成同一个输入框，后缀和展开图标固定在最右侧，不再使用互相挤压的左右双框。完整说明见 `docs/CHANGELOG_v0.2.21.0.md`。

## v0.2.20.0 顶栏同步、通用滚动 Chrome、紧凑 Gmail 内容区与详情首帧稳定

本版针对 `Record_2026-07-24-18-59-05` 真机录屏继续修复首页顶栏：收起状态的搜索按钮重新放回与抽屉、更多、添加邮箱相同的顶栏行，只有搜索容器展开/收回期间才由 Overlay 临时接管。普通滚动时四个入口共享同一个 `translationY`、同一时长和同一缓动，不再出现搜索按钮单独提前、延后或斜着离场。

首页、联系人和设置现在复用 `ChromeScroll.kt` 的滚动方向累计、顶部复位和位移动画。联系人向上滚动会同时收起顶部搜索栏与底部导航，反向滚动恢复；设置页虽然没有固定标题，也会按相同规则收起/恢复底部导航。邮件与联系人主体改为对比图中的紧凑 Gmail 风格：6dp 低圆角、3dp 行间距、统一左右留白和更清楚的已读/未读字体层级。写邮件按钮与详情删除按钮复用同一个带外圈阴影和轮廓的圆形动作组件，浅色模式下不再像贴在页面上的平面图。

邮件详情不再用真实发件人文字制作 Compose 占位层，而是使用稳定骨架；WebView 在 `postVisualStateCallback` 确认最终排版已栅格化后再渐显。HTML 布局缓存升级为 `layout-v12`，发件人头部统一字号和字重、只在头部禁用字体合成，并允许过长邮箱自然换行，避免打开邮件时名称/邮箱粗细跳变以及尾部突然变成 `...`。完整说明见 `docs/CHANGELOG_v0.2.20.0.md`。

## v0.2.19.0 邮件渐显、刷新反馈、统一顶栏与 HTML 宽度修复

本版继续处理真机录屏中的细节问题：邮件正文加载期间先显示与主题一致的发件人占位层，正文可见后短渐显，不再直接暴露 WebView 白色首帧；首页恢复下拉圆形进度和顶栏同步进度线。抽屉、搜索、更多、添加邮箱统一为同尺寸圆形顶栏动作，Yahoo 等多后缀选择改为不挤压表单的悬浮菜单。

首页、联系人、设置、写信、添加邮箱、抽屉和详情页重新统一背景与 Surface 层级；HTML 邮件布局缓存升级为 `layout-v9`，修复桌面模板侧边距过大、引用邮件偏右、正文过窄以及标题被裁切。详情滚动阈值按设备 density 换算，顶栏收回后不再残留未消耗的顶部空白。完整说明见 `docs/CHANGELOG_v0.2.19.0.md`，动画规则继续以 `docs/README_MOTION_SPEC.md` 为准。

## v0.2.18.0 搜索单容器、写信布局与首次邮件性能修复

本版将首页搜索按钮和展开后的搜索框合并为同一个容器所有者，打开、关闭及顶栏滚动过程中不再切换两套图标；首页和详情顶栏恢复不透明背景，避免滚动恢复后与邮件内容重叠。写信页删除底部发送和正文区域附件按钮，只保留右上角发送与附件图标；抄送/密送改为收件人右侧展开，邮箱后缀改为仅在输入过程中出现的临时建议。无论写信抽屉处于 80% 还是全屏，返回键均直接关闭。

为降低进程冷启动后第一次打开 HTML 邮件的停顿，本版在首页首帧之后的空闲窗口预热单实例 WebView，并在未预热完成时让详情原生壳层先完成 Forward 动画，再挂载 Chromium；离开详情后复用同一 WebView，内存压力下自动释放。完整说明见 `docs/CHANGELOG_v0.2.18.0.md`，动画约束继续以 `docs/README_MOTION_SPEC.md` 为准。

## v0.2.17.1 Compose 编译兼容性修复

删除当前 Compose UI 版本不支持的 `androidx.compose.ui.input.pointer.consume` 顶层导入；拖动排序继续直接调用 `PointerInputChange.consume()`。该修复不改变 v0.2.17.0 的功能、动画、附件、数据库或邮箱管理逻辑。完整说明见 `docs/CHANGELOG_v0.2.17.1.md`。

## v0.2.17.0 写信底部抽屉、附件、邮箱抽屉管理与交互整理

本版继续完成首页与写信流程的结构调整：性能构建启用 R8 与资源压缩；底部导航选中态改为与点击范围一致的完整椭圆，写信按钮和详情删除按钮使用完整强调色容器；删除邮件增加确认提示。首页邮件手势改为右滑切换已读/未读、左滑切换星标，并加强纵向滚动优先级。

写邮件统一为覆盖当前页面的 Material 3 底部抽屉，首次打开约占屏幕 80%，上滑可展开全屏、下滑可关闭；首页、联系人、回复和转发都复用同一个入口。写信页新增系统文件多选、附件移除、Outbox 持久化和 SMTP `multipart/mixed` 发送。数据库升级为 v5，并提供 4→5 无损迁移。

邮箱管理从设置页迁入首页抽屉：默认显示前三个邮箱，超过三个才出现展开按钮；每个邮箱支持长按拖动排序、重命名和带确认的本地删除，排序会同步用于首页、后台同步和写信账户选择。搜索容器转场也重新统一了源图标与动画图标的生命周期，避免重复绘制造成抽动。完整变更见 `docs/CHANGELOG_v0.2.17.0.md`，动画规则见 `docs/README_MOTION_SPEC.md`。

## v0.2.16.1 动画稳定性热修复

本版针对 v0.2.16.0 真机录屏中的动画闪烁继续调整：写信页从右下角以 Bottom Sheet 方向进入，不再执行 FAB 共享边界；首页顶部标题栏、底部 Dock、邮件详情顶部栏和底部操作栏改为保持布局不变的屏幕外位移动画，并加入 52/56px 方向阈值，避免滚动轻微抖动时反复开关。搜索按钮使用真实的尺寸、位置与圆角插值扩展为搜索框，键盘会等容器过渡完成后再获得焦点。邮件左右滑动在列表纵向滚动期间禁用，并提高触发距离；顶部文件夹 Chip 的图标和文字改为整体居中。完整规则继续写入 `docs/README_MOTION_SPEC.md`。

## v0.2.16.0 Forward/Backward 动画、滚动组件、搜索容器与后台收信修复

本版按 Material 3 transition pattern 重新调整邮件详情导航：邮件列表进入详情采用 Forward，返回采用严格反向的 Backward，不再让 WebView 参与邮件卡片共享边界，解决上一版共享元素与正文首帧叠加造成的闪白。首页顶栏、底部 Dock 与详情操作 Dock 会按滚动方向从对应屏幕边缘退出和返回；搜索入口扩展为搜索容器；通知条从顶部进入、导航抽屉从左侧进入、键盘与底部组件保持从底部进入的空间关系。

后台收信不再把 1/5/10 分钟设置静默当成 15 分钟：短周期改为持久化的一次性 WorkManager 链，15 分钟及以上继续使用周期任务；重复打开 App 不会重置下一次运行时间。新邮件通知改用新的高重要性声音频道，修复旧频道被创建为静音后升级仍无声音的问题。HTML 邮件 viewport、引用邮件宽度和 WebView 缩放策略也再次调整，避免转发/引用内容被整体缩得过小。完整变更见 `docs/CHANGELOG_v0.2.16.0.md`，动画规则以 `docs/README_MOTION_SPEC.md` 为准。

## v0.2.15.1 编译兼容热修复

修复 v0.2.15.0 动画重构引入的 Compose API 导入错误：系统动画缩放改为读取 Android `ANIMATOR_DURATION_SCALE`，`MutableTransitionState` 改用 `androidx.compose.animation.core`，并移除 `calculateTopPadding` 的无效顶层导入。功能与动画设计不变。

## v0.2.15.0 Material 3 动画、首轮滚动稳定性与邮件图标修复

本版按 Material 3 Motion 关系恢复并重构动画：邮件卡片和主写信按钮使用 Container Transform，主 Tab 使用 Fade Through，搜索/回复/转发使用 Shared Z，添加邮箱流程使用 Shared X；WebView 不参与共享尺寸动画。后台同步与正文预取会避开邮件列表正在滚动的窗口，并在 App 回到前台后保留首轮交互保护时间，减少重新打开 App 后 90Hz 降到 30Hz 的波动。相对路径资源与内联 SVG 图标也已修复。完整动画规则已纳入 `docs/README_MOTION_SPEC.md`，变更见 `docs/CHANGELOG_v0.2.15.0.md`。

## v0.2.14.0 Thunderbird 对比优化：稳定帧率、正文流水线与 WebView 复用

本版根据 Thunderbird Android 源码和真机视频调整列表与正文加载架构：邮件列表改为轻量 Room 投影，HTML 清理移出主线程，详情页复用 WebView，同步/交互正文/后台预取使用独立 IMAP 通道。远程图片图标移到右下角；同时取消强制 120Hz，让系统按负载选择更稳定的 60/90/120Hz。完整变更见 `docs/CHANGELOG_v0.2.14.0.md`。

## v0.2.13.0 邮件移动端渲染、滚动与联系人闪烁修复

本版重点修复 Grab 回执大段空白、品牌字体颜色丢失、取消订阅邮件横向超出屏幕、Apple 邮件无法继续滚动等问题。HTML 邮件不再强制清除原始颜色；固定宽度、异常高度与超大字号会按手机屏幕归一化。发件人区域进入正常文档流，远程图片改为仅在需要时出现的悬浮图片图标。联系人缓存随启动快照预读取，切换联系人页面不再闪出“暂无联系人”；首页“正在同步邮件”完成垂直居中。

完整变更见 `docs/CHANGELOG_v0.2.13.0.md`。

## v0.2.12.1 编译热修复

修复 `DetailScreen.kt` 的两个 Kotlin 编译错误：移除不兼容的 `calculateTopPadding` 顶层导入，并将 `WebResourceError.description` 从 `CharSequence?` 转成 `String?` 后再调用 `orEmpty()`。

## v0.2.12.0 冷启动、下拉刷新、HTML 邮件与 120Hz 专项修复

本版根据 BondMail/K-9 真机视频与日志继续修复：移除启动阶段的主线程 WebView 预热并使用 Android SplashScreen 保持到缓存首页首帧；修复刷新中列表与进度区重叠；修复 Bybit/Grab/GitHub 邮件的正文截断、桌面宽度、HTML 附件误判、相对图片地址和滑动后永久加载；详情底部操作区改为覆盖正文的半透明 Dock。同步端补充无 `UIDNEXT` 服务商的 UID 增量路径，避免 163 每次刷新重新拉取最近 30 封邮件头。

完整变更见 `docs/CHANGELOG_v0.2.12.0.md`。调试日志使用 Debug 包；公平比较 120Hz 帧耗时时使用 `installPerformance`。

## v0.2.10 加载、弹性刷新与导航动效

- 邮件正文和 HTML 首帧统一使用横向条形加载动画，不再显示圆形加载器。
- 首页下拉改为自定义弹性位移：拖动时列表带阻力下移并露出空白，松手后回弹；达到阈值时保留顶部条形同步区，结束后平滑收回。
- “暂无邮件”增加启动缓冲窗口，Room 缓存到达前不再短暂闪现空状态。
- App 启动后预热 WebView 引擎，降低第一次打开已缓存 HTML 邮件时的停顿。
- 页面进入使用右侧覆盖式滑入，上一页只做轻微视差；返回与预测性返回使用相反方向，接近 Telegram 的页面切换节奏。
- 继续保留长按选择、抽屉返回优先级和 Room 正文缓存。

版本：`0.2.10`


## v0.2.9 交互、缓存与诊断修复

- 长按选择与底部导航点击反馈统一使用圆角/圆形边界，移除方形阴影。
- 底部导航选中背景会在三个按钮之间平滑滑动。
- 选择模式按系统返回键先退出选择；抽屉打开时返回键先关闭抽屉。
- 已缓存正文不再因为解析器版本变化自动重新连接服务器。
- 邮件正文和 HTML 首帧加载统一为同一种小型加载动画。
- 下拉时只显示 Material 拖动圆圈；松手后圆圈收回，顶部条形同步区平滑展开并推动列表下移，结束后平滑收回。
- 首页启动时优先同步读取 Room 中的缓存快照，不再先闪出“暂无邮件”。
- 新增 IMAP/SMTP/同步诊断日志，详见 `docs/LOGCAT_GUIDE.md`。
- IMAP 移除无效的短生命周期连接池配置，并记录网络类型、TLS 尝试与真实异常原因，便于继续定位 163/iCloud 偶发连接失败。

版本：`0.2.9`


## v0.2.8 连接、邮件正文与刷新修复

- 163/126 改为 AUTH LOGIN，并加入网络验证等待、TLS 回退和三次连接重试。
- iCloud 与其他邮箱保留用户输入的登录大小写，减少登录地址不一致。
- 同一邮箱的同步、正文加载、已读和星标操作串行执行，避免同时建立多个 IMAP 会话。
- 邮件正文加载失败可直接重试；已打开正文继续使用 Room 缓存。
- 详情页显示账户中保存的邮箱大小写，不再使用服务器返回的小写收件人。
- 加强 MIME 编码词解析，修复部分 `=?UTF-8?...?=` 文本。
- HTML 邮件增加移动端宽度归一化和 WebView overview 模式。
- 下拉刷新恢复拖动反馈，触发后继续显示顶部条形同步进度。
- 底部左侧三项导航扩展到可用宽度，写邮件按钮尺寸保持不变。

版本：`0.2.8`


## v0.2.7 同步稳定性修复

- 邮箱地址继续按用户输入的大小写显示，但 IMAP/SMTP 登录统一使用小写地址，避免 163/126 等服务商出现“添加成功、后续同步失败”。
- 手动刷新只同步当前选择的邮箱；“全部账户”时才同步全部邮箱。一个失效账户不会阻塞新邮箱。
- 新邮箱保存后自动切换到该邮箱，并在连接会话关闭后自动拉取最近邮件。
- 同一邮箱同步串行，不同邮箱互不阻塞。
- 下拉刷新改为顶部条形进度与“正在同步邮件”提示。
- 左滑切换已读/未读，右滑删除。
- 修复设置页遗留错误显示为“无法访问 %s”的问题。

版本：`0.2.7`

BondMail 是使用 Kotlin、Jetpack Compose、Material 3、Room、DataStore 与 WorkManager 编写的 Android 原生多账户邮箱。

## v0.2.5 界面与动效专项修复

- 首页、联系人、设置页统一避让状态栏，修复标题与系统时间重叠。
- 首页邮箱分类移动到邮件列表顶部，向下滚动后会随列表自然离开，不再固定占据顶部。
- 分类按钮改为图标居中；选中时图标平滑左移并展开文字，取消突兀的 Crossfade。
- 搜索改为从屏幕顶部向下展开的独立搜索面板，遮罩、关闭和结果列表均使用纵向动画。
- 添加邮箱的用户名与邮箱后缀改为完全同高、同圆角、同边框的双块布局；左宽右窄。
- 多域名列表改为从域名控件下方纵向展开，不再使用突然缩放出现的 Popup 菜单。
- 邮件滑动背景同时预置删除和已读/未读图标，卡片滑开时自然露出，避免图标中途突然切换。
- 新页面使用轻量缩放淡入；返回继续采用 Android 官方 Navigation Compose predictive-back 缩小当前页面的方式。
- 深色模式 HTML 邮件启用 WebView algorithmic darkening，并清理邮件中硬编码的黑色文字，改善夜间可读性。
- WebView 开启离屏预栅格并在首帧可见后淡入，减少正文加载时的闪烁。
- 底部悬浮 Dock 增加导航栏安全区避让。

## v0.2.4 本轮修改

- 修复邮件卡片点击事件被内部 Card 吞掉、无法进入详情的问题。
- 邮件从右向左滑动会根据当前状态在“标记未读 / 标记已读”之间切换，并显示对应图标。
- 深色模式下同步切换状态栏与导航栏图标明暗，顶部时间和系统图标保持清晰。
- `NavHost` 按 Android 官方示例使用 `popExitTransition + scaleOut(0.9f)` 和 `popEnterTransition = EnterTransition.None`，手势未松开时显示上一级页面。
- 写邮件 ViewModel 提前创建并保持账户状态，减少进入写邮件页面的首帧闪烁。
- IMAP 与 SMTP 增加一次兼容模式重试；添加账户改为顺序验证，降低移动网络下偶发连接超时。
- 添加邮箱页改为左侧用户名宽、右侧域名窄的同高双块布局；只有多个后缀时才显示下拉箭头。
- 语言设置改成竖向单选列表。
- 抽屉宽度改为屏幕 80%；邮箱列表默认显示前三个，超过三个时可展开或收起。
- 设置页新增“编辑邮箱”，进入独立邮箱排序页面；排序结果应用于抽屉、设置、同步和写邮件账户列表。
- 底部导航改为悬浮小型 Dock，三个导航按钮与写邮件按钮尺寸统一，页面内容可显示在其下方。
- 数据库升级到版本 4，增加邮箱排序字段并提供 3 → 4 无损迁移。
- 三份 JSON 多语言文件统一为 133 个 key。

## v0.2.2 本轮修改

### 首页、联系人和底部导航

- 邮件重新改成 Gmail 类似的独立圆角邮件块，每封邮件之间保留小间距。
- 联系人也使用独立圆角块，邮件与联系人页面使用相同的视觉语言。
- 邮件块保留发件人头像、账户角标、发件人、主题、摘要、时间、未读状态和星标。
- 写邮件悬浮按钮只保留较大的写信图标，不再显示“写邮件”文字。
- 底部导航保持独立背景和顶部分隔线，与邮件内容区明确区分。
- 完全移除 `HorizontalPager` 和页面左右滑动切换。
- 点击底部导航时立即切换页面，只执行很轻的 110ms 透明度收尾动画，避免整页横向动画与邮件列表抢占绘制资源。
- 邮件、联系人和设置的页面状态使用 `SaveableStateHolder` 保留，切回来不会重新创建整个页面状态。

### Gmail 风格侧边抽屉

- 首页左上角改为菜单按钮。
- 点击后打开覆盖底部导航的 Material 3 抽屉。
- 抽屉显示全部收件箱、已绑定邮箱、添加邮箱、收件箱、星标、已发送、草稿、垃圾邮件、回收站和设置入口。
- 选择邮箱后可以只查看对应账户的收件箱。

### HTML 邮件和图片

- 重写 MIME 正文解析逻辑，正确区分 `text/plain`、`text/html`、`multipart/alternative` 和 `multipart/related`。
- HTML 邮件继续使用 WebView 渲染，不再把 HTML 降级成一整段纯文本。
- 基础支持 `cid:` 内嵌图片，单张内嵌图片上限为 3MB。
- 允许图片时支持 HTTPS 和邮件中仍在使用的 HTTP 图片。
- 移除邮件原站点遗留、会阻止本地 WebView 加载图片的 CSP 元标签。
- 远程图片仍受“始终加载 / 仅 Wi-Fi / 从不加载 / 单封允许”策略控制。
- HTML 正文保存在 Room；再次打开时直接渲染本地 HTML，不再重新连接 IMAP 下载。
- 已缓存 HTML 再次打开时不显示 WebView 加载进度条，减少“已经看过还在等待”的感觉。

### 邮件正文缓存迁移

- 数据库升级到版本 3，提供 2 → 3 无损迁移。
- 增加 `bodyParserVersion`，用于识别旧版本错误解析的正文。
- v0.2.1 已经缓存成纯文本的 HTML 邮件，在升级后第一次重新打开时会重新下载并按新解析器保存一次。
- 新解析器保存成功后，后续再次打开只读取 Room 本地缓存。

### 首次同步与刷新

- 新账户保存后由首页 ViewModel 立即执行前台同步，不再额外排队一个重复的 WorkManager 首次同步。
- 首次同步只读取最近 40 封邮件的 Envelope、UID 和 Flags，不下载全部正文。
- 邮件头逐封写入 Room，列表会逐封出现。
- 修复旧版本已经推进 `UIDNEXT`、但本地没有邮件记录时一直显示“暂无邮件”的问题。
- 同步前会检查本地最新窗口是否完整；缓存为空或不完整时自动重建最近 40 封。
- 后续刷新根据本地最大 UID 获取新邮件，单次最多处理 80 封。
- 最近 50 封只轻量更新已读和星标状态。
- 下拉刷新图标最多显示约 1.2 秒；网络同步可以继续在后台完成，新邮件会继续逐封进入列表。
- 如果所有账户都同步失败，首页会显示实际连接错误，不再静默显示空列表。

### 添加邮箱页面

- 邮箱域名后缀合并进用户名输入框，不再使用与输入框割裂的大号蓝色胶囊按钮。
- 域名菜单锚定在后缀控件下方，选中项使用勾选状态。
- 邮箱服务商列表与账户输入页继续使用独立页面和统一的 Material 3 过渡动画。

### 页面动画和返回

- 所有导航页面统一使用轻量的右侧进入、右侧退出和淡入淡出动画。
- 邮件详情、添加邮箱、写邮件等页面不再生硬瞬间出现。
- Android 预测性返回保持开启；支持的系统上，手势未松开时可以看到上一级页面。
- 底部三个主页面不参与横向滑动手势，避免与邮件左右滑动操作冲突。

### 多语言与脚本

- 业务文本继续统一保存在 JSON。
- `zh.json`、`zh-CHT.json`、`en.json` 当前均为 133 个相同 key。
- 项目中没有自定义 BAT，只保留 Gradle 官方 Windows Wrapper：`gradlew.bat`。

## 环境要求

- Android Studio
- JDK 17
- Android SDK 36
- Windows PowerShell
- 已开启 USB 调试的 Android 手机

建议在 Android Studio 的 Gradle JDK 设置中选择 Android Studio 自带的 JDK 17。

## 编译并直接安装到手机

```powershell
cd D:\Code\ProJect\BondMail_Kotlin
.\gradlew.bat clean installDebug
```

后续普通更新不需要每次清理：

```powershell
.\gradlew.bat installDebug
```

只生成 APK：

```powershell
.\gradlew.bat assembleDebug
```

APK 位置：

```text
app\build\outputs\apk\debug\app-debug.apk
```

查看 ADB 设备：

```powershell
adb devices
```

手动覆盖安装：

```powershell
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
```

## 同步与缓存逻辑

### 第一次绑定邮箱

1. 添加页依次验证 IMAP 与 SMTP，并在临时网络错误时自动重试一次。
2. 保存账户并立即返回首页。
3. 首页直接发起前台邮件头同步。
4. 只读取 INBOX 最近 40 封邮件的发件人、标题、日期、UID 和状态。
5. 邮件头逐封写入 Room，因此列表逐封出现。
6. 邮件正文在用户第一次打开对应邮件时下载。

### 后续刷新

- 以本地最大 UID 为实际增量位置，避免旧 Folder 游标与本地数据不一致。
- 如果本地最近窗口为空或不完整，自动重新获取最近 40 封邮件头。
- 正常情况下只获取新增 UID。
- 仅轻量刷新最近 50 封邮件的已读和星标状态。
- 不重复下载已经用当前解析器保存的正文。

### 正文缓存

- 纯文本和 HTML 正文下载后保存到 Room。
- HTML 预处理结果保存在内存 LRU 缓存。
- 再次进入邮件时只重新渲染本地 HTML，不重新连接邮箱服务器。
- 内嵌 `cid:` 图片会转换为本地 Data URI 后随 HTML 一起缓存。
- App 进程重启后内存 HTML 文档缓存会消失，但 Room 正文仍然存在。

## JSON 多语言规范

Compose 业务页面不得继续新增 `stringResource(R.string.xxx)`。界面文案统一放在：

```text
app/src/main/assets/i18n/en.json
app/src/main/assets/i18n/zh.json
app/src/main/assets/i18n/zh-CHT.json
```

代码中使用：

```kotlin
Text(tr("add_mailbox"))
```

新增语言步骤：

1. 复制 `en.json`，例如创建 `ja.json`。
2. 保持所有 key 不变，只翻译 value。
3. 在 `ui/i18n/JsonI18n.kt` 的 `SupportedLanguages.options` 增加一项。
4. 设置页面会自动出现新语言选项。

Android Manifest、通知频道名称等必须由 Android 系统直接读取的少量文字，仍保留在 `res/values*/strings.xml`；业务界面文字全部使用 JSON。

## 当前邮箱支持

- QQ Mail：客户端授权码
- 163 Mail：客户端授权码，保留网易 IMAP Client ID
- 126 Mail：客户端授权码，保留网易 IMAP Client ID
- iCloud Mail：App 专用密码
- Yahoo Mail：App Password
- Gmail：Google Identity Services OAuth 2.0 + IMAP/SMTP XOAUTH2
- Outlook / Hotmail / Live：Microsoft MSAL + IMAP/SMTP XOAUTH2
- Microsoft 365：代码支持 MSAL/XOAUTH2，当前服务商选择入口隐藏

## 当前限制

- Gmail 与 Microsoft OAuth 代码已经接入，但服务商同意页、撤销授权、不同设备/签名和真实 XOAUTH2 仍需联网真机回归。
- 附件发送已实现基础版；下载、预览和复杂附件类型仍未完整实现。
- `cid:` 内嵌图片已完成基础支持，但超大图片、复杂嵌套邮件仍需要更多样本验证。
- 发送后同步服务器 Sent 文件夹尚未实现。
- Sent、Drafts、Spam、Trash 抽屉入口已建立，但完整服务器文件夹识别和同步尚未完成。
- 当前仅同步 INBOX 最近窗口，尚未加入滑动到底部继续加载更旧邮件。
- Room Schema 导出当前关闭，进入稳定数据库迁移阶段后再恢复。
- 当前交付环境没有完整 Android SDK，因此最终 Android 类型解析和 APK 构建仍以本机 Gradle 输出为准。

更多说明：

- `docs/IMPLEMENTATION_STATUS.md`
- `docs/TEST_CHECKLIST.md`
- `docs/README_OAUTH.md`
- `docs/FLUTTER_HANDOFF_REQUIREMENTS.md`

## v0.2.6 UI transition update

- All secondary routes now enter from right to left and return from left to right.
- Navigation Compose transitions remain gesture-driven during Android predictive back.
- Unread cards use an opaque Material surface so swipe actions never bleed through the card.
- Swipe actions now reveal only the active side; left swipe deletes, right swipe toggles read/unread.
- Network and sync failures use a top Material 3 notice instead of a bottom snackbar hidden by the floating dock.
- Settings choices use compact vertical option lists; their row height follows the selected list density.
- “Message list density” is renamed to “List density”.
- Mailbox username/domain blocks use the same height and rounded shape, with a wider username block.
- Dark HTML mail sanitization now also removes hard-coded text colors from embedded stylesheets.
