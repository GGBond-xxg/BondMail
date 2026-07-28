# BondMail v0.2.12.1 编译热修复

## 修复

1. 删除 `DetailScreen.kt` 中无法解析的：

```kotlin
import androidx.compose.foundation.layout.calculateTopPadding
```

项目当前 Compose 版本中的 `calculateTopPadding()` 可直接通过 `PaddingValues` 调用，不需要该顶层导入。

2. 将：

```kotlin
error?.description.orEmpty()
```

修改为：

```kotlin
error?.description?.toString().orEmpty()
```

`WebResourceError.description` 的类型是 `CharSequence?`，不能直接使用仅适用于 `String?` 的 `orEmpty()`。

3. 版本号更新为 `0.2.12.1`，`versionCode` 更新为 `25`。
