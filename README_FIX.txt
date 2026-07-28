BondMail Kotlin v0.2.38.0

基线：v0.2.37.0

本版重点：
1. 启动器、圆形图标、Android 13+ 主题图标和启动页直接使用用户提供的彩色/monochrome 原图。
2. 应用内主动刷新、前台恢复刷新和手动 Worker 不发送系统通知；后台且应用不在前台时才允许提醒。
3. 新建 HIGH 重要级别通知频道 new_mail_alerts_v3，恢复默认声音、振动和悬浮通知条件。
4. 未加载邮件使用填满正文可用区域的加载卡、7 行骨架和 40dp 圆形加载动画。
5. 明确短邮件先弹簧收回再渐显；长邮件保持展开；不确定模板按长邮件处理。
6. 抽屉额外邮箱展开/收回增加高度、位移、透明度和箭头旋转动画。
7. v0.2.37.0 的 WebView 原生渐显、页面复用、整页 Q 弹、Grab/Binance/Facebook 排版与动量置顶继续保留。

版本：
- versionCode 59
- versionName 0.2.38.0
- Room 8
- MIME parser 8
- HTML cache layout-v33

Windows 构建：
  .\gradlew.bat --stop
  .\gradlew.bat clean compileDebugKotlin assembleDebug assemblePerformance --no-daemon

完整说明：
  docs\CHANGELOG_v0.2.38.0.md

重点回归：
- 卸载测试版后重装，确认彩色与主题图标均为提供的原图。
- 前台主动刷新出新邮件只更新列表，不弹系统通知。
- 退到后台收到新邮件时，新频道有声音、振动并可悬浮。
- 未缓存邮件加载卡填满正文；短邮件弹簧收回，长邮件保持展开。
- 抽屉超过三个邮箱时，展开和收回不再瞬间跳变。
