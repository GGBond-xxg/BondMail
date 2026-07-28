# BondMail v0.2.12.0 变更说明

本版基于 `v0.2.11.0`，针对用户提供的开屏、下拉刷新、不同邮件对比视频，以及 BondMail/K-9 Logcat 和 `gfxinfo framestats` 进行专项修复。数据库结构没有变化，可直接覆盖安装并保留账户与邮件缓存。

## 1. 冷启动白屏

- 移除 `MainActivity` 启动阶段在主线程创建并销毁 WebView 的预热代码。
- 接入 Android SplashScreen，启动画面保持到 Room 本地缓存完成预取且 Compose 首页完成首次成功组合。
- 启动画面背景分别适配浅色和深色主题，避免系统启动窗口与 App 首帧之间出现白色空帧。
- WorkManager 周期任务注册移到后台调度，不再参与首页第一帧关键路径。
- 保留 2.5 秒安全兜底，异常情况下不会一直停留在启动画面。

## 2. 首页下拉刷新重叠

- 刷新进行中固定保留 42dp 顶部同步区，普通向上滚动不再把列表位移压回 0。
- 进度区出现与内容预留改为同一帧，不再从 0 动画到 42dp 造成短暂叠层。
- 刷新期间不吞掉正常列表 fling；结束后再平滑收回顶部预留。
- 邮件行刷新时移除逐行 `animateItem()`，避免 Room 状态更新与手势滚动同时触发大量位移动画。

## 3. Bybit、Grab、GitHub HTML 邮件

- 保留发件人原始 `body` 子节点，不再套入额外 `<main>`。这会恢复大量依赖 `body > table`、`:first-child` 等选择器的邮件模板。
- 开启 WebView `loadWithOverviewMode`、宽视口与文本自动排版，使 600/700px 桌面邮件模板缩放到手机宽度。
- 清除常见结构节点中大于等于 360px 的内联 `min-width`，同时保留原始配色、表格层级和品牌样式。
- 图片、视频和 SVG 限制在可视宽度内；正文增加左右 16px 和底部操作区安全距离。
- MIME 正文不再因为 `name=message.html` 就被错误识别为附件。
- `Content-Base` / `Content-Location` 会从父级 MIME 结构继承，并写入 HTML `<base>`，支持相对图片地址。
- 远程图片识别同时覆盖绝对 URL、协议相对 URL、`srcset`、CSS `url(...)` 和带远程 `<base>` 的相对路径。
- MIME 解析器版本升级到 7。旧缓存邮件升级后第一次打开会重新获取一次，之后继续使用 Room 缓存。

## 4. 滑动后出现加载并一直不消失

- 修复 Compose `pageVisible` 状态按正文 key 重建、但旧 WebViewClient 仍持有旧状态对象的问题。
- WebViewClient 与页面可见状态现在在 AndroidView 生命周期内保持稳定。
- 同一邮件切换远程图片策略时保留旧正文，直到新文档提交；切换不同邮件时才回到顶部并显示首次加载状态。
- 页面提交、主文档错误和远程图片 HTTP 错误增加 `BondMail-Web` 调试日志。
- 离开详情页时显式停止并销毁 WebView，降低反复进入邮件后的内存和渲染负担。

## 5. 半透明详情操作区

- 详情页不再使用占据布局高度的 `Scaffold.bottomBar`。
- 回复、转发、分享和独立红色删除按钮覆盖在正文上方，正文仍可从半透明背景下看到。
- HTML 底部保留 132px 可滚动安全距离，最后一行可以滚动到操作区上方。
- 首页 Dock 与详情 Dock 使用相同透明度和零 tonal elevation。

## 6. 同步速度与 163 重复拉取

- 当服务商不返回可靠 `UIDNEXT` 时，改用 `localMaxUid + 1 ... 最后一封远端 UID` 的有界增量查询。
- 不再因为 `UIDNEXT` 缺失而每次下拉都重新下载最近 30–40 封 Envelope。
- 初始/修复窗口已经获取 Flags 时不再重复执行第二次 Flags fetch。
- 最近状态刷新窗口从 50 封缩小到 24 封。
- 尝试启用 IMAP COMPRESS；服务端不支持时 JavaMail 会继续使用普通连接。

## 7. 120Hz 与帧耗时

- 继续请求当前分辨率支持的最高刷新模式。
- 邮件卡片普通状态取消 0.5dp 阴影，账户角标取消阴影，减少 120Hz 下 GPU 图层开销。
- 新增 `performance` 构建类型：非 Debug、关闭 R8、使用本机 Debug 签名，可覆盖安装且不清除数据，适合与商店版 K-9 公平比较帧耗时。

```powershell
.\gradlew.bat installPerformance
```

性能 APK 路径：

```text
app\build\outputs\apk\performance\app-performance.apk
```

Debug 包用于抓取 `BondMail-*` 日志：

```powershell
.\gradlew.bat installDebug
```

## 8. 新增日志

Debug 包可筛选：

```text
BondMail
BondMail-IMAP
BondMail-SMTP
BondMail-Perf
BondMail-Web
```

`mime parsed` 现在额外输出图片、表格、样式和 `<base>` 数量，用于判断服务器返回的是完整 HTML 还是纯文本替代内容。

## 真机回归重点

1. 强制停止后启动 App，系统启动画面结束后应直接出现缓存首页，不得先白一下。
2. 下拉触发同步后立刻向上滑动，进度区和邮件列表不得重叠。
3. 分别打开 Bybit、Grab、GitHub 测试邮件；旧缓存第一次会重抓，第二次应出现 `body cache hit`。
4. GitHub 邮件滚动、返回、再次打开，不得突然出现永久加载层。
5. 详情底部正文应能透过半透明 Dock 看见，删除按钮保持独立红色图标。
6. 在 163 无新邮件时连续刷新两次，第二次日志中的 `newHeaders` 应为 0，耗时不应再接近旧版的 18 秒。
7. 使用 `installPerformance` 后重新抓 `gfxinfo framestats`，不要拿 Debug Compose 包直接与 K-9 商店 Release 包做最终帧率结论。
