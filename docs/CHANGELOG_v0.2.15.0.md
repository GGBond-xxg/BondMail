# BondMail v0.2.15.0

## Material 3 Motion 重构

本版不再通过“删除过渡动画”换取流畅度，而是按照 Material 3 的页面关系选择动画模式，并把时长、缓动和弹簧参数集中到 `ui/motion`：

- 邮件卡片 → 邮件详情：`Container Transform`。卡片背景从原位置扩展为详情页壳层，返回时反向收回；WebView 正文不参加尺寸动画。
- 主界面写信按钮 → 写信页：`Container Transform`。只有背景容器参与共享边界，输入框在目标页正常显示。
- 详情页回复/转发 → 写信页：`Shared Z-Axis`，不会错误匹配主界面的写信按钮。
- 搜索：由顶部整块滑入改成 `Shared Z-Axis` 风格的淡入和轻微放大。
- 邮件、联系人、设置：使用 `Fade Through`，并继续通过 `SaveableStateHolder` 保留各页状态。
- 添加邮箱的服务商选择 → 凭据填写：使用小范围 `Shared X-Axis`，不再整屏大幅平移。
- 选择模式顶栏：使用 `Fade Through`。
- 邮件卡片、底部导航和写信按钮恢复统一按压反馈。
- 系统动画缩放为 `0x` 时关闭位移、缩放和装饰性动画。

新增文件：

```text
app/src/main/java/com/bond/mail/ui/motion/BondMotion.kt
app/src/main/java/com/bond/mail/ui/motion/BondTransitions.kt
app/src/main/java/com/bond/mail/ui/motion/BondSharedKeys.kt
app/src/main/java/com/bond/mail/ui/motion/PressMotion.kt
```

项目继续使用 Compose BOM `2025.06.01`（Compose Animation 1.8.3），因此共享边界的 ResizeMode 使用当前依赖可编译的 `ScaleToBounds()` API，没有为动画升级整套依赖。

## 下拉刷新

- 重写为明确的 `Idle / Pulling / Armed / Refreshing` 状态。
- 触发距离缩短到约 `80dp`，最大拉伸约 `128dp`。
- 拖动时直接跟手，越过阈值只触发一次触觉反馈。
- 释放后通过高阻尼弹簧回到正常位置，不再硬赋值产生跳变。
- 拖动和刷新使用同一个圆形指示器，不再切换成顶部矩形线性进度条。
- 刷新进行中列表恢复正常位置，仍可继续滚动。

## 重新打开后滑动 90Hz → 30Hz

新增 `UiPerformanceGate`，不降低刷新率，也不关闭同步，只把可以延后的后台任务避开用户正在滚动的时间窗口：

- 邮件列表滚动时，后台 MIME 正文预取等待列表停止。
- WorkManager 周期同步在列表停止后再进入 IMAP 阶段。
- App 冷启动或重新回到前台后增加 5 秒首轮交互保护窗口；等待期间如果用户开始滑动，会重新等待列表静止，避免任务刚好在第一次 fling 下方启动。
- 前台静默同步同样避开正在滚动的窗口，并且不再把首页切换成手动刷新状态。
- 周期任务注册从 `UPDATE` 改为 `KEEP`，避免每次进程启动重复更新同一周期任务。
- 启动正文预取延后到 20 秒；同步后的预取延后到 15 秒。

手动下拉刷新和用户主动打开邮件始终立即执行，不受性能门控影响。

## 邮件图标和图片

- HTML 文档恢复可靠的 `baseUrl`：优先使用 `<base href>`，否则从绝对资源地址推导目录，改善相对路径图标和图片。
- 保留 MIME 解析器写入的 `Content-Base / Content-Location`。
- 不再对内联 SVG 图标强制 `height:auto`。部分邮件 SVG 没有 `viewBox`，原规则会让小图标在 WebView 中塌缩为 0 高度。
- WebView HTML 缓存版本升级到 `layout-v6`，旧的错误布局结果不会继续复用。

## 动画规范文档

用户提供的完整规范已加入，并将实现基线及 v0.2.15.0 落地状态写入文档顶部：

```text
docs/README_MOTION_SPEC.md
```

后续 AI 或开发者修改页面、刷新、导航和加载动画时，应先阅读该文档，并且不得重新退回“所有页面统一横向滑动”或“直接删除动画掩盖卡顿”的实现。

## 编译与验证

建议依次执行：

```powershell
.\gradlew.bat clean compileDebugKotlin
.\gradlew.bat assembleDebug
.\gradlew.bat assemblePerformance
```

功能和日志使用 Debug 包；最终滚动与动画流畅度使用 Performance 包。当前生成环境没有 Android SDK，且无法连接 Gradle 分发服务器，因此这里仅完成源码和静态检查，未实际生成 APK。
