# BondMail v0.2.38.0

- `versionCode`: `59`
- `versionName`: `0.2.38.0`
- Room: `v8`（无数据库结构变化）
- MIME parser: `v8`
- HTML prepared-document cache: `layout-v33`
- Upgrade baseline: `v0.2.37.0`

## 1. 启动图标改为用户提供的原始文件

上一版仍然使用了项目内重新绘制的信封资源，没有真正替换为确认过的两张图片。本版不再重绘，直接以用户提供的原图作为唯一来源：

- 彩色图标：`BondMailDefIcon(2).png`；
- Android 13+ 主题图标：`BondMailmonaIcon(1).png`；
- 原图按字节原样保存在 `drawable-nodpi`；
- mdpi、hdpi、xhdpi、xxhdpi、xxxhdpi 的旧版启动器图标由彩色原图等比例缩放生成；
- 方形、圆形、Adaptive Icon 和启动页统一指向新的 `ic_launcher_bondmail` 资源；
- Android 13+ Adaptive Icon 使用用户提供的透明 monochrome 图，而不是从彩色图标自动推导。

为绕过桌面启动器对旧资源名的缓存，本版同时更换了 Manifest 中的图标资源名。部分启动器仍可能缓存应用图标，真机测试时可先移除桌面快捷方式后重新添加；若仍不刷新，再卸载测试版后重装。

## 2. 主动刷新只更新列表，不再重复发送系统通知

用户在应用内下拉或点击刷新时，新邮件已经直接显示在当前列表，再弹一条系统通知属于重复反馈。本版把同步来源明确分为：

- `ALERT`：应用不在前台时的后台周期同步，可以发送新邮件通知；
- `CONSUME_SILENTLY`：下拉刷新、页面刷新、前台恢复刷新和手动 WorkManager 同步，只更新列表并消费通知 UID，不弹通知。

并发保护包括：

- AppContainer 记录应用是否处于前台；
- 即使后台任务在应用打开前已经开始，只要完成时应用处于前台，也会静默消费；
- `shouldNotify → show/consume → markNotified` 由同一互斥锁串行执行；
- 主动刷新与后台任务同时结束时，同一 UID 只会被处理一次；
- 关闭通知期间收到的邮件也会被消费，重新开启通知时不会把旧邮件一次性补弹出来。

## 3. 后台通知恢复声音、振动和悬浮提示条件

Android 8.0 以后，通知频道第一次创建后，其声音和重要级别由系统持久保存。仅修改旧频道代码无法修复已经被创建为静默的频道，因此本版使用新的频道 ID：

```text
new_mail_alerts_v3
```

新频道配置为：

- `IMPORTANCE_HIGH`；
- 系统默认通知铃声；
- 两段振动节奏；
- 灯光、角标和锁屏私密显示；
- 单封邮件不再作为无摘要的分组子通知，避免部分系统隐藏悬浮横幅；
- Android 13+ 仍严格检查 `POST_NOTIFICATIONS` 权限；
- 应用级通知总开关关闭时不尝试发出通知。

悬浮横幅最终仍受手机系统的频道设置、勿扰模式和厂商后台策略控制。安装本版后应在系统的“BondMail → 通知”中看到新的高优先级邮件频道。

## 4. 未加载邮件的正文占位改为全高加载卡

之前偶发出现的小型骨架卡只占正文顶部一段，下面留下大块空白，看起来像页面没有打开。本版加载阶段会：

- 主题、头像、发件人、邮箱和日期继续由稳定原生层显示；
- 正文圆角卡默认填满顶栏与底部操作栏之间的可读区域；
- 骨架行由 4 行扩展为最多 7 行；
- 正文区域中央使用 `40dp` 圆形加载动画，替代底部细进度线；
- 已知附件时提前显示接近最终尺寸的附件条；
- 占位卡保持 `0dp` 外投影和低透明边框，深色模式四角不会重新变黑。

## 5. 短邮件弹簧收回，长邮件保持展开后渐显

Prepared HTML 新增保守的内容高度提示：

```text
MailContentHeightHint.SHORT
MailContentHeightHint.LONG
```

判断会综合正文字符数、段落/标题/引用块、表格、有效图片/视频和附件数量。固定桌面画布邮件直接按长邮件处理；跟踪像素、透明占位图和 `1×1` 图片不参与高度判断。

最终过渡为：

