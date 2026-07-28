package com.bond.mail.data.model

enum class AuthType { APP_PASSWORD, OAUTH2 }

data class MailProvider(
    val id: String,
    val label: String,
    val suffixes: List<String>,
    val imapHost: String,
    val imapPort: Int = 993,
    val smtpHost: String,
    val smtpPort: Int,
    val smtpSsl: Boolean,
    val smtpStartTls: Boolean,
    val authType: AuthType,
    val netEaseClientId: Boolean = false,
    val visibleInPicker: Boolean = true,
)

object ProviderRegistry {
    val providers: List<MailProvider> = listOf(
        MailProvider("qq", "QQ Mail", listOf("qq.com", "foxmail.com"), "imap.qq.com", smtpHost = "smtp.qq.com", smtpPort = 465, smtpSsl = true, smtpStartTls = false, authType = AuthType.APP_PASSWORD),
        MailProvider("163", "163 Mail", listOf("163.com"), "imap.163.com", smtpHost = "smtp.163.com", smtpPort = 465, smtpSsl = true, smtpStartTls = false, authType = AuthType.APP_PASSWORD, netEaseClientId = true),
        MailProvider("126", "126 Mail", listOf("126.com"), "imap.126.com", smtpHost = "smtp.126.com", smtpPort = 465, smtpSsl = true, smtpStartTls = false, authType = AuthType.APP_PASSWORD, netEaseClientId = true),
        MailProvider("gmail", "Gmail", listOf("gmail.com", "googlemail.com"), "imap.gmail.com", smtpHost = "smtp.gmail.com", smtpPort = 587, smtpSsl = false, smtpStartTls = true, authType = AuthType.OAUTH2),
        MailProvider("outlook", "Outlook / Hotmail / Live", listOf("outlook.com", "outlook.jp", "outlook.co.jp", "hotmail.com", "hotmail.co.jp", "live.com", "live.jp", "msn.com"), "outlook.office365.com", smtpHost = "smtp-mail.outlook.com", smtpPort = 587, smtpSsl = false, smtpStartTls = true, authType = AuthType.OAUTH2),
        MailProvider("m365", "Microsoft 365", emptyList(), "outlook.office365.com", smtpHost = "smtp.office365.com", smtpPort = 587, smtpSsl = false, smtpStartTls = true, authType = AuthType.OAUTH2, visibleInPicker = false),
        MailProvider("icloud", "iCloud Mail", listOf("icloud.com", "me.com", "mac.com"), "imap.mail.me.com", smtpHost = "smtp.mail.me.com", smtpPort = 587, smtpSsl = false, smtpStartTls = true, authType = AuthType.APP_PASSWORD),
        MailProvider("yahoo", "Yahoo Mail", listOf("yahoo.com", "yahoo.com.cn", "yahoo.co.jp"), "imap.mail.yahoo.com", smtpHost = "smtp.mail.yahoo.com", smtpPort = 465, smtpSsl = true, smtpStartTls = false, authType = AuthType.APP_PASSWORD),
    )

    fun byId(id: String): MailProvider = providers.first { it.id == id }
    fun fromEmail(email: String): MailProvider? = providers.firstOrNull { provider ->
        provider.suffixes.any { email.endsWith("@$it", ignoreCase = true) }
    }
}
