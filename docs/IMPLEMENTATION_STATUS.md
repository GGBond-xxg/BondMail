# BondMail v0.2.38.0 实现状态

更新时间：2026-07-27
技术栈：Kotlin、Jetpack Compose、Material 3、Room、DataStore、WorkManager、Android JavaMail、Google Identity Services、Microsoft MSAL
当前数据库版本：Room v8

> 本文记录当前源码状态。历史变更请查看 `docs/CHANGELOG_v*.md`。

## 已完成

| 模块 | 当前状态 | 实现说明 |
|---|---|---|
| 原生 Android 工程 | 已完成 | 单 `app` 模块，JDK 17，`compileSdk/targetSdk 36` |
| Material 3 主题 | 已完成 | 浅色、深色、动态取色、统一 Motion Token 与 `BondMailSurfacePalette`；启动器、圆形/方形 Adaptive Icon、Android 13+ monochrome 主题图标和启动页直接使用用户提供的彩色/透明原图 |
| 多账户 | 已完成 | 统一收件箱、单账户筛选、账户排序、重复邮箱拦截、不同账户并发同步 |
| 添加邮箱 | 已完成基础版 | 单一邮箱输入框；授权码/密码字段请求安全输入类型并支持 IME 完成收键盘；Gmail/Microsoft 走服务商安全登录 |
| 邮箱账户编辑 | 已完成 | 显示名称统一最多 12 字符；授权码/App Password 双协议验证后替换；OAuth 原地重新授权 |
| 首页邮箱抽屉 | 已完成 | 前三账户、带高度/位移/透明度动画的展开收回、同一箭头旋转、编辑、删除确认、拖动排序、文件夹切换 |
| 邮件列表 | 已完成 | Room 轻量投影、缓存快照、未读投影入口、右侧时间/星标、Gmail 首尾圆角与选中形变；打开即本地已读；列表与详情共享单一 BODY 打开事务，正文成功后去重提交 IMAP `\Seen`；FLAGS generation 防止旧同步覆盖新状态 |
| 邮件详情 | 已完成基础版 | 顶栏显示所属账户；主题按真实手机宽度在 `22sp / 20sp / 18sp` 三档自适应并最多三行；Compose 实测主题/发件人高度写入 HTML 固定占位；主题、头像、发件人与 HTML 共用约 60dp 阻尼顶部 Q 弹；头像/发件人区域可直接拖动并惯性滚动 WebView；HTML 等待最终视觉提交后由 WebView 自身执行 260ms/150ms 渐显，精确缓存重开直接显示；未加载正文使用全高卡、7 行骨架和 40dp 圆形进度；明确短邮件先阻尼弹簧收回再渐显，长/不确定邮件保持展开；最近 16 封完整 MIME 快照与两槽 WebView LRU 支持同封重开及 A→B→A；低阴影、失败重试、回复/转发/分享/删除保持 |
| HTML 移动端适配 | 已完成基础版 | `layout-v33`：PreparedDocument 带保守 SHORT/LONG 高度提示；普通邮件 FLUID；Binance 真响应式模板用 FLUID、固定旧模板整体缩放；Grab 即使带伪响应式规则也保留检测宽度/600px 完整画布并使用 106% 文字缩放；Facebook/Meta 独立 132% 文字缩放并仅放大可信品牌 Logo；桌面 Newsletter 默认 118%；社交/支付图标横排、算法深色化、小图标/Emoji、渲染进程恢复与失败重试保持 |
| 下拉刷新/滚动 Chrome | 已完成 | 跟手阻尼、单一进度线；首页、联系人、设置与详情滚动收起/恢复；首页与联系人置顶使用“局部加速 → 虚拟化跳过长距离 → 强调减速收尾”的动量曲线 |
| 写邮件与附件 | 已完成基础版 | 72% Bottom Sheet、全屏安全区、SAF 多选、Outbox、`multipart/mixed`；有内容返回时保存/舍弃草稿；本地草稿通过 WorkManager 同步到 IMAP Drafts |
| IMAP 同步 | 已完成基础版 | UID 增量、Store 复用、批量 UID/Envelope FETCH、Header/Body 分离、历史通知基线、前后台恢复、渐进首屏、32 封窗口分批预热、可见视口预热、点击优先、同一邮件打开 single-flight、交互/预取 Store 与单邮件通道双重隔离、pending `\Seen` token 防旧 FLAGS 回写、128 KiB 自动下载上限；Sent/Drafts 特殊文件夹发现、最近窗口对账、APPEND/删除；账户 FLAGS generation 拦截过期远端快照 |
| SMTP 发件 | 已完成基础版 | 授权码与 XOAUTH2 显式 Transport 连接；已发送列表显示 QUEUED/SENDING/SENT/FAILED；SMTP 接受与 IMAP Sent 归档分离，归档重试不会重复发信 |
| Gmail OAuth | 已接入 | Google Identity Services AuthorizationClient；短期令牌；Gmail IMAP/SMTP XOAUTH2；原地重新授权 |
| Microsoft OAuth | 已接入 | MSAL 多账户；Outlook/Hotmail/Live/M365 Scope；静默令牌；IMAP/SMTP XOAUTH2；原地重新授权 |
| OAuth 安全 | 已完成基础版 | 无 Client Secret；Access Token 不写入 Room/CredentialStore/日志；服务商 SDK 管理令牌缓存 |
| 授权码安全 | 已完成 | Android Keystore AES/GCM；新凭证双协议验证成功后再替换 |
| 后台同步/通知 | 已完成基础版 | WorkManager、UID 去重；应用内主动/前台刷新静默消费且不重复通知，后台仅在应用不可见时提醒；新 `new_mail_alerts_v3` HIGH 频道使用默认声音、振动和悬浮提示条件；首次历史邮件不批量通知；系统权限仅由用户主动请求 |
| 多语言 | 已完成基础版 | `zh`、`zh-CHT`、`en` Key 与格式占位符一致；通知权限与权限设置文案已同步 |
| Room 迁移 | 已完成 | 1→2、2→3、3→4、4→5、5→6、6→7、7→8；v7 新增发送状态、稳定 Message-ID 与远端草稿定位字段，v8 新增附件元数据 JSON，全部使用无损 `ALTER TABLE` |
| 性能构建 | 已配置 | 非调试、R8、资源压缩、Debug 签名；已忽略 Nimbus JOSE 未使用的可选 Tink/Bouncy Castle 算法引用 |

