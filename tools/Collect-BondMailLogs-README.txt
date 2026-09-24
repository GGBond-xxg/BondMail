BondMail 闪退日志采集（Windows）

1. 手机开启 USB 调试，用数据线连接电脑，在手机上允许调试授权。
2. 将 Collect-BondMailLogs.cmd 与 Collect-BondMailLogs.ps1 放在同一文件夹。
   脚本会自动寻找已加入 PATH 或 Android SDK 中的 adb；也可把两个文件放到 adb.exe 所在的 platform-tools 文件夹。
3. 双击 Collect-BondMailLogs.cmd。若连接多台设备，选择发生闪退的主力机。
4. 出现 Recording 后，在手机上打开 BondMail，进入已发送/发件箱，滚动到 OSL 邮件附近，停留等待，按你平时能触发闪退的方式复现。
5. 闪退后回到电脑窗口按 Enter。桌面将生成 BondMail-Logs-日期时间.zip。
6. 把 ZIP 发到当前对话，并说明停留多久后闪退、有无继续滑动或点击；没有复现也请说明。

脚本不清空已有日志、不卸载应用、不清除数据、不更改系统设置、不上传文件。
只采集崩溃相关系统标签、应用退出记录、机型/系统/应用版本，不读取邮件数据库。
日志仍可能包含邮箱地址或账号名称，发送前可检查并遮盖这些信息，保留异常名和堆栈。

指定 ADB 路径（PowerShell）：
powershell -NoProfile -ExecutionPolicy Bypass -File .\Collect-BondMailLogs.ps1 -AdbPath "C:\platform-tools\adb.exe"
