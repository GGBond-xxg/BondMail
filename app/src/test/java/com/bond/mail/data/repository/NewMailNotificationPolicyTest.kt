package com.bond.mail.data.repository

import com.bond.mail.data.db.MessageEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NewMailNotificationPolicyTest {
    private val arrival = MessageEntity(
        id = "mail", accountId = "account", folderType = "INBOX", remoteFolder = "INBOX",
        remoteUid = 42, senderName = "Sender", senderAddress = "sender@example.com",
        subject = "New mail", preview = "", bodyText = "", bodyHtml = null,
        receivedAt = 1, unread = true, starred = false, hasAttachments = false,
    )

    private fun eligible(
        latest: MessageEntity? = arrival,
        discovered: MessageEntity = arrival,
        pending: Boolean = false,
        consumed: Boolean = false,
    ) = NewMailNotificationPolicy.shouldAlert(discovered, latest, pending, consumed)

    @Test fun genuinelyNewUnreadMailAlerts() = assertTrue(eligible())
    @Test fun localReadAfterSyncSnapshotSuppressesDelayedAlert() =
        assertFalse(eligible(latest = arrival.copy(unread = false)))
    @Test fun mailAlreadyReadOnAnotherDeviceDoesNotAlert() =
        assertFalse(eligible(discovered = arrival.copy(unread = false)))
    @Test fun pendingReadSuppressesAlertBeforeRoomWrite() =
        assertFalse(eligible(pending = true))
    @Test fun consumedMailDoesNotReplayAfterRestartOrMarkUnread() =
        assertFalse(eligible(consumed = true))
    @Test fun removedOrMovedMailDoesNotAlert() {
        assertFalse(eligible(latest = null))
        assertFalse(eligible(latest = arrival.copy(folderType = "TRASH")))
    }
    @Test fun replacementUidOrAccountCannotReuseOldCandidate() {
        assertFalse(eligible(latest = arrival.copy(remoteUid = 43)))
        assertFalse(eligible(latest = arrival.copy(accountId = "other")))
    }
    @Test fun localDraftAndNonInboxMailDoNotAlert() {
        assertFalse(eligible(discovered = arrival.copy(remoteUid = 0)))
        assertFalse(eligible(discovered = arrival.copy(deliveryState = "DRAFT")))
        assertFalse(eligible(discovered = arrival.copy(folderType = "SENT")))
    }
}
