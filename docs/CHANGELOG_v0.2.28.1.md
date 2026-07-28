# BondMail v0.2.28.1

## Compose 编译热修复

- 修复 `DetailScreen.kt` 中在 `remember { ... }` 非 Composable 计算块内调用 `tr("no_subject")` 导致的编译错误。
- 现在先在 Composable 作用域解析本地化的“无主题”文案，再把普通字符串传入 `remember`。
- 将该字符串加入 `remember` key，切换语言后邮件头仍会正确更新。
- 不改变 v0.2.28.0 的 Gmail 风格详情布局、正文预热、Header/Body 分离、HTML 双模式和底部操作栏。

## 版本

```text
versionCode = 45
versionName = 0.2.28.1
Room 数据库版本 = 6
HTML 文档缓存 = layout-v22
```