## 服务商登录状态

| 服务商 | 状态 | 说明 |
|---|---|---|
| QQ | 已接入 | 授权码 |
| 163 / 126 | 已接入 | 客户端授权码、网易 ID 兼容、支持原地更新授权码 |
| iCloud | 已接入 | App 专用密码、支持原地更新 |
| Yahoo | 已接入 | App Password、支持原地更新 |
| Gmail | 已接入待真机完成服务商回归 | Google Android OAuth + `https://mail.google.com/` + XOAUTH2 |
| Outlook / Hotmail / Live | 已接入待真机完成服务商回归 | MSAL public client + Outlook IMAP/SMTP delegated Scope + XOAUTH2 |
| Microsoft 365 | 代码支持、入口隐藏 | 与 Outlook 共用 MSAL/XOAUTH2；当前产品入口重点面向个人 Microsoft 邮箱 |

## 仍需扩展

- Gmail OAuth 同意屏幕公开发布验证、不同 Google 账户/撤销授权/签名配置的真机回归。
- Microsoft 个人账户、不同地区别名、撤销授权和 Release 签名的真机回归。
- IMAP IDLE 常驻推送与厂商后台限制下的接近实时收信。
- 更旧 Sent/Drafts 分页、更多服务商特殊文件夹别名、附件下载预览、富文本签名与定时自动保存草稿。
- 平板双栏/三栏、Baseline Profile 与 Macrobenchmark 自动性能回归。
- 不重建 Activity 的全局主题环形揭示层：主动切换以主题按钮为圆心，跟随系统使用右上/左下固定锚点，并保持 Compose 导航与邮件 WebView 实例。

## 构建与验证

```powershell
.\gradlew.bat --stop
.\gradlew.bat clean compileDebugKotlin assembleDebug assemblePerformance --no-daemon
```

当前交付环境没有 Android SDK，也无法下载完整 Gradle/Android/Maven 依赖，因此发行包执行 Kotlin PSI、核心源码桩类型检查、JSON/XML、文案引用、Room 迁移静态检查、补丁应用、ZIP 比对和 SHA256 校验。Compose 类型解析、Room KSP、R8、OAuth 服务商页面、真实 XOAUTH2 连接与 WebView 真机排版，必须以本机 Gradle 和联网设备结果为准。
