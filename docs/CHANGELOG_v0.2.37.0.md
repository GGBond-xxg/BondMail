# BondMail v0.2.37.0

- `versionCode`: `58`
- `versionName`: `0.2.37.0`
- Room: `v8`（无数据库结构变化）
- MIME parser: `v8`
- HTML prepared-document cache: `layout-v32`
- Upgrade baseline: `v0.2.36.0`

## 1. 启动图标降低视觉重量

上一版图标使用大面积实心信封，开启 Android 主题图标后尤其显得比 Gmail、Thunderbird 等图标更粗、更靠前。本版重新整理前景几何：

- 信封改为留白更充分的圆角描边，不再使用大块实心图形；
- 前景缩进到 adaptive icon 安全区域，避免不同启动器裁切后显得过满；
- 全彩图标使用较柔和的蓝灰背景，降低与桌面壁纸的冲突；
- Android 13+ 增加独立 `monochrome` 前景，主题图标不再从彩色前景自动推导成厚重色块；
- 普通图标、圆形图标和启动页图标使用同一套信封比例。

系统启动器可能缓存旧图标。覆盖安装后若桌面仍显示旧图标，可移除桌面快捷方式后重新添加；应用数据无需清除。

## 2. 顶部 Q 弹改为整张邮件共同运动

邮件 HTML 位于 WebView，主题、头像和发件人则是 Compose 稳定层。旧版在正文顶部下拉时只有 Chromium 的内容产生弹性，原生发件人区域仍停在原处，所以看起来像两张彼此分离的页面。

本版关闭 WebView 私有 overscroll，并由详情页维护一份共享的顶部拉伸距离：

- HTML 正文、纯文本预览、主题、头像、发件人、邮箱和日期使用同一个垂直偏移；
- 下拉最大位移约 `60dp`，越接近极限阻力越大；
- 松手后使用有阻尼的弹簧回位，不是匀速移回；
- 从正文开始下拉与从主题/头像区域开始下拉共用同一状态；
- 手势使用屏幕绝对 `rawY` 计算，页面本身移动时不会反向放大或抖动；
- 一旦正文真正开始向上滚动，会结束顶部拉伸并恢复普通 WebView 滚动。

## 3. HTML 从 WebView 自身渐显，避免半页突然闪出

`AndroidView` 中的平台 WebView 可能先向 Surface 提交一帧，再应用 Compose 外层透明度。旧实现即使等待了 `VisualStateCallback`，部分设备仍可能出现“HTML 已经露出一半，随后整页突然闪出来”。

本版把揭示动画移到 WebView 自身：

- 新页面创建或重新栅格化时，WebView 先保持 `alpha=0` 并下移 `6dp`；
- `onPageFinished` 后继续等待 `VisualStateCallback`、两个绘制帧及必要的远程资源稳定窗口；
- 完成后直接对 WebView 执行透明度与位移动画，纯文本预览同步淡出；
- 首次打开使用 `260ms` 强调减速曲线；
- 已看过但对应 WebView 已被淘汰时使用 `150ms` 轻渐显；
- 精确复用已提交 WebView 时立即显示，不重播动画；
- `onPageCommitVisible` 只作为异常兜底，普通邮件延后至约 `2400ms`，含远程资源的邮件延后至约 `4200ms`，不再抢先显示半排版页面；
- WebView 回收到池中时会取消遗留动画并恢复透明度/位移，避免下一封邮件继承旧动画状态。

## 4. 未加载纯文本邮件卡片去除重阴影

加载阶段的纯文本卡片不再使用外投影：

- Compose 占位卡 `shadowElevation` 调整为 `0dp`；
- 边框透明度降低为 `0.20`，仍可辨认卡片边界；
- 最终 HTML 内容卡阴影降低到 `0 1px 2px rgba(0,0,0,.055)`；
- 原有短邮件自然高度和尺寸缓动保留。

这样浅色模式下四个圆角不会像黑色描边，也不会在 HTML 出现时发生明显阴影切换。

## 5. Grab 固定桌面模板恢复完整显示

Grab 部分事务邮件虽然包含手机媒体查询，但核心绿色头图和正文仍锁在约 `600px` 的表格中。把根表强制改为 FLUID 会使内部规则互相冲突，最终只显示左半边。

本版增加 Grab 专用识别：

