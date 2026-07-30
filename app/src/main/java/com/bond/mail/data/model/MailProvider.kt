package com.bond.mail.data.model

import com.bond.mail.data.db.AccountEntity

enum class AuthType { APP_PASSWORD, OAUTH2 }
enum class MailSecurity { SSL_TLS, STARTTLS, NONE }
enum class MailAuthMechanism { AUTO, LOGIN, PLAIN }

data class CustomMailConfig(
    val loginName: String,
    val imapHost: String,
    val imapPort: Int,
    val imapSecurity: MailSecurity,
    val smtpHost: String,
    val smtpPort: Int,
    val smtpSecurity: MailSecurity,
    val authMechanism: MailAuthMechanism,
)

data class MailProvider(
    val id: String,
    val label: String,
    val suffixes: List<String>,
    val imapHost: String,
    val imapPort: Int = 993,
    val imapSecurity: MailSecurity = MailSecurity.SSL_TLS,
    val smtpHost: String,
    val smtpPort: Int,
    val smtpSecurity: MailSecurity,
    val authType: AuthType,
    val authMechanism: MailAuthMechanism = MailAuthMechanism.AUTO,
    val netEaseClientId: Boolean = false,
    val visibleInPicker: Boolean = true,
) {
    val smtpSsl: Boolean get() = smtpSecurity == MailSecurity.SSL_TLS
    val smtpStartTls: Boolean get() = smtpSecurity == MailSecurity.STARTTLS
}

object ProviderRegistry {
    val providers: List<MailProvider> = listOf(
        MailProvider("qq", "QQ Mail", listOf("qq.com", "foxmail.com"), "imap.qq.com", smtpHost = "smtp.qq.com", smtpPort = 465, smtpSecurity = MailSecurity.SSL_TLS, authType = AuthType.APP_PASSWORD),
        MailProvider("163", "163 Mail", listOf("163.com"), "imap.163.com", smtpHost = "smtp.163.com", smtpPort = 465, smtpSecurity = MailSecurity.SSL_TLS, authType = AuthType.APP_PASSWORD, authMechanism = MailAuthMechanism.LOGIN, netEaseClientId = true),
        MailProvider("126", "126 Mail", listOf("126.com"), "imap.126.com", smtpHost = "smtp.126.com", smtpPort = 465, smtpSecurity = MailSecurity.SSL_TLS, authType = AuthType.APP_PASSWORD, authMechanism = MailAuthMechanism.LOGIN, netEaseClientId = true),
        MailProvider("gmail", "Gmail", listOf("gmail.com", "googlemail.com"), "imap.gmail.com", smtpHost = "smtp.gmail.com", smtpPort = 587, smtpSecurity = MailSecurity.STARTTLS, authType = AuthType.OAUTH2),
        MailProvider("outlook", "Outlook / Hotmail / Live", listOf("outlook.com", "outlook.jp", "outlook.co.jp", "hotmail.com", "hotmail.co.jp", "live.com", "live.jp", "msn.com"), "outlook.office365.com", smtpHost = "smtp-mail.outlook.com", smtpPort = 587, smtpSecurity = MailSecurity.STARTTLS, authType = AuthType.OAUTH2),
        MailProvider("m365", "Microsoft 365", emptyList(), "outlook.office365.com", smtpHost = "smtp.office365.com", smtpPort = 587, smtpSecurity = MailSecurity.STARTTLS, authType = AuthType.OAUTH2, visibleInPicker = false),
        MailProvider("icloud", "iCloud Mail", listOf("icloud.com", "me.com", "mac.com"), "imap.mail.me.com", smtpHost = "smtp.mail.me.com", smtpPort = 587, smtpSecurity = MailSecurity.STARTTLS, authType = AuthType.APP_PASSWORD),
        MailProvider("yahoo", "Yahoo Mail", listOf("yahoo.com", "yahoo.com.cn", "yahoo.co.jp"), "imap.mail.yahoo.com", smtpHost = "smtp.mail.yahoo.com", smtpPort = 465, smtpSecurity = MailSecurity.SSL_TLS, authType = AuthType.APP_PASSWORD),
        MailProvider("custom", "Other Mail", emptyList(), "", smtpHost = "", smtpPort = 587, smtpSecurity = MailSecurity.STARTTLS, authType = AuthType.APP_PASSWORD),
    )

    fun byId(id: String): MailProvider = providers.first { it.id == id }
    fun fromEmail(email: String): MailProvider? = providers.firstOrNull { provider ->
        provider.suffixes.any { email.endsWith("@$it", ignoreCase = true) }
    }

    fun forAccount(account: AccountEntity): MailProvider {
        val provider = byId(account.providerId)
        if (provider.id != "custom") return provider
        return provider.copy(
            imapHost = account.customImapHost.orEmpty(),
            imapPort = account.customImapPort ?: 993,
            imapSecurity = account.customImapSecurity
                ?.let { runCatching { MailSecurity.valueOf(it) }.getOrNull() }
                ?: MailSecurity.SSL_TLS,
            smtpHost = account.customSmtpHost.orEmpty(),
            smtpPort = account.customSmtpPort ?: 587,
            smtpSecurity = account.customSmtpSecurity
                ?.let { runCatching { MailSecurity.valueOf(it) }.getOrNull() }
                ?: MailSecurity.STARTTLS,
            authMechanism = account.customAuthMechanism
                ?.let { runCatching { MailAuthMechanism.valueOf(it) }.getOrNull() }
                ?: MailAuthMechanism.AUTO,
        )
    }
}

val AccountEntity.mailLoginName: String
    get() = loginName?.trim().takeUnless { it.isNullOrBlank() } ?: email.trim()

val AccountEntity.visibleEmail: String
    get() = displayEmail?.trim()
        ?.takeIf { it.equals(email.trim(), ignoreCase = true) }
        ?: email.trim()

val AccountEntity.visibleAvatarText: String
    get() = avatarText?.trim().takeUnless { it.isNullOrBlank() }
        ?: displayName.trim().firstOrNull()?.uppercase()
        ?: "@"
