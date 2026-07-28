package com.bond.mail.data.auth

import android.app.PendingIntent

/**
 * A short-lived provider authorization result. Access tokens are intentionally never persisted by
 * BondMail; Microsoft MSAL and Google Identity Services own their provider token caches.
 */
data class OAuthGrant(
    val providerId: String,
    val email: String,
    val displayName: String,
    val providerAccountId: String,
    val accessToken: String,
) {
    /** Prevent accidental token disclosure from debugger/logging code that prints this value. */
    override fun toString(): String =
        "OAuthGrant(providerId=$providerId, email=<redacted>, displayName=<redacted>, " +
            "providerAccountId=<redacted>, accessToken=<redacted>)"
}

sealed interface GoogleAuthorizationStep {
    data class Authorized(val grant: OAuthGrant) : GoogleAuthorizationStep
    data class RequiresResolution(val pendingIntent: PendingIntent) : GoogleAuthorizationStep
}

class OAuthReauthorizationRequiredException(
    message: String = "OAuth authorization must be renewed",
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

/** Raised when a reauthorization flow returns a different mailbox than the local account. */
class OAuthAccountMismatchException(
    val expectedEmail: String,
    val actualEmail: String,
) : IllegalStateException("OAuth account mismatch")

class OAuthAuthorizationCancelledException : IllegalStateException("OAuth authorization was cancelled")

class OAuthConfigurationException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)
