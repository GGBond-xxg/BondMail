package com.bond.mail.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bond.mail.data.db.AccountEntity
import com.bond.mail.data.db.MessageListRow
import com.bond.mail.data.model.visibleEmail
import com.bond.mail.data.settings.MailDensity
import com.bond.mail.ui.i18n.tr
import com.bond.mail.ui.motion.BondMotionDuration
import com.bond.mail.ui.motion.bondMotionEnabled
import com.bond.mail.ui.theme.bondSurfaces
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Compact Gmail-like message row with click, long-press selection and a separate star target. */
@Composable
fun MessageCard(
    message: MessageListRow,
    account: AccountEntity?,
    contactAvatarText: String? = null,
    density: MailDensity,
    monetBrandIcons: Boolean,
    onOpen: () -> Unit,
    onLongClick: () -> Unit,
    onStar: () -> Unit,
    selected: Boolean = false,
    selectionMode: Boolean = false,
    shape: Shape = MailContentDefaults.SingleItemShape,
    modifier: Modifier = Modifier,
) {
    val motionEnabled = bondMotionEnabled()
    val minimumHeight = when (density) {
        MailDensity.COMFORTABLE -> 104.dp
        MailDensity.STANDARD -> 96.dp
        MailDensity.COMPACT -> 82.dp
    }
    val avatarSize = when (density) {
        MailDensity.COMFORTABLE -> 52.dp
        MailDensity.STANDARD -> 48.dp
        MailDensity.COMPACT -> 42.dp
    }
    val verticalPadding = when (density) {
        MailDensity.COMFORTABLE -> 13.dp
        MailDensity.STANDARD -> 11.dp
        MailDensity.COMPACT -> 9.dp
    }
    val trailingHeight = when (density) {
        MailDensity.COMFORTABLE -> 76.dp
        MailDensity.STANDARD -> 68.dp
        MailDensity.COMPACT -> 56.dp
    }
    val unreadAccentHeight = when (density) {
        MailDensity.COMFORTABLE -> 58.dp
        MailDensity.STANDARD -> 54.dp
        MailDensity.COMPACT -> 44.dp
    }
    val targetRowColor = when {
        selected -> MaterialTheme.colorScheme.secondaryContainer
        message.unread -> MaterialTheme.bondSurfaces.contentUnread
        else -> MaterialTheme.bondSurfaces.content
    }
    val rowColor by animateColorAsState(
        targetValue = targetRowColor,
        animationSpec = tween(BondMotionDuration.EffectShort),
        label = "message-row-color",
    )

    val timeLabel = remember(message.receivedAt) { formatMailTime(message.receivedAt) }
    val outgoing = message.folderType == "SENT" || message.folderType == "DRAFTS"
    val outgoingState = if (message.folderType == "DRAFTS") "DRAFT" else message.deliveryState
    val primaryLabel = if (outgoing) {
        message.recipients.substringBefore(',').trim().ifBlank {
            account?.visibleEmail ?: message.senderAddress
        }
    } else {
        message.senderName.ifBlank { message.senderAddress }
    }
    val starScale = remember { Animatable(1f) }
    LaunchedEffect(message.starred, motionEnabled) {
        if (!motionEnabled) {
            starScale.snapTo(1f)
        } else {
            starScale.snapTo(if (message.starred) 0.78f else 0.90f)
            starScale.animateTo(1f)
        }
    }

    GroupedListSurface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        containerColor = rowColor,
        selected = selected,
        onClick = onOpen,
        onLongClick = onLongClick,
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            if (message.unread && !selectionMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .width(3.dp)
                        .height(unreadAccentHeight)
                        .clip(RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp))
                        .background(MaterialTheme.colorScheme.primary),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = minimumHeight)
                    .padding(start = 16.dp, end = 8.dp, top = verticalPadding, bottom = verticalPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.size(avatarSize + 4.dp), contentAlignment = Alignment.Center) {
                    if (selectionMode) {
                        Icon(
                            imageVector = if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            contentDescription = null,
                            modifier = Modifier.size(avatarSize),
                            tint = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    } else {
                        ContactAvatar(
                            name = primaryLabel,
                            email = if (outgoing) message.recipients else message.senderAddress,
                            customText = contactAvatarText,
                            size = avatarSize,
                            monet = monetBrandIcons,
                        )
                        if (account != null) {
                            AccountBadge(
                                account = account,
                                size = 18.dp,
                                modifier = Modifier.align(Alignment.BottomEnd),
                            )
                        }
                    }
                }

                Spacer(Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = primaryLabel,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 16.sp,
                            lineHeight = 20.sp,
                        ),
                        fontWeight = if (message.unread) FontWeight.Bold else FontWeight.Medium,
                        color = if (message.unread) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (message.folderType == "DRAFTS" || message.deliveryState == "DRAFT") {
                            Text(
                                text = tr("draft_label"),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            text = message.subject.ifBlank { tr("no_subject") },
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 15.sp,
                                lineHeight = 19.sp,
                            ),
                            fontWeight = if (message.unread) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (message.unread) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (density != MailDensity.COMPACT) {
                        Text(
                            text = message.preview.ifBlank { message.senderAddress },
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 14.sp,
                                lineHeight = 18.sp,
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.88f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .width(if (outgoing) 72.dp else 52.dp)
                        .height(trailingHeight),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (message.unread) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(
                            text = timeLabel,
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                            fontWeight = if (message.unread) FontWeight.Bold else FontWeight.Normal,
                            color = if (message.unread) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                        )
                    }
                    if (!selectionMode && outgoing) {
                        AnimatedContent(
                            targetState = outgoingState,
                            transitionSpec = {
                                fadeIn(tween(150)) togetherWith fadeOut(tween(110))
                            },
                            label = "outgoing-state",
                        ) { state ->
                            val (label, color) = when (state) {
                                "QUEUED", "SENDING" -> tr("sending") to MaterialTheme.colorScheme.primary
                                "FAILED" -> tr("send_failed") to MaterialTheme.colorScheme.error
                                "DRAFT" -> tr("draft_label") to MaterialTheme.colorScheme.error
                                else -> tr("sent_status") to MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = color,
                                maxLines = 1,
                            )
                        }
                    } else if (!selectionMode) {
                        IconButton(
                            onClick = onStar,
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                imageVector = if (message.starred) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                contentDescription = null,
                                tint = if (message.starred) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier
                                    .size(23.dp)
                                    .graphicsLayer {
                                        scaleX = starScale.value
                                        scaleY = starScale.value
                                    },
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatMailTime(epochMillis: Long): String {
    val zone = ZoneId.systemDefault()
    val dateTime = Instant.ofEpochMilli(epochMillis).atZone(zone)
    return if (dateTime.toLocalDate() == LocalDate.now(zone)) {
        dateTime.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
    } else {
        dateTime.format(DateTimeFormatter.ofPattern("M/d"))
    }
}
