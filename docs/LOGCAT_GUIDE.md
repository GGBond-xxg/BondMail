# BondMail 调试日志获取（Windows PowerShell）

BondMail 的 Debug 版本会输出以下 Logcat 标签：

- `BondMail`：同步任务、缓存命中、正文获取
- `BondMail-IMAP`：IMAP 连接、同步、正文下载
- `BondMail-SMTP`：SMTP 测试和发送连接
- `BondMail-Perf`：启动缓存、正文分阶段耗时与刷新率请求
- `BondMail-Web`：HTML 文档提交、远程图片 HTTP 错误与主文档加载错误
- `BondMail-OAuth`：Google/Microsoft 登录、静默令牌与重新授权阶段（不输出 Token）

日志不会记录客户端授权码、App 专用密码或完整邮件正文；邮箱地址只显示为掩码。

## 1. 确认手机连接

```powershell
adb devices
```

手机应显示为 `device`，不能是 `unauthorized`。

## 2. 清空旧日志

```powershell
adb logcat -c
```

## 3. 开始实时记录

在项目目录运行：

```powershell
adb logcat -v time "BondMail:D" "BondMail-IMAP:D" "BondMail-SMTP:D" "BondMail-Perf:D" "BondMail-Web:D" "BondMail-OAuth:D" "AndroidRuntime:E" "*:S" |
  Tee-Object -FilePath .\BondMail-log.txt
```

保持这个 PowerShell 窗口运行，然后在手机上复现问题：

1. 打开 BondMail。
2. 进入发生问题的邮箱。
3. 下拉同步或打开加载失败的邮件。
4. 等待错误出现。
5. 回到 PowerShell 按 `Ctrl + C` 停止记录。

把项目目录中的 `BondMail-log.txt` 发来即可。

## 4. 问题已经发生时导出当前日志

```powershell
adb logcat -d -v time "BondMail:D" "BondMail-IMAP:D" "BondMail-SMTP:D" "BondMail-Perf:D" "BondMail-Web:D" "BondMail-OAuth:D" "AndroidRuntime:E" "*:S" > .\BondMail-log.txt
```

## 5. 同时记录网络相关系统日志（连接问题仍无法定位时）

```powershell
adb logcat -c
adb logcat -v time "BondMail:D" "BondMail-IMAP:D" "BondMail-SMTP:D" "BondMail-Perf:D" "BondMail-Web:D" "BondMail-OAuth:D" "ConnectivityService:D" "NetworkMonitor:D" "AndroidRuntime:E" "*:S" |
  Tee-Object -FilePath .\BondMail-network-log.txt
```

该文件可能包含设备网络状态信息，发送前可以自行查看；BondMail 不会在日志中写入授权码。

## 6. 与 K-9 Mail 分开抓取并对比

请按 `docs/K9_BONDMAIL_LOG_COMPARISON.md` 操作。该说明包含按进程分别抓 Logcat、同步/首次打开/缓存再次打开的统一测试步骤，以及 `dumpsys gfxinfo ... framestats` 帧耗时导出命令。


## v0.2.27.0 首次同步与 HTML 分类日志

Debug 包中重点过滤：

```powershell
adb logcat -v threadtime `
  "BondMail:D" `
  "BondMail-IMAP:D" `
  "BondMail-Web:D" `
  "AndroidRuntime:E" `
  "*:S" | Tee-Object -FilePath .\BondMail-v027.log
```

首次同步应出现：

```text
header window provider=... messages=... initial=true fetch=...ms
initial headers visible ...
syncAccount success ... elapsed=...ms
```

网易/Cloudflare 排版应出现：

```text
layout selected=FLUID ... transactionalFluid=true domain=service.netease.com
fluid compact expanded tag=table ...
layout selected=DESKTOP_SCALED ... domain=em1.cloudflare.com
```

渲染进程被系统回收时会出现：

```text
renderer gone crashed=... priority=...
```

日志中不得包含真实授权码、Access Token、Refresh Token 或邮箱密码。

## v0.2.28.1 视口预热、点击优先与详情复用

Debug 包可继续使用 `BondMail / BondMail-IMAP / BondMail-Web` 过滤。重点查看：

```text
body cache hit uid=...
body cache miss provider=... uid=...
document reuse retained=true layout=...
document load changedMessage=... layout=...
```

判断方式：

- 第一次打开未预热邮件可能出现 `body cache miss`；
- 首页停留后再打开可见邮件应更容易出现 `body cache hit`；
- 返回并立即重开同一封邮件应出现 `document reuse retained=true`；
- 打开另一封邮件会正常出现新的 `document load`，旧正文不会闪现；
- 网易域名日志中的 `transactionalFluid=true` 只用于 HTML 布局分类，不参与正文下载优先级。
