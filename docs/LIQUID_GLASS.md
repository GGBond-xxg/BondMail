# Liquid Glass

在 **设置 → 外观 → 界面样式 → Liquid Glass** 启用，切回 Material 3 或 MIUIX 即可关闭。
选择会保存，支持浅色、深色、跟随系统、壁纸取色及自选主题色。

## 效果范围

- 邮件、联系人和设置页共用的底部导航，以及旁边的写信按钮。
- 有账户时的收件箱顶栏，实时采样其下方滚动的邮件列表。
- 邮件卡片、正文和设置内容保持普通清晰材质。详情页的 WebView 不参与背景采样。

Android 13 / API 33 以上使用 GPU 模糊、边缘折射和高光。Android 8–12 使用原有
实色控件，仍保留主题选择。省电、高对比度文字或移除动画开启时也回退为实色；
系统设置恢复后自动恢复效果。折射不会模糊前景文字和图标。

## 实现与维护

用户提供的 QWEA0/Liquid-Glass-Android 面向 View/XML，作者建议 Compose 项目使用
Kyant0/AndroidLiquidGlass。此项目使用 Compose，因此接入后者的固定源码版本
`1.0.0-alpha04`（`84456cbe04e093f1f7f7a590330a3b2615a785a4`）。源码随应用一起
编译，保留当前 Kotlin 2.1 / Compose 1.8 工具链，没有引入 JitPack 或额外原生库。
来源、许可证与本地修改见根目录 `THIRD_PARTY_NOTICES.md`。

`BondLiquidGlass.kt` 管理材质与回退，`LocalGlassBackdrop` 仅提供给录制内容之外的
导航控件，避免渲染层自引用。主页面和收件箱各自拥有独立内容层。关闭效果时不挂载
采样 modifier；没有定时截图、位图读取或持续亮度采样任务。

`LiquidGlassStyleTest` 覆盖实际选择器、持久化、浅/深/动态色切换、Activity 重建、
减少动画回退，以及三种界面样式往返切换，并恢复测试前的用户设置。
