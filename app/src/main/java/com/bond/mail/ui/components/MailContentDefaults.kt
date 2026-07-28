package com.bond.mail.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bond.mail.ui.motion.BondMotionEasing
import com.bond.mail.ui.motion.bondMotionEnabled

/** Shared Gmail-style geometry for message, contact and search-result groups. */
internal object MailContentDefaults {
    val OuterCorner: Dp = 12.dp
    val InnerCorner: Dp = 5.dp
    val SelectedCorner: Dp = 12.dp

    val SingleItemShape: Shape = RoundedCornerShape(OuterCorner)
    val MiddleItemShape: Shape = RoundedCornerShape(InnerCorner)
    val ItemSpacing = 3.dp
    val HorizontalInset = 10.dp

    data class ItemCorners(
        val topStart: Dp,
        val topEnd: Dp,
        val bottomStart: Dp,
        val bottomEnd: Dp,
    )

    fun itemCorners(index: Int, itemCount: Int): ItemCorners {
        if (itemCount <= 1) {
            return ItemCorners(OuterCorner, OuterCorner, OuterCorner, OuterCorner)
        }
        return when (index) {
            0 -> ItemCorners(OuterCorner, OuterCorner, InnerCorner, InnerCorner)
            itemCount - 1 -> ItemCorners(InnerCorner, InnerCorner, OuterCorner, OuterCorner)
            else -> ItemCorners(InnerCorner, InnerCorner, InnerCorner, InnerCorner)
        }
    }

    fun itemShape(index: Int, itemCount: Int): Shape {
        val corners = itemCorners(index, itemCount)
        return RoundedCornerShape(
            topStart = corners.topStart,
            topEnd = corners.topEnd,
            bottomStart = corners.bottomStart,
            bottomEnd = corners.bottomEnd,
        )
    }

    /**
     * Gmail changes a selected middle row into an independent rounded surface. BondMail keeps that
     * geometry but morphs the corners rather than snapping them, then returns to the row's original
     * first/middle/last shape when selection is cleared.
     */
    @Composable
    fun animatedItemShape(
        index: Int,
        itemCount: Int,
        selected: Boolean,
    ): Shape {
        val base = itemCorners(index, itemCount)
        val target = if (selected) {
            ItemCorners(SelectedCorner, SelectedCorner, SelectedCorner, SelectedCorner)
        } else {
            base
        }
        val duration = if (bondMotionEnabled()) 180 else 0
        val spec = tween<Dp>(
            durationMillis = duration,
            easing = BondMotionEasing.Standard,
        )
        val topStart by animateDpAsState(target.topStart, spec, label = "group-top-start")
        val topEnd by animateDpAsState(target.topEnd, spec, label = "group-top-end")
        val bottomStart by animateDpAsState(target.bottomStart, spec, label = "group-bottom-start")
        val bottomEnd by animateDpAsState(target.bottomEnd, spec, label = "group-bottom-end")
        return RoundedCornerShape(
            topStart = topStart,
            topEnd = topEnd,
            bottomStart = bottomStart,
            bottomEnd = bottomEnd,
        )
    }
}
