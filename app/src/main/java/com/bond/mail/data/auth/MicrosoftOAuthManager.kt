package com.bond.mail.data.auth

import android.app.Activity
import android.content.Context
import com.bond.mail.data.mail.MailLog
import com.microsoft.identity.client.AcquireTokenParameters
import com.microsoft.identity.client.AcquireTokenSilentParameters
import com.microsoft.identity.client.AuthenticationCallback
import com.microsoft.identity.client.IAuthenticationResult
import com.microsoft.identity.client.IMultipleAccountPublicClientApplication
import com.microsoft.identity.client.IPublicClientApplication
import com.microsoft.identity.client.PublicClientApplication
import com.microsoft.identity.client.Prompt
import com.microsoft.identity.client.SilentAuthenticationCallback
import com.microsoft.identity.client.exception.MsalException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Microsoft personal/work account OAuth backed by MSAL's encrypted token cache. */
class MicrosoftOAuthManager(
    context: Context,
    private val configurations: OAuthClientConfigStore,
) {
    private val appContext = context.applicationContext
    private val initializationMutex = Mutex()

    @Volatile
    private var application: IMultipleAccountPublicClientApplication? = null

    fun configurationChanged() {
        application = null
    }

    suspend fun authorize(
        activity: Activity,
        providerId: String,
        loginHint: String? = null,
        forceLogin: Boolean = false,
    ): OAuthGrant {
        MailLog.d(
            MailLog.OAUTH,
            "microsoft authorize start provider=$providerId mode=${if (forceLogin) "renew" else "add"} " +
                "account=${loginHint?.let(MailLog::accountHint) ?: "selector"}",
        )
        val app = application()
        val result = suspendCancellableCoroutine<IAuthenticationResult> { continuation ->
            val callback = object : AuthenticationCallback {
                override fun onSuccess(authenticationResult: IAuthenticationResult) {
                    if (continuation.isActive) continuation.resume(authenticationResult)
                }

                override fun onError(exception: MsalException) {
                    if (continuation.isActive) continuation.resumeWithException(exception)
                }

                override fun onCancel() {
                    if (continuation.isActive) {
                        continuation.resumeWithException(OAuthAuthorizationCancelledException())
                    }
                }
            }
            val parametersBuilder = AcquireTokenParameters.Builder()
                .startAuthorizationFromActivity(activity)
                .withScopes(MAIL_SCOPES)
                .withPrompt(if (forceLogin) Prompt.LOGIN else Prompt.SELECT_ACCOUNT)
                .withCallback(callback)
            loginHint?.trim()?.takeIf { it.contains('@') }?.let(parametersBuilder::withLoginHint)
            val parameters = parametersBuilder.build()

            activity.runOnUiThread {
                runCatching { app.acquireToken(parameters) }
                    .onFailure { failure ->
                        if (continuation.isActive) continuation.resumeWithException(failure)
                    }
            }
        }

        val email = result.account.username.orEmpty().trim()
        if (!email.contains('@')) {
            throw OAuthConfigurationException("Microsoft did not return a mailbox address")
        }
        MailLog.d(MailLog.OAUTH, "microsoft authorize success account=${MailLog.accountHint(email)}")
        return OAuthGrant(
            providerId = providerId,
            email = email,
            displayName = email.substringBefore('@').ifBlank { "Microsoft" },
            providerAccountId = result.account.id,
            accessToken = result.accessToken,
        )
    }

    /** Obtain a cached Microsoft token without starting UI. UI-required failures are surfaced to
     * the account editor so the user can explicitly sign in again. */
    suspend fun accessToken(providerAccountId: String): String {
        val app = application()
        val account = withContext(Dispatchers.IO) {
            runCatching { app.getAccount(providerAccountId) }
                .getOrElse { failure ->
                    throw OAuthReauthorizationRequiredException(
                        "Microsoft account is no longer available",
                        failure,
                    )
                }
        } ?: throw OAuthReauthorizationRequiredException("Microsoft account is no longer available")

        val authority = account.authority
        MailLog.d(
            MailLog.OAUTH,
            "microsoft silent token start account=${MailLog.accountHint(account.username.orEmpty())}",
        )
        return suspendCancellableCoroutine { continuation ->
            val parameters = AcquireTokenSilentParameters.Builder()
                .forAccount(account)
                .withScopes(MAIL_SCOPES)
                .fromAuthority(authority)
                .withCallback(object : SilentAuthenticationCallback {
                    override fun onSuccess(authenticationResult: IAuthenticationResult) {
                        MailLog.d(
                            MailLog.OAUTH,
                            "microsoft silent token success " +
                                "account=${MailLog.accountHint(account.username.orEmpty())}",
                        )
                        if (continuation.isActive) {
                            continuation.resume(authenticationResult.accessToken)
                        }
                    }

                    override fun onError(exception: MsalException) {
                        if (!continuation.isActive) return
                        if (exception is com.microsoft.identity.client.exception.MsalUiRequiredException) {
                            continuation.resumeWithException(
                                OAuthReauthorizationRequiredException(cause = exception),
                            )
                        } else {
                            // Network and transient broker failures are not consent revocations.
                            // Preserve them so normal retry/error handling can distinguish the case.
                            continuation.resumeWithException(exception)
                        }
                    }
                })
                .build()
            runCatching { app.acquireTokenSilentAsync(parameters) }
                .onFailure { failure ->
                    if (continuation.isActive) continuation.resumeWithException(failure)
                }
        }
    }

    private suspend fun application(): IMultipleAccountPublicClientApplication {
        application?.let { return it }
        return initializationMutex.withLock {
            application?.let { return@withLock it }
            suspendCancellableCoroutine { continuation ->
                PublicClientApplication.createMultipleAccountPublicClientApplication(
                    appContext,
                    configurations.microsoftConfigurationFile(),
                    object : IPublicClientApplication.IMultipleAccountApplicationCreatedListener {
                        override fun onCreated(application: IMultipleAccountPublicClientApplication) {
                            this@MicrosoftOAuthManager.application = application
                            if (continuation.isActive) continuation.resume(application)
                        }

                        override fun onError(exception: MsalException) {
                            if (continuation.isActive) {
                                continuation.resumeWithException(
                                    OAuthConfigurationException(
                                        "Microsoft OAuth configuration could not be loaded",
                                        exception,
                                    ),
                                )
                            }
                        }
                    },
                )
            }
        }
    }

    companion object {
        val MAIL_SCOPES: List<String> = listOf(
            "https://outlook.office.com/IMAP.AccessAsUser.All",
            "https://outlook.office.com/SMTP.Send",
        )
    }
}
