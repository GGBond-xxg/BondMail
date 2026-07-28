# BondMail v0.2.16.0 更新说明

## 1. Material 3 导航动画

- 邮件列表 → 详情改为 **Forward**：目标页从尾部小距离进入，来源页轻微后退并淡出。
- 详情 → 邮件列表改为严格反向的 **Backward**。
- 删除邮件卡片与详情页之间的 `sharedBounds`；WebView 不参与任何尺寸变形，避免共享元素、导航转场和 Chromium 首帧同时绘制造成闪白。
- 主写信 FAB 仍保留 Container Transform；详情回复/转发仍使用 Shared Z。
- 主 Tab 继续使用 Fade Through；添加邮箱步骤继续使用 Shared X。

## 2. 根据滚动手势隐藏/显示组件

- 首页向下浏览旧邮件时：顶部 App Bar 从上方退出，底部 Dock 从下方退出。
- 向上返回或回到列表顶部时：对应组件从原来的屏幕边缘恢复。
- 邮件详情向下滚动正文时：顶部栏从上方退出，底部回复/转发/分享/删除 Dock 从下方退出；反向滚动恢复。
- 动画只处理 alpha/translation，不改变 LazyColumn 或 WebView 的测量尺寸。

## 3. 搜索 Container Transform

- 删除原有整块顶部 Sheet 式搜索。
- 搜索按钮作为视觉来源，搜索容器从右上入口尺寸扩展成大圆角搜索面板。
- 关闭时按相反方向收回；背景仅轻度遮罩，键盘从底部进入。
- 搜索结果仍使用本地列表，不修改邮件同步与查询逻辑。

## 4. 空间来源统一

- 顶部错误/状态通知从屏幕上方进入和退出。
- Modal Navigation Drawer 保持从左侧进入。
- 底部 Dock、详情操作区、键盘和其他底部组件从屏幕底部进入。
- 新规则已写入 `docs/README_MOTION_SPEC.md`，后续 AI/开发者必须以该文档为基线。

## 5. 后台收信与 1 分钟设置

- 修复旧实现：界面可选 1/5/10 分钟，但后台实际只注册 15 分钟周期任务的问题。
- 1/5/10 分钟改为自续接的一次性 WorkManager 链；15 分钟及以上继续使用标准 PeriodicWork。
- 调度模式、间隔和短周期 token 持久化；重复启动 App 不再取消并重建同一任务，避免每次打开都把下一次同步往后推。
- Worker 同步后统一执行新邮件去重和通知；前台静默同步也走相同通知路径。
- 网络失败使用指数退避并重试。

说明：Android 会在 Doze、省电模式及厂商后台策略下延迟普通后台任务。该方案修复了设置未生效和任务被反复重置的问题，但不是服务器推送或精确闹钟。Thunderbird 的接近实时推送依赖更完整的 IMAP IDLE/前台服务体系，本版没有用一个伪“1 分钟定时器”冒充精确推送。

## 6. 通知声音

- 新邮件使用新频道 `new_mail_v2`，重要性为 High。
- 设置默认通知音、振动、灯光、Badge、Email 类别与 High priority。
- 使用新频道 ID 是为了绕开 Android 对旧频道声音/重要性设置的持久化；旧版频道一旦被系统或用户静音，应用升级不能直接覆盖。
- Android 13 及以上仍需用户授予通知权限；系统频道页面仍可单独关闭声音。

## 7. HTML 邮件显示比例与图标

- HTML viewport 固定为 `device-width`，WebView 关闭 overview 自动缩小并使用 100% 文本缩放。
- 引用邮件、Gmail/Yahoo 引用区和嵌套表格限制到移动端宽度，避免整封转发内容被缩成窄小桌面页面。
- 图片和视频按屏幕宽度缩放；SVG 不强制 `height:auto`，保留邮件中的小图标高度。
- HTML 布局缓存版本升级为 `layout-v7`，旧的错误布局不会继续命中内存缓存。

## 8. 构建与测试

版本：`0.2.16.0`，`versionCode = 30`。

```powershell
.\gradlew.bat --stop
.\gradlew.bat clean installDebug --no-daemon
.\gradlew.bat --stop
.\gradlew.bat clean installPerformance --no-daemon
```

建议测试：

1. 从邮件列表连续打开/返回 10 次，确认无共享边界闪白。
2. 首页和详情页上下滚动，确认顶部/底部组件从正确方向退出和恢复。
3. 打开、关闭搜索，观察容器扩展与收回。
4. 设置 1 分钟同步，退到后台并锁屏；等待多轮后核对新邮件和声音。
5. 在系统设置中确认 BondMail 的“新邮件”频道允许声音。
6. 对比转发 Bitget 邮件与 Thunderbird 的正文比例和内联图标。
