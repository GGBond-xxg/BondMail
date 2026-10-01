package com.bond.mail.data.repository

import com.bond.mail.data.db.MessageEntity

internal object NewMailNotificationPolicy {
    fun shouldAlert(
        discovered: MessageEntity,
        latest: MessageEntity?,
        pendingRead: Boolean,
        consumed: Boolean,
    ): Boolean = !consumed && !pendingRead &&
        discovered.unread && discovered.folderType == "INBOX" &&
        discovered.deliveryState == "REMOTE" && discovered.remoteUid > 0L &&
        latest != null && latest.unread && latest.deliveryState == "REMOTE" &&
        latest.id == discovered.id && latest.accountId == discovered.accountId &&
        latest.folderType == discovered.folderType && latest.remoteUid == discovered.remoteUid
}
