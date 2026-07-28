# BondMail v0.2.35.1

- `versionCode`: `56`
- `versionName`: `0.2.35.1`
- Room: `v8`（无数据库结构变化）
- MIME parser: `v8`
- HTML prepared-document cache: `layout-v28`
- Upgrade baseline: `v0.2.35.0`

## 编译热修复

修复 `DetailScreen.kt:406` 的 Compose `Modifier.padding()` 参数组合错误。

原代码：

```kotlin
.padding(horizontal = 24.dp, bottom = 96.dp)
```

Compose 的 `padding` 没有“`horizontal` 与 `bottom` 混合”的重载，因此 Debug 和 Performance 都会在 Kotlin 类型检查阶段失败。

现改为四边参数重载：

```kotlin
.padding(start = 24.dp, end = 24.dp, bottom = 96.dp)
```

显示效果不变：左右仍为 `24.dp`，底部仍为 `96.dp`。本次不改动首封未读打开事务、稳定邮件头部、正文渐显、Binance HTML 排版、夜间模式或数据库结构。

## 修改文件

- `app/src/main/java/com/bond/mail/ui/screens/DetailScreen.kt`
- `app/build.gradle.kts`
- `app/src/main/java/com/bond/mail/data/mail/ImapClient.kt`

## 构建命令

```powershell
.\gradlew.bat --stop
.\gradlew.bat clean compileDebugKotlin assembleDebug assemblePerformance --no-daemon
```
