# K-9 Mail 与 BondMail 日志抓取、脱敏和首次同步对比

本说明用于比较同一个邮箱在 K-9 Mail 与 BondMail 中的：

- 添加账户与首次同步耗时；
- 邮件头批量获取方式；
- 小邮件正文自动下载方式；
- 邮件逐条进入列表的时机；
- 打开正文的建连、文件夹、MIME 与 WebView 阶段；
- 正文滚动与帧耗时。

## 重要安全提示

K-9/Thunderbird Android 的某些 Debug 版本即使关闭“敏感日志”，仍可能在账户配置持久化日志中输出：

- 完整邮箱地址；
- IMAP/SMTP 主机；
- 应用专用密码或客户端授权码；
- 邮件标题、收件人和退订链接中的 Token。

抓取后先搜索：

```text
password
secret
token
Authorization
incomingServerSettings
outgoingServerSettings
```

发现密码或授权码时，应立即在邮箱网页端撤销并重新生成。不要把原始完整日志放入公开仓库、工单或聊天；只发送脱敏副本。

BondMail 自己的关键日志只输出掩码账户提示和阶段耗时，不应记录密码、授权码、Token 或完整正文。

## 1. 准备 ADB

确认设备：

```powershell
adb devices
```

BondMail 包名：

```text
com.bond.mail
```

查询 K-9/Thunderbird 实际包名：

```powershell
adb shell pm list packages | findstr /I "k9 thunderbird"
```

示例变量：

```powershell
$k9Package = "com.fsck.k9"
$bondPackage = "com.bond.mail"
```

## 2. K-9 首次添加账户日志

先在 K-9 中启用：

```text
设置 → 常规设置 → 调试 → 启用调试日志 / 同步调试日志
```

不要启用敏感日志。为了保留首次同步过程，可先删除测试邮箱账户，而不是清除整个 App 的其他账户。

```powershell
adb shell am force-stop $k9Package
adb logcat -c
adb shell monkey -p $k9Package -c android.intent.category.LAUNCHER 1
Start-Sleep -Seconds 2
$k9Pid = (adb shell pidof $k9Package).Trim()

adb logcat --pid=$k9Pid -v threadtime |
  Tee-Object -FilePath .\K9-first-sync-full.log
```

保持窗口运行，然后：

1. 添加测试邮箱；
2. 完成权限和账户设置；
3. 进入收件箱；
4. 等邮件停止继续增加；
5. PowerShell 按 `Ctrl + C`。

K-9 进程中途重启时 PID 会变化，需要重新执行 `pidof`。

## 3. BondMail 首次添加账户日志

必须安装 Debug 构建。`performance`/Release 构建可能经过 R8 混淆，也可能不输出应用内部阶段日志。

```powershell
.\gradlew.bat installDebug --no-daemon

adb shell am force-stop $bondPackage
adb shell pm clear $bondPackage
adb logcat -c
```

只抓 BondMail 的关键标签：

```powershell
adb logcat -v threadtime `
  "BondMail:D" `
  "BondMail-IMAP:D" `
  "BondMail-SMTP:D" `
  "BondMail-Perf:D" `
  "BondMail-Web:D" `
  "AndroidRuntime:E" `
  "*:S" |
  Tee-Object -FilePath .\BondMail-first-sync-key.log
```

然后在手机中添加同一个测试邮箱，等收件箱停止继续增加，再按 `Ctrl + C`。

## 4. v0.2.23.0 应出现的关键日志

搜索：

```text
syncAccount start
connect attempt
connect success
connect reuse
sync success
initial inbox visible
initial inbox batch
body batch success
body cache hit
body cache miss
body success
document load
gesture travel
```

预期含义：

- `sync success ... newHeaders=...`：一次批量邮件头 FETCH 已完成；
- `initial inbox visible ... first=1`：第一条历史邮件已写入 Room；
- 多条 `initial inbox batch`：剩余历史邮件分批进入本地列表；
- `body batch success requested=... loaded=... skippedLarge=... limitBytes=131072`：最近小邮件正文通过一次批量 BODY FETCH 获取，大邮件保留按需下载；
- `connect reuse`：复用刚才账户验证/邮件头同步使用的认证 Store；
- `gesture travel=...`：正文 WebView 确实收到完整拖动手势。

## 5. K-9 日志中值得对比的模式

首次同步通常能看到：

```text
UID SEARCH ...
UID FETCH ... HEADER.FIELDS ... RFC822.SIZE ...
Have ... large messages and ... small messages
UID FETCH ... BODY.PEEK[]
saveMessage
notify listeners that we got a new small message
```

这里的重点是：

1. 邮件头是批量 FETCH；
2. 按 `maximumAutoDownloadMessageSize` 区分小邮件与大邮件；
3. 小邮件正文也是一个批量 FETCH；
4. 服务器每返回一封，客户端就保存这一封并通知列表；
5. “逐条显示”不等于“每封邮件单独建立网络请求”。

BondMail v0.2.23.0 采用相同原则：网络保持批量，Room/UI 提交渐进化；自动正文上限同样为 128 KiB，但首轮只预取最近可见窗口，控制移动数据和首屏负载。

## 6. 打开同一封邮件的对比

### BondMail

```powershell
adb logcat -c
adb logcat -v threadtime `
  "BondMail-IMAP:D" "BondMail-Web:D" "BondMail-Perf:D" "AndroidRuntime:E" "*:S" |
  Tee-Object -FilePath .\BondMail-detail-key.log
```

操作：

1. 打开目标邮件；
2. 等正文和图片显示；
3. 从顶部连续拖动、短拖、快速 fling；
4. 返回列表，再打开同一封邮件；
5. 按 `Ctrl + C`。

`body success` 会拆分：

```text
connect=...ms
open=...ms
structure=...ms
parse=...ms
total=...ms
```

判断：

- `connect` 大：TLS/登录或网络等待；
- `open` 大：IMAP 文件夹打开慢；
- `structure` 大：MIME/正文下载慢；
- `parse` 大：复杂 HTML、字符集或内嵌资源解析慢；
- 第二次打开出现 `body cache hit`：不应再次下载正文；
- `document load ... layout=DESKTOP_SCALED desktopWidth=600`：桌面 newsletter 进入整页缩放模式；
- `gesture travel` 有值但 `scrollY` 不变：检查文档真实高度/顶部边缘；
- `gesture travel` 都没有：触摸没有到达 WebView。

## 7. 帧耗时

### BondMail

```powershell
adb shell dumpsys gfxinfo $bondPackage reset
# 在手机上滚动约 10 秒
adb shell dumpsys gfxinfo $bondPackage framestats > .\BondMail-framestats.txt
```

### K-9

```powershell
adb shell dumpsys gfxinfo $k9Package reset
# 执行相同滚动动作约 10 秒
adb shell dumpsys gfxinfo $k9Package framestats > .\K9Mail-framestats.txt
```

公平比较帧率时使用 BondMail `performance` 构建；定位 IMAP/HTML 时使用 Debug 构建。不要把 Compose Debug 开销当成最终产品性能。

## 8. 建议保留的脱敏文件

```text
K9-first-sync-sanitized.log
BondMail-first-sync-key.log
BondMail-detail-key.log
BondMail-framestats.txt
K9Mail-framestats.txt
```

脱敏时至少替换邮箱地址、密码/授权码、Token、私有 IP、邮件 ID 和包含个人信息的标题。
