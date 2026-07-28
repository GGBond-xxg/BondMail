# BondMail v0.2.17.1

这是 v0.2.17.0 的 Kotlin 编译兼容性修复版，不修改数据库结构、邮件业务逻辑或界面行为。

## 修复

- 删除 `MailApp.kt` 中当前 Compose UI 版本不存在的顶层导入：

  ```kotlin
  import androidx.compose.ui.input.pointer.consume
  ```

- 保留拖动排序中的：

  ```kotlin
  change.consume()
  ```

  在本项目使用的 Compose 版本中，`consume()` 是 `PointerInputChange` 可直接调用的成员，不需要单独导入。

- 修复 Debug 和 Performance 构建共同出现的：

  ```text
  Unresolved reference 'consume'
  ```

## 版本

- `versionCode`: 33
- `versionName`: 0.2.17.1
- Room 数据库版本继续为 v5，无新增迁移。
