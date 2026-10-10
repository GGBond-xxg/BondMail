package com.bond.mail.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.room.InvalidationTracker
import androidx.work.*
import com.bond.mail.MailApplication
import com.bond.mail.NewMailNotificationMode
import com.bond.mail.ui.i18n.loadJsonStrings
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

open class MailWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = WidgetUpdates.enqueue(context)
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) = WidgetUpdates.enqueue(context)
    override fun onDeleted(context: Context, ids: IntArray) { ids.forEach { WidgetStore(context).delete(it) } }
    override fun onRestored(context: Context, oldIds: IntArray, newIds: IntArray) {
        val store = WidgetStore(context)
        oldIds.zip(newIds).forEach { (old, new) -> store.get(old)?.let { store.save(new, it) }; store.delete(old) }
        WidgetUpdates.enqueue(context)
    }
}
class SmallMailWidget : MailWidgetProvider()
class MediumMailWidget : MailWidgetProvider()
class LargeMailWidget : MailWidgetProvider()

object WidgetUpdates {
    private val renderMutex = Mutex()
    private val providers = listOf(SmallMailWidget::class.java, MediumMailWidget::class.java, LargeMailWidget::class.java)
    fun ids(context: Context): IntArray = providers.flatMap { AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, it)).asList() }.toIntArray()
    fun owns(context: Context, id: Int): Boolean = id in ids(context)
    fun enqueue(context: Context) {
        if (ids(context).isEmpty()) return
        // Appending prevents an update racing the end of a running renderer from being dropped.
        WorkManager.getInstance(context).enqueueUniqueWork("mail_widget_render", ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<WidgetRenderWorker>().build())
    }
    fun observe(application: MailApplication, scope: CoroutineScope) {
        val changes = Channel<Unit>(Channel.CONFLATED)
        application.container.database.invalidationTracker.addObserver(object : InvalidationTracker.Observer("messages", "accounts") {
            override fun onInvalidated(tables: Set<String>) { changes.trySend(Unit) }
        })
        application.getSystemService(android.net.ConnectivityManager::class.java).registerDefaultNetworkCallback(
            object : android.net.ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: android.net.Network) { changes.trySend(Unit) }
                override fun onLost(network: android.net.Network) { changes.trySend(Unit) }
            })
        scope.launch {
            for (signal in changes) {
                delay(400)
                while (changes.tryReceive().isSuccess) { /* Coalesce a sync batch without starving the desktop. */ }
                enqueue(application)
            }
        }
        scope.launch {
            application.container.settings.settings.map { it.languageCode }.distinctUntilChanged().collect { enqueue(application) }
        }
    }
    suspend fun save(context: Context, id: Int, config: WidgetConfig) {
        renderMutex.withLock {
            WidgetStore(context).save(id, config)
            // Clear the previous account/content immediately, including if the following read fails.
            val settings = (context.applicationContext as MailApplication).container.settings.settings.first()
            val manager = AppWidgetManager.getInstance(context)
            manager.updateAppWidget(id, WidgetRenderer(context, loadJsonStrings(context, settings.languageCode))
                .responsive(id, config, WidgetSnapshot(readError = true), manager.getAppWidgetOptions(id)))
        }
        updateAll(context)
    }
    suspend fun updateAll(context: Context) = renderMutex.withLock {
        val container = (context.applicationContext as MailApplication).container
        val settings = container.settings.settings.first()
        val renderer = WidgetRenderer(context, loadJsonStrings(context, settings.languageCode))
        val store = WidgetStore(context)
        val manager = AppWidgetManager.getInstance(context)
        for (id in ids(context)) {
            val config = store.get(id)
            val data = try { config?.let { widgetSnapshot(container.database, it) } ?: WidgetSnapshot() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { WidgetSnapshot(readError = true) }
            // A privacy/account edit during the Room read must not publish the obsolete payload.
            if (store.get(id) != config || !owns(context, id)) continue
            manager.updateAppWidget(id, renderer.responsive(id, config, data, manager.getAppWidgetOptions(id)))
        }
    }
}

class WidgetRenderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        WidgetUpdates.updateAll(applicationContext); Result.success()
    } catch (cancelled: CancellationException) { throw cancelled }
      catch (_: Exception) { if (runAttemptCount < 2) Result.retry() else Result.failure() }
}

class WidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "widget.refresh") { WidgetUpdates.enqueue(context); return }
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
        if (!WidgetUpdates.owns(context, id)) return
        val config = WidgetStore(context).get(id) ?: return
        if (!config.refresh) return
        WidgetStore(context).setRefreshing(config.accountId, true)
        WidgetUpdates.enqueue(context)
        // Existing repository account mutex also serializes this with foreground/FCM sync.
        WorkManager.getInstance(context).enqueueUniqueWork("mail_widget_sync_${config.accountId}", ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<WidgetSyncWorker>()
                .setInputData(workDataOf("account" to config.accountId))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build())
    }
}

class WidgetSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getString("account") ?: return Result.failure()
        val container = (applicationContext as MailApplication).container
        val configured = WidgetUpdates.ids(applicationContext).any { WidgetStore(applicationContext).get(it)?.accountId == id }
        if (!configured || container.database.accountDao().byId(id)?.enabled != true) {
            WidgetUpdates.updateAll(applicationContext); return Result.success()
        }
        WidgetStore(applicationContext).setRefreshing(id, true)
        WidgetUpdates.updateAll(applicationContext)
        return try {
            container.syncAccountAndNotify(id, NewMailNotificationMode.CONSUME_SILENTLY)
            WidgetUpdates.updateAll(applicationContext)
            Result.success()
        } catch (cancelled: CancellationException) { throw cancelled }
          catch (_: Exception) {
            WidgetUpdates.enqueue(applicationContext)
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        } finally {
            WidgetStore(applicationContext).setRefreshing(id, false)
            WidgetUpdates.enqueue(applicationContext)
        }
    }
}

data class WidgetOpenRequest(val widgetId: Int, val action: String, val messageId: String, val sequence: Long)