- 支持正常 `@grab.com` 地址及 `no-reply_at_grab_com_...` 一类转义地址；
- Grab 模板即使声明响应式规则，也优先保留原始固定画布；
- 优先采用检测到的 `520–720px` 实际宽度，无法读取时回退到 `600px`；
- 完整画布按手机可用宽度一次缩放，不再重写内部列结构；
- 使用 `106%` 文字缩放补偿整体缩放后的正文密度；
- Binance 的“真响应式用 FLUID、旧固定模板整体缩放”规则继续独立生效。

## 6. Facebook/Meta 邮件文字和品牌 Logo 增强

Facebook 注册/安全邮件本身经常使用偏小字号和 16–24px 品牌图，即使 Apple Mail 也会按原始尺寸显示。本版只对可信 Facebook/Meta 发件人做局部增强：

- 识别 `facebookmail.com`、`facebook.com`、`meta.com` 及 Facebook/Meta 发件人名称；
- WebView 文字缩放提高到 `132%`，不影响其他发件人；
- 在邮件前部有限范围内查找带 Facebook/Meta 特征的品牌图片；
- 排除 `1×1`、`2×2` 跟踪像素；
- 横向字标调整为约 `128px`，方形品牌标调整为约 `52px`；
- 使用 `object-fit: contain`，不拉伸、不裁切；
- 未能可靠识别品牌图片时保持原始图片，不全局放大正文中的普通照片或图标。

## 7. 置顶滚动改为“先快后慢”的动量曲线

首页和联系人列表不再直接调用默认 `animateScrollToItem(0)`。新的 `animateToTopWithMomentum()` 会根据当前位置分段处理：

1. 先在当前可见区域用强调加速曲线建立向上速度；
2. 邮件很多时，在已经高速移动的阶段跳过完全不可见的长距离中段，避免持续测量数百行；
3. 最后几屏使用强调减速曲线平滑停在顶部；
4. 根据剩余屏数自动把收尾时长控制在约 `270–425ms`；
5. 末尾校正到精确 `index=0 / offset=0`；
6. 系统关闭动画时立即回到顶部。

## 8. 主题环形揭示仍暂缓

CircularRevealSwitch 仍未接入。本版继续优先保证邮件详情 WebView、头部手势与正文缓存稳定。后续主题动画应使用不重建 Activity 的页面快照覆盖层，避免切换主题时销毁正在查看的邮件页面。

## 主要修改文件

- `app/build.gradle.kts`
- `app/src/main/java/com/bond/mail/data/mail/ImapClient.kt`
- `app/src/main/java/com/bond/mail/ui/components/MailWebViewCache.kt`
- `app/src/main/java/com/bond/mail/ui/components/MailWebViewPool.kt`
- `app/src/main/java/com/bond/mail/ui/motion/BondMotion.kt`
- `app/src/main/java/com/bond/mail/ui/motion/ScrollMotion.kt`
- `app/src/main/java/com/bond/mail/ui/screens/DetailScreen.kt`
- `app/src/main/java/com/bond/mail/ui/screens/HomeScreen.kt`
- `app/src/main/java/com/bond/mail/ui/screens/ContactsScreen.kt`
- `app/src/main/res/drawable/ic_launcher_foreground.xml`
- `app/src/main/res/drawable/ic_launcher_monochrome.xml`
- `app/src/main/res/drawable/ic_app.xml`
- `app/src/main/res/drawable/ic_app_round.xml`
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`
- `app/src/main/res/values/colors.xml`

## 本机构建命令

```powershell
.\gradlew.bat --stop
.\gradlew.bat clean compileDebugKotlin assembleDebug assemblePerformance --no-daemon
```

## 真机重点回归

1. 覆盖安装后检查彩色图标和 Android 主题图标，确认信封不再比 Gmail 明显更粗、更满。
2. 在邮件顶部从正文、主题、头像和发件人分别向下拉，确认所有内容作为一张页面共同 Q 弹并平滑回位。
3. 对同一封未缓存邮件比较 K-9：应先保持可读预览，随后整张 HTML 连续渐显，不露出半页后突然闪现。
4. 打开纯文本短邮件，确认加载卡四角无重阴影，最终正文仍自然收拢。
5. 回归 Grab 取消订阅邮件，绿色头图、标题和正文应完整位于屏幕内，无右侧裁切。
6. 打开 Facebook 注册/安全邮件，检查正文可读性及 Logo 尺寸，同时确认普通邮件图片未被误放大。
7. 在几百封邮件处点击置顶，观察动画是否先快后慢，并最终准确停在第一封。
