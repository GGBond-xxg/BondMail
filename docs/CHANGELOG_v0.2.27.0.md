# BondMail v0.2.27.0

## 真机回归结论

本版根据 v0.2.26.0 的 Debug 全流程录屏、`gfxinfo`、`meminfo` 和首次使用反馈继续修复。Cloudflare 桌面 Newsletter、GitHub 短邮件、通知权限流程、授权码更新与前后台刷新已经通过用户真机回归，因此本轮不重写这些已正常链路，只处理首次同步等待、近期正文预取、网易窄卡片、WebView 渲染进程恢复和通知提示细节。

## 首次同步提速

- 修复首次同步按 UID 排序前逐封调用 `folder.getUID()` 的问题。部分网易服务器会因此为最近约 40 封邮件额外发送逐条 UID 查询，导致保存账户后长时间停留在空列表。
- 现在先对整个窗口执行一次包含 `UID / ENVELOPE / FLAGS / Message-ID` 的批量 FETCH，完成后再在内存中按 UID 排序。
- 新增 `header window ... fetch=...ms` 诊断日志，方便区分服务器建立连接、批量邮件头 FETCH 与 Room 渐进写入耗时。
- 原有 K-9 式展示保留：最新 8 封逐条写入，后续按 4 封一组进入列表；网络请求仍保持批量，不改成逐封往返。

## 正文预取与打开速度

- 首次同步的小正文预取窗口由最近 12 封提高到最近 24 封。
- 预取改为每批 8 封，先让第一屏尽快完成，再继续预热后续邮件；避免一个 24 封的大 BODY 请求让最前面的邮件等待整个批次。
- 每批正文仍逐封更新 Room，列表摘要不会单帧整批替换。
- 继续保持 128 KiB 自动下载上限；超出上限的 Newsletter 按需获取，避免大邮件阻塞整个首屏。
- 用户主动打开邮件时仍会取消同账户低优先级预取任务，并优先完成当前邮件。

## 网易手机邮件与 HTML layout-v21

- HTML 准备缓存升级为 `layout-v21`，旧排版自动失效。
- 对 `163.com`、`126.com`、`yeah.net` 与 `*.netease.com` 的事务邮件明确优先使用 FLUID 手机布局，避免 600px Outlook 兼容外框把内部 300/360px 手机卡片整体缩成中央小图。
- 紧凑主卡片检测范围由 220px 起，并同时扫描 HTML 属性、内联样式与命中实际正文节点的 CSS class/id 规则。
- 网易模板没有可解析宽度时，会根据正文与图片覆盖率寻找最主要的深层内容表格，再扩展到手机可用宽度；Cloudflare 等非网易桌面 Newsletter 不走此强制路径，保持已经验证正常的整体缩放。
- 新增布局诊断日志：FLUID/DESKTOP、紧凑宽度、桌面画布宽度、评分、发件域名与实际扩展容器。
- 对邮件中缺少 Emoji variation selector 的图片/盾牌符号补充彩色 Emoji 呈现提示，作为 Bybit 等小图标在 Android 字体缺字时的兼容回退；真实远程图片与 CID 图片处理保持不变。

## WebView 稳定性

- 增加 `onRenderProcessGone()` 处理。Chromium 渲染进程因系统回收或崩溃退出时，死亡 WebView 不再放回复用池。
- 第一次渲染进程退出会自动创建新 WebView 并重试当前邮件；连续失败才显示现有的“邮件内容加载失败 / 重新加载”。
- 复用池新增显式 `discard()`，确保活动计数、父 View、触摸监听与 Chromium 资源正确清理。
- 保持单实例预热/复用策略，不额外常驻第二个 WebView。

## 通知权限动效与设置状态

- 通知提示卡移除与 `AnimatedVisibility` 重复叠加的 `animateItem()`，只保留一套高度/透明度动画；下面欢迎卡和邮件行继续使用位置动画补位，改善“拒绝通知”时比“允许通知”更顿的感觉。
- “拒绝通知”改为低强调 TextButton，“允许通知”保留主按钮层级。
- 设置页已允许状态改用 `onSurfaceVariant` 中性色，不再用主色表现成可点击动作；未允许时的“去授权”仍为可点击按钮。

## 保留并确认的实现

- Performance R8 对 Nimbus JOSE 可选 Tink/Bouncy Castle 引用的处理。
- Header / Body 分离、正文骨架渐显和失败重试。
- 163/126 授权码 IMAP + SMTP 双协议验证后替换。
- FLUID / DESKTOP_SCALED 双模式与 Cloudflare 完整画布缩放。
- Gmail/Microsoft OAuth 与 IMAP/SMTP XOAUTH2。
- 首次历史邮件通知基线、前后台同步 generation 隔离和通知权限用户主动请求。

## 版本

```text
versionCode = 43
versionName = 0.2.27.0
Room 数据库版本 = 6
HTML 文档缓存 = layout-v21
```
