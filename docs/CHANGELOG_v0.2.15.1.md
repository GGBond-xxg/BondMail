# BondMail v0.2.15.1

## 编译兼容热修复

修复 v0.2.15.0 在当前 Compose BOM 下的 Kotlin 编译错误，不改变动画设计和业务逻辑：

- `BondMotion.kt`：移除不可用的 `LocalMotionDurationScale`，改为读取 Android 系统的 `Settings.Global.ANIMATOR_DURATION_SCALE`，并监听设置变化，继续支持系统“移除动画/动画缩放 0x”。
- `HomeScreen.kt`：`MutableTransitionState` 改为从 `androidx.compose.animation.core` 导入。
- `DetailScreen.kt`：移除不存在的 `calculateTopPadding` 顶层导入；继续使用 `PaddingValues.calculateTopPadding()` 成员方法。
- 版本号更新为 `0.2.15.1`，`versionCode` 更新为 `29`。

## 建议构建命令

先停止残留 Gradle 进程，再单独执行一个目标：

```powershell
.\gradlew.bat --stop
.\gradlew.bat clean installPerformance --no-daemon
```

不要同时粘贴运行 `compileDebugKotlin`、`assembleDebug`、`assemblePerformance` 三个命令；后两个都会再次触发编译。
