# BondMail v0.2.31.1

- `versionCode`: `50`
- `versionName`: `0.2.31.1`
- Upgrade baseline: `v0.2.31.0`

## Compilation hotfix

`SmtpClient.prepare()` and `SmtpClient.send()` are public functions. In v0.2.31.0 they returned the internal type `PreparedOutgoingMessage`, which Kotlin rejects because a public API cannot expose a less-visible return type.

The return type now has public visibility:

```kotlin
data class PreparedOutgoingMessage(
    val internetMessageId: String,
    val raw: ByteArray,
)
```

No runtime logic changed. SMTP delivery, IMAP Sent append, draft synchronization, unread handling, predictive back and WebView rendering remain identical to v0.2.31.0.
