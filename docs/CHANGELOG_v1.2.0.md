# BondMail v1.2.0

## 后台收信

- 接入 Firebase Cloud Messaging 与 Cloudflare Worker 定时调度，在应用处于后台时按用户设置的周期唤醒邮件同步。
- 移除常驻前台邮件服务与常驻通知，保留 WorkManager 作为系统兜底任务。
- Cloudflare 仅保存安装标识、随机注册密钥、FCM Token 和同步周期，不保存邮箱密码或 OAuth 凭据。
- 新邮件通知支持桌面角标；进入应用后会清理已展示的邮件通知。

## 邮件与文件夹

- 完善垃圾邮件、垃圾箱和草稿箱的数据加载及对应滑动、恢复、继续编写和彻底删除操作。
- 点击未读邮件时立即切换为已读样式，不再等待 IMAP 状态提交完成。
- 优化部分复杂 HTML 邮件的深浅色适配、文字可读性、图片比例和尾部空白。

## 界面与品牌

- 修复浅色模式冷启动时状态栏图标颜色不正确的问题。
- 优化页面打开、返回和底部导航切换动画，返回后可立即滚动列表。
- 新增 Agoda、Bitget、Cathay、Coinbase、富途、IBKR、iFAST、Longbridge、LottieFiles、N26、OSL、TRAE、Wise 等品牌图标匹配。

## 版本与验证

- `versionCode = 120`
- `versionName = 1.2.0`
- Firebase、Cloudflare Worker、D1 和 Android 真机后台同步链路验证通过。
- Gmail 与 Outlook 在应用处于后台时均能由 FCM 唤醒并完成同步。
