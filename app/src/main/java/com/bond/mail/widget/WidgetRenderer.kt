package com.bond.mail.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.toArgb
import com.bond.mail.MainActivity
import com.bond.mail.R
import com.bond.mail.data.settings.ThemeMode
import com.bond.mail.data.settings.UiStyle
import com.bond.mail.ui.i18n.JsonStrings
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date

class WidgetRenderer(private val context: Context, private val strings: JsonStrings) {
    private fun text(key: String) = strings.text(key)
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()

    fun responsive(id: Int, config: WidgetConfig?, snapshot: WidgetSnapshot, options: Bundle): RemoteViews {
        if (Build.VERSION.SDK_INT >= 31) {
            val sizes = androidx.core.os.BundleCompat.getParcelableArrayList(options, AppWidgetManager.OPTION_APPWIDGET_SIZES, SizeF::class.java)
                ?.filter { it.width.isFinite() && it.height.isFinite() && it.width > 0 && it.height > 0 }
                ?.distinct()?.take(8)
            if (!sizes.isNullOrEmpty()) return RemoteViews(sizes.associateWith { render(id, config, snapshot, it) })
        }
        val minW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 280).coerceAtLeast(90)
        val maxW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, minW).coerceAtLeast(minW)
        val minH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 160).coerceAtLeast(90)
        val maxH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, minH).coerceAtLeast(minH)
        return RemoteViews(render(id, config, snapshot, SizeF(maxW.toFloat(), minH.toFloat())),
            render(id, config, snapshot, SizeF(minW.toFloat(), maxH.toFloat())))
    }

    fun render(id: Int, config: WidgetConfig?, snapshot: WidgetSnapshot, size: SizeF, interactive: Boolean = true): RemoteViews {
        val c = config ?: WidgetConfig("")
        val dark = when (c.mode) {
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
            ThemeMode.SYSTEM -> context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        }
        val colors = palette(c.style, dark)
        val valid = config != null && snapshot.valid
        val status = when {
            !valid -> text("widget_settings")
            WidgetStore(context).refreshing(c.accountId) -> text("refreshing")
            !isOnline() -> text("widget_offline")
            snapshot.syncError -> text("widget_cached_error")
            else -> ""
        }
        val layout = widgetLayout(size.width, size.height, context.resources.configuration.fontScale,
            c.preview && c.privacy == WidgetPrivacy.NORMAL, status.isNotEmpty())
        val rv = RemoteViews(context.packageName, R.layout.widget_mail)
        fun visible(view: Int, show: Boolean) = rv.setViewVisibility(view, if (show) View.VISIBLE else View.GONE)
        fun label(view: Int, value: String, secondary: Boolean = false) {
            rv.setTextViewText(view, value); rv.setTextColor(view, if (secondary) colors.secondary else colors.foreground)
        }
        rv.setImageViewBitmap(R.id.widget_background, background(size, colors, c.style))
        val title = if (!layout.compact && c.unread && valid) "${text("inbox")}  ${snapshot.unread}" else text("inbox")
        label(R.id.widget_title, title)
        label(R.id.widget_compact_title, text("inbox"))
        label(R.id.widget_account, snapshot.email, true)
        val scale = context.resources.configuration.fontScale.coerceAtLeast(1f)
        visible(R.id.widget_header, !layout.compact || !valid)
        visible(R.id.widget_account, valid && !layout.compact && size.height >= 285)
        val logo = mailIcon(colors, c.style)
        rv.setImageViewBitmap(R.id.widget_logo, logo)
        rv.setImageViewBitmap(R.id.widget_compact_logo, logo)
        visible(R.id.widget_compact, layout.compact && valid)
        visible(R.id.widget_rows, !layout.compact && valid && snapshot.rows.isNotEmpty() && layout.rows > 0)
        visible(R.id.widget_empty, !valid || (!layout.compact && (snapshot.rows.isEmpty() || layout.rows == 0)))
        label(R.id.widget_empty, when {
            snapshot.readError -> text("widget_read_error")
            config == null -> text("widget_tap_configure")
            !snapshot.valid -> text("widget_account_missing")
            snapshot.rows.isEmpty() -> text("widget_empty")
            else -> text("widget_open_inbox")
        }, true)
        label(R.id.widget_count, snapshot.unread.toString())
        label(R.id.widget_unread_label, text("widget_unread"), true)
        visible(R.id.widget_count, c.unread)
        val showStatus = status.isNotEmpty() && (!layout.compact || size.height >= 140)
        visible(R.id.widget_status, showStatus)
        label(R.id.widget_status, status, true)
        val showSmallLogo = size.height >= 145 * scale
        visible(R.id.widget_compact_logo, showSmallLogo)
        val showUnreadLabel = c.unread && size.height >= 120 * scale
        visible(R.id.widget_unread_label, showUnreadLabel)
        if (layout.compact) {
            val budget = size.height - 28 - 22 * scale - (if (showSmallLogo) 44 else 0) -
                (if (showStatus) 16 * scale else 0f) - (if (showUnreadLabel) 16 * scale else 0f)
            rv.setTextViewTextSize(R.id.widget_count, android.util.TypedValue.COMPLEX_UNIT_SP,
                (budget / (1.25f * scale)).coerceIn(16f, 28f))
        }
        visible(R.id.widget_compose, valid && !layout.compact && c.compose)
        val smallCompose = valid && layout.compact && c.compose && size.width >= 120 && size.height >= 145 && scale < 1.5f
        visible(R.id.widget_compact_compose, smallCompose)
        // Reserve room for even a large unread total beside the compact-card action.
        rv.setViewPadding(R.id.widget_count, 0, 0, if (smallCompose) dp(50) else 0, 0)
        rv.setViewPadding(R.id.widget_unread_label, 0, 0, if (smallCompose) dp(50) else 0, 0)
        visible(R.id.widget_refresh, valid && !layout.compact && c.refresh && size.width >= 250 && size.height >= 285)
        for (view in listOf(R.id.widget_compose, R.id.widget_compact_compose, R.id.widget_refresh)) {
            rv.setImageViewBitmap(view, actionIcon(colors, c.style, when(view) {
                R.id.widget_refresh -> "refresh"
                R.id.widget_compact_compose -> "plus"
                else -> "compose"
            }))
            rv.setContentDescription(view, text(if (view == R.id.widget_refresh) "widget_show_refresh" else "compose_mail"))
        }
        val titleDescription = listOf(snapshot.accountName, text("inbox"),
            if (valid && c.unread) "${snapshot.unread} ${text("widget_unread")}" else "").filter { it.isNotBlank() }.joinToString(", ")
        rv.setContentDescription(R.id.widget_title, titleDescription)
        rv.setContentDescription(R.id.widget_compact_title, titleDescription)
        rv.removeAllViews(R.id.widget_rows)
        if (valid && !layout.compact) snapshot.rows.take(layout.rows).forEach { source ->
            val mail = source.copy(
                sender = source.sender.takeIf { c.sender }.orEmpty(),
                senderAddress = source.senderAddress.takeIf { c.sender }.orEmpty(),
                subject = source.subject.takeIf { c.subject && c.privacy != WidgetPrivacy.HIDE_CONTENT }.orEmpty(),
                preview = source.preview.takeIf { c.preview && c.privacy == WidgetPrivacy.NORMAL }.orEmpty(),
                time = source.time.takeIf { c.time },
            )
            val row = RemoteViews(context.packageName, R.layout.widget_mail_row)
            row.setInt(R.id.widget_row_body, "setMinimumHeight", dp(layout.rowHeight))
            if (Build.VERSION.SDK_INT >= 31) row.setViewLayoutHeight(R.id.widget_row_body, layout.rowHeight.toFloat(), android.util.TypedValue.COMPLEX_UNIT_DIP)
            else row.setInt(R.id.widget_row_body, "setMinimumHeight", dp(layout.rowHeight))
            val sender = mail.sender.ifBlank { if (mail.unread) text("widget_unread_message") else text("widget_message") }
            row.setTextViewText(R.id.widget_sender, sender)
            row.setTextViewText(R.id.widget_subject, mail.subject)
            row.setTextViewText(R.id.widget_preview, mail.preview)
            row.setTextViewText(R.id.widget_time, mail.time?.let(::formatTime).orEmpty())
            row.setImageViewBitmap(R.id.widget_avatar, avatar(colors, c.style, mail, size.height >= 285))
            row.setViewVisibility(R.id.widget_subject, if (mail.subject.isNotBlank()) View.VISIBLE else View.GONE)
            row.setViewVisibility(R.id.widget_preview, if (layout.summary && mail.preview.isNotBlank()) View.VISIBLE else View.GONE)
            row.setViewVisibility(R.id.widget_time, if (mail.time != null && size.width >= 260) View.VISIBLE else View.GONE)
            row.setTextColor(R.id.widget_sender, colors.foreground)
            row.setTextColor(R.id.widget_subject, colors.secondary)
            row.setTextColor(R.id.widget_preview, colors.secondary)
            row.setTextColor(R.id.widget_time, colors.secondary)
            row.setInt(R.id.widget_divider, "setBackgroundColor", if (c.style == UiStyle.MIUIX) colors.divider else 0)
            // Typeface cannot be set through RemoteViews. A bold span survives parceling on all supported Android versions.
            if (mail.unread) row.setTextViewText(R.id.widget_sender, android.text.SpannableString(sender).apply {
                setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, length, 0)
            })
            row.setContentDescription(R.id.widget_row, listOf(if (mail.unread) text("widget_unread_message") else text("widget_message"),
                mail.sender, mail.subject, if (layout.summary) mail.preview else "", mail.time?.let(::formatTime).orEmpty()).filter { it.isNotBlank() }.joinToString(", "))
            if (interactive) row.setOnClickPendingIntent(R.id.widget_row, open(id, "detail", mail.id))
            rv.addView(R.id.widget_rows, row)
        }
        if (interactive) {
            rv.setOnClickPendingIntent(R.id.widget_root, if (valid) open(id, "inbox") else configure(id))
            rv.setOnClickPendingIntent(R.id.widget_status, configure(id))
            rv.setContentDescription(R.id.widget_status, status + ", " + text("widget_settings"))
            rv.setOnClickPendingIntent(R.id.widget_compose, open(id, "compose"))
            rv.setOnClickPendingIntent(R.id.widget_compact_compose, open(id, "compose"))
            val refreshIntent = Intent(context, WidgetActionReceiver::class.java).setAction("widget.refresh")
                .setData(Uri.parse("bondmail-widget://refresh/$id")).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            rv.setOnClickPendingIntent(R.id.widget_refresh, PendingIntent.getBroadcast(context, 0, refreshIntent, flags))
        }
        return rv
    }

    private fun isOnline(): Boolean = runCatching {
        val manager = context.getSystemService(android.net.ConnectivityManager::class.java)
        manager.getNetworkCapabilities(manager.activeNetwork)?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }.getOrDefault(true)

    private fun formatTime(time: Long): String {
        val format = if (android.text.format.DateUtils.isToday(time)) DateFormat.getTimeInstance(DateFormat.SHORT, strings.locale)
            else SimpleDateFormat("MM-dd", strings.locale)
        return format.format(Date(time))
    }
    private val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    private fun open(id: Int, action: String, messageId: String = ""): PendingIntent = PendingIntent.getActivity(context, 0,
        Intent(context, MainActivity::class.java).setAction("com.bond.mail.WIDGET_OPEN")
            .setData(Uri.Builder().scheme("bondmail-widget").authority(action).appendPath(id.toString()).appendPath(messageId).build())
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id).putExtra("widget_action", action).putExtra("widget_message", messageId), flags)
    private fun configure(id: Int): PendingIntent = PendingIntent.getActivity(context, 0,
        Intent(context, WidgetConfigActivity::class.java).setData(Uri.parse("bondmail-widget://configure/$id"))
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id), flags)

    private data class Palette(val background: Int, val foreground: Int, val secondary: Int, val accent: Int, val divider: Int, val gradient: Int)
    private fun palette(style: UiStyle, dark: Boolean): Palette {
        val scheme = if (Build.VERSION.SDK_INT >= 31) {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else if (dark) darkColorScheme() else lightColorScheme()
        if (style == UiStyle.MATERIAL3) return Palette(scheme.surfaceContainer.toArgb(), scheme.onSurface.toArgb(), scheme.onSurfaceVariant.toArgb(), scheme.primary.toArgb(), scheme.outlineVariant.toArgb(), scheme.surfaceContainer.toArgb())
        if (style == UiStyle.LIQUID_GLASS) return if (dark) Palette(0x44141414, 0xFFFFFFFF.toInt(), 0xE6FFFFFF.toInt(), 0xFFFFFFFF.toInt(), 0x26FFFFFF, 0x60141414)
            else Palette(0x88FFFFFF.toInt(), 0xFF142032.toInt(), 0xFF344256.toInt(), 0xFF142032.toInt(), 0x33FFFFFF, 0x66FFFFFF)
        return if (dark) Palette(0xFF21252D.toInt(), 0xFFF5F6FA.toInt(), 0xFFC2C8D2.toInt(), 0xFF7CB7FF.toInt(), 0xFF353B46.toInt(), 0xFF21252D.toInt())
            else Palette(0xFFFCFDFF.toInt(), 0xFF181C24.toInt(), 0xFF526078.toInt(), 0xFF0969DD.toInt(), 0xFFE7EDF5.toInt(), 0xFFFCFDFF.toInt())
    }

    private fun actionIcon(p: Palette, style: UiStyle, action: String): Bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888).apply {
        val canvas = Canvas(this).apply { scale(2f, 2f) }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val primary = action == "compose" && style == UiStyle.MIUIX
        paint.color = when {
            style == UiStyle.LIQUID_GLASS -> 0x28FFFFFF
            primary -> 0xFF2478FF.toInt()
            else -> (p.accent and 0x00FFFFFF) or 0x20000000
        }
        val radius = if (action == "plus") 20f else 16f
        canvas.drawCircle(24f, 24f, radius, paint)
        if (style == UiStyle.LIQUID_GLASS) {
            paint.style = Paint.Style.STROKE; paint.strokeWidth = .8f; paint.color = 0x99FFFFFF.toInt()
            canvas.drawCircle(24f, 24f, radius, paint)
        }
        paint.shader = null; paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.8f
        paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND
        paint.color = if (primary) android.graphics.Color.WHITE else p.accent
        when (action) {
            "plus" -> { canvas.drawLine(17f,24f,31f,24f,paint); canvas.drawLine(24f,17f,24f,31f,paint) }
            "refresh" -> {
                canvas.drawArc(RectF(18f,18f,30f,30f), 30f, 295f, false, paint)
                canvas.drawPath(android.graphics.Path().apply { moveTo(30f,17f); lineTo(30f,22f); lineTo(25f,22f) },paint)
            }
            else -> {
                canvas.drawPath(android.graphics.Path().apply {
                    moveTo(18f,26f); lineTo(27f,17f); lineTo(31f,21f); lineTo(22f,30f); lineTo(17f,31f); close()
                    moveTo(25f,19f); lineTo(29f,23f)
                }, paint)
            }
        }
    }

    private fun mailIcon(p: Palette, style: UiStyle): Bitmap = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888).apply {
        val canvas = Canvas(this).apply { scale(2f,2f) }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        if (style != UiStyle.MATERIAL3) {
            paint.shader = LinearGradient(0f,0f,40f,40f, if (style == UiStyle.LIQUID_GLASS) 0x997BCFFF.toInt() else 0xFF479EFF.toInt(),
                if (style == UiStyle.LIQUID_GLASS) 0x66377BDF else 0xFF245AFF.toInt(), Shader.TileMode.CLAMP)
            canvas.drawRoundRect(RectF(1f,1f,39f,39f),9f,9f,paint)
            paint.shader = null
            if (style == UiStyle.LIQUID_GLASS) {
                paint.color = 0xAAFFFFFF.toInt(); paint.strokeWidth = .8f; paint.style = Paint.Style.STROKE
                canvas.drawRoundRect(RectF(1f,1f,39f,39f),9f,9f,paint)
            }
        }
        paint.color = if (style == UiStyle.MATERIAL3) p.accent else android.graphics.Color.WHITE
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f; paint.strokeJoin = Paint.Join.ROUND
        canvas.drawRoundRect(RectF(7f,10f,33f,30f),3f,3f,paint)
        canvas.drawPath(android.graphics.Path().apply { moveTo(8f,12f); lineTo(20f,22f); lineTo(32f,12f) },paint)
    }

    private fun avatar(p: Palette, style: UiStyle, mail: WidgetMail, large: Boolean): Bitmap = Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888).apply {
        val canvas = Canvas(this)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val showIdentity = mail.sender.isNotBlank()
        if (!large && style == UiStyle.MIUIX || !showIdentity) {
            paint.color = if (mail.unread) p.accent else p.divider
            canvas.drawCircle(32f,32f,7f,paint)
            return@apply
        }
        val tones = intArrayOf(0xFFDDD5FA.toInt(),0xFFD2EAD9.toInt(),0xFFF8D5E4.toInt(),0xFFD5E5FC.toInt(),0xFFF4E7C8.toInt())
        paint.color = when(style) {
            UiStyle.MATERIAL3 -> tones[(mail.sender.hashCode() and Int.MAX_VALUE) % tones.size]
            UiStyle.LIQUID_GLASS -> 0x55FFFFFF
            else -> (p.accent and 0xFFFFFF) or 0x18000000
        }
        canvas.drawCircle(32f,32f,29f,paint)
        val ink = if (style == UiStyle.MATERIAL3) 0xFF302B42.toInt() else p.foreground
        val artwork = com.bond.mail.ui.components.contactLogoBitmap(context, mail.sender, mail.senderAddress, ink, 36)
        paint.alpha = 255
        if (artwork != null) canvas.drawBitmap(artwork,14f,14f,paint)
        else {
            paint.color = ink; paint.textAlign = Paint.Align.CENTER; paint.textSize = 30f
            canvas.drawText(mail.sender.firstOrNull()?.uppercase().orEmpty(),32f,32f-(paint.ascent()+paint.descent())/2,paint)
        }
        if (mail.unread) {
            paint.color = p.accent
            canvas.drawCircle(6f,6f,5f,paint)
        }
    }

    private fun background(size: SizeF, p: Palette, style: UiStyle): Bitmap {
        // Bound the binder payload even on large launcher grids; background contains no mail data.
        val ratio = minOf(1f, 400f / maxOf(size.width, size.height))
        val w = (size.width * ratio).toInt().coerceIn(1, 400)
        val h = (size.height * ratio).toInt().coerceIn(1, 400)
        val key = "$w:$h:$p:$style"
        synchronized(backgrounds) { backgrounds.get(key)?.let { return it } }
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            val canvas = Canvas(this)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), p.background, p.gradient, Shader.TileMode.CLAMP)
            val radius = (if (style == UiStyle.MIUIX) 22 else if (style == UiStyle.LIQUID_GLASS) 24 else 28) * ratio
            val rect = RectF(1f, 1f, w - 1f, h - 1f)
            canvas.drawRoundRect(rect, radius, radius, paint)
            if (style == UiStyle.LIQUID_GLASS) {
                // Neutral alpha only: the launcher wallpaper stays visible through this surface.
                // No synthetic wallpaper, colored spotlight or screenshot is baked into the bitmap.
                paint.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), 0xCCFFFFFF.toInt(), 0x40FFFFFF, Shader.TileMode.CLAMP)
                paint.style = Paint.Style.STROKE; paint.strokeWidth = .8f * ratio
                canvas.drawRoundRect(rect, radius, radius, paint)
            }
            synchronized(backgrounds) { backgrounds.put(key, this) }
        }
    }
    companion object {
        private val backgrounds = object : android.util.LruCache<String, Bitmap>(4 * 1024 * 1024) {
            override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
        }
    }
}
