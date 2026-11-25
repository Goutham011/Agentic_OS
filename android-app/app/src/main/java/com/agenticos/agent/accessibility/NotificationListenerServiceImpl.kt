package com.agenticos.agent.accessibility

import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.agenticos.agent.utils.NotificationEvent
import kotlinx.serialization.encodeToString

class NotificationListenerServiceImpl : NotificationListenerService() {
    companion object {
        private const val TAG = "NotifListener"
        const val ACTION_NOTIFICATION_POSTED = "com.agenticos.agent.NOTIFICATION_POSTED"
        const val ACTION_NOTIFICATION_REMOVED = "com.agenticos.agent.NOTIFICATION_REMOVED"
        const val EXTRA_NOTIFICATION = "notification_event"
        
        // Apps to ignore (sensitive)
        private val ignoredPackages = setOf(
            "com.android.chrome", // browser passwords
            "com.google.android.gms.auth", // auth
            "com.android.settings", // settings
            "com.google.android.gms" // play services
        )
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "NotificationListenerServiceImpl created")
    }
    
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn?.let { notification ->
            val pkg = notification.packageName
            
            // Skip ignored packages
            if (ignoredPackages.contains(pkg)) return
            
            val notificationEvent = extractNotificationEvent(notification)
            
            // Broadcast to agent
            val intent = Intent(ACTION_NOTIFICATION_POSTED).apply {
                putExtra(EXTRA_NOTIFICATION, encodeToString(notificationEvent))
            }
            LocalBroadcastManager.getInstance(this@NotificationListenerServiceImpl).sendBroadcast(intent)
            
            Log.d(TAG, "Notification posted: $pkg - ${notificationEvent.title}")
        }
    }
    
    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn?.let { notification ->
            val intent = Intent(ACTION_NOTIFICATION_REMOVED).apply {
                putExtra("package", notification.packageName)
                putExtra("tag", notification.tag)
                putExtra("id", notification.id)
            }
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        }
    }
    
    private fun extractNotificationEvent(sbn: StatusBarNotification): NotificationEvent {
        val notification = sbn.notification
        val extras = notification.extras
        
        val title = extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
        val summaryText = extras.getCharSequence(android.app.Notification.EXTRA_SUMMARY_TEXT)?.toString() ?: ""
        
        // Detect call notifications
        val isCall = notification.category == android.app.Notification.CATEGORY_CALL ||
                     notification.category == android.app.Notification.CATEGORY_MISSED_CALL ||
                     text.lowercase().contains("missed call") ||
                     title.lowercase().contains("call")
        
        // Detect SMS
        val isSms = notification.category == android.app.Notification.CATEGORY_MESSAGE ||
                    sbn.packageName == "com.google.android.apps.messaging" ||
                    sbn.packageName.contains("sms")
        
        // Extract caller/sender
        var sender = ""
        if (isCall) {
            // Try to get phone number from extras
            sender = extras.getCharSequence(android.app.Notification.EXTRA_PHONE_NUMBER)?.toString() ?: ""
            if (sender.isBlank()) {
                // Parse from text
                val phoneRegex = "\\+?\\d[\\d\\s-]{8,}".toRegex()
                sender = phoneRegex.find(text)?.value ?: phoneRegex.find(title)?.value ?: ""
            }
        } else if (isSms) {
            sender = extras.getCharSequence(android.app.Notification.EXTRA_MESSAGES)?.toString() ?: ""
            // SMS sender often in title
            if (sender.isBlank()) sender = title
        }
        
        return NotificationEvent(
            packageName = sbn.packageName,
            title = title,
            text = text,
            bigText = bigText,
            summaryText = summaryText,
            isCall = isCall,
            isSms = isSms,
            sender = sender,
            timestamp = sbn.postTime,
            notification = notification // Parcelable for potential reply
        )
    }
    
    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Notification listener connected")
        
        // Request current notifications
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val current = currentNotificationList
            current?.forEach { sbn ->
                onNotificationPosted(sbn)
            }
        }
    }
    
    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d(TAG, "Notification listener disconnected")
    }
}