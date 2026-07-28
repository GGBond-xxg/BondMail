# BondMail v0.2.13.0 变更说明

## 1. HTML 邮件颜色与布局

- 不再在深色模式下删除邮件原有的 `color`、`bgcolor` 和 `text-fill-color`。
- 保留 Grab、GitHub、Bybit、Apple 等邮件自己的品牌色、按钮色和信息层级。
- 深色模式改由 Android WebView 的 algorithmic darkening 处理，BondMail 只提供页面背景和默认文字颜色。
- 对大于手机宽度的固定 `width`、`min-width`、表格宽度和桌面模板容器进行移动端归一化。
- 对超过 34px 的正文标题字体和异常大的固定行高进行上限处理，避免邮件标题在手机上被放大到屏幕外。
- 清理 `height:100%`、`100vh`、大于 260px 的异常固定高度、`position:fixed` 和阻塞滚动的内联布局，修复 Grab 回执出现大段空白的问题。
- 保留邮件中 `body > table` 等直接子节点选择器，并为原来的首个正文节点兼容 `:first-child` 样式。

## 2. 邮件滚动与头部

- 发件人区域改为 WebView 正常文档流中的第一个区域，不再通过绝对定位覆盖正文。
- 修复 Apple 等邮件标题钻到发件人区域下面、正文看得到但无法继续滑动的问题。
- WebView 强制使用手机 viewport，初始缩放为 100%，关闭 overview 自动缩放。
- 开启 WebView 嵌套滚动并在触摸期间阻止 Compose 父级抢占手势。
- 强制文档根节点允许纵向滚动，限制横向溢出。

## 3. 远程图片按钮

- 删除正文内部带文字的“显示远程图片”胶囊按钮。
- 只有检测到远程图片且当前被阻止时，邮件页右上方才显示一个半透明图片图标。
- 点击后加载当前邮件的远程图片并记住该邮件的允许状态；不需要远程图片的邮件不显示按钮。

## 4. 联系人页面

- 启动阶段与邮箱列表一起预读取联系人快照。
- 切换到底部联系人导航时直接显示 Room 缓存，不再先闪出“暂无联系人”。
- 真正没有联系人时延迟 550ms 后再显示空状态，避免数据库首次发射期间误显示。

## 5. 首页同步提示

- “正在同步邮件”改为在 42dp 同步区域中真正垂直居中。
- 顶部线性进度条仍固定在同步区域顶部。

## 6. 版本

- `versionName`: `0.2.13.0`
- `versionCode`: `26`
- NetEase IMAP ID 版本同步更新为 `0.2.13.0`。

## 验证说明

已完成：

- 修改文件 Kotlin 语法扫描。
- `MailWebViewCache.kt` 使用本地类型桩执行 Kotlin 编译检查。
- Room 查询字段与现有 `ContactRow` 字段逐项核对。
- ZIP 完整性与 SHA256 清单检查。

当前环境无法连接 `services.gradle.org` 下载 Gradle 8.11.1，因此未在此环境实际执行 Android Gradle 编译。请在 Windows 项目目录运行：

```powershell
.\gradlew.bat clean installDebug
```