- 长邮件：全高加载卡保持展开，完整 HTML 在视觉提交后直接渐显，页面自然向下滚动；
- 明确的短邮件：完整 HTML 已经在底层提交后，占位卡先以阻尼弹簧从全高收回到摘要/附件的近似高度，约 `130ms` 后再开始淡出；
- 不确定的模板宁可保持展开，不执行“先缩短又立刻长高”的错误动画；
- 精确复用已经提交的 WebView 时不显示占位卡，也不重播收回或渐显动画；
- 系统关闭动画时直接切换到最终内容。

HTML 缓存版本升级为 `layout-v33`，避免复用上一版缺少高度提示的 PreparedDocument。

## 6. 抽屉邮箱展开/收回增加连续动画

抽屉仍默认显示前三个邮箱，但额外账户不再瞬间出现或消失：

- 展开时容器从顶部向下增长，账户行轻微下移并渐显；
- 收回时先渐隐，再向顶部压缩；
- “添加邮箱”和后续文件夹入口跟随容器高度连续移动；
- 展开箭头使用同一枚图标旋转 `0° → 180°`，不再切换两枚图标；
- 动画使用项目统一的 Material 3 强调减速/加速曲线；
- 系统关闭动画时立即切换，不强制播放。

## 7. 既有邮件详情修复保持

v0.2.37.0 的以下行为继续保留：

- HTML 等待 `onPageFinished`、VisualStateCallback 和稳定绘制帧后由 WebView 自身渐显；
- 精确重复打开同一封邮件以及 `A → B → A` 优先复用已提交 WebView；
- 主题、头像、发件人与正文共用顶部 Q 弹位移；
- Grab 固定画布、Binance 响应式/固定模板和 Facebook/Meta Logo 专用规则；
- 置顶使用“先快后慢”的分段动量曲线；
- CircularRevealSwitch 主题动画仍未接入，避免本次邮件与通知修复引入 Activity 重建。

## 主要修改文件

- `app/src/main/java/com/bond/mail/AppContainer.kt`
- `app/src/main/java/com/bond/mail/background/MailNotificationManager.kt`
- `app/src/main/java/com/bond/mail/background/MailSyncWorker.kt`
- `app/src/main/java/com/bond/mail/ui/ViewModels.kt`
- `app/src/main/java/com/bond/mail/ui/MailApp.kt`
- `app/src/main/java/com/bond/mail/ui/components/MailWebViewCache.kt`
- `app/src/main/java/com/bond/mail/ui/screens/DetailScreen.kt`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/res/drawable-nodpi/bondmail_icon_color.png`
- `app/src/main/res/drawable-nodpi/bondmail_icon_monochrome.png`
- `app/src/main/res/drawable/bondmail_icon_color_layer.xml`
- `app/src/main/res/drawable/bondmail_icon_monochrome_layer.xml`
- `app/src/main/res/mipmap-*/ic_launcher_bondmail*.png`
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher_bondmail*.xml`
- `app/src/main/res/mipmap-anydpi-v33/ic_launcher_bondmail*.xml`
- `app/src/main/res/values/colors.xml`
- `app/src/main/res/values/styles.xml`
- `app/src/main/res/values-night/styles.xml`
- `app/src/main/java/com/bond/mail/data/mail/ImapClient.kt`
- `app/build.gradle.kts`

## 本机构建命令

```powershell
.\gradlew.bat --stop
.\gradlew.bat clean compileDebugKotlin assembleDebug assemblePerformance --no-daemon
```

## 真机重点回归

1. 卸载旧测试版后安装本版，确认桌面彩色图标和 Android 主题图标都是用户提供的版本。
2. 保持应用在前台，下拉刷新出一封新邮件：列表应更新，但系统不响铃、不弹横幅。
3. 退出应用后等待后台同步收到新邮件：应使用新频道播放声音、振动并满足悬浮通知条件。
4. 打开从未加载过的邮件：正文加载卡应默认填满可用区域，圆形进度动画明显但不遮挡头部。
5. 打开一两行纯文本或附件邮件：全高卡应先弹簧收回，再平滑显出最终内容。
6. 打开长 Newsletter：加载卡保持展开，HTML 完整渐显后可自然向下滚动，不先错误缩短。
7. 抽屉超过三个邮箱时反复展开和收回，额外账户、“添加邮箱”和文件夹入口应连续移动，无直线瞬切。
