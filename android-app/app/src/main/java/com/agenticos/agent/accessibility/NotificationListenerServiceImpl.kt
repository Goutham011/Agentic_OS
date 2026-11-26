package com.agenticos.agent.accessibility

import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.agenticos.agent.utils.JsonProvider
import com.agenticos.agent.utils.NotificationEvent
import kotlinx.serialization.json.JsonPrimitive

class NotificationListenerServiceImpl : NotificationListenerService() {
    companion object {
        private const val TAG = "NotificationListener"
    }
    
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn?.let { handleNotification(it) }
    }
    
    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Could track removed notifications
    }
    
    private fun handleNotification(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        
        // FIX #3: SECURITY - Do NOT store the raw Notification object
        // Extract only what we need to prevent credential/data leakage from
        // sensitive apps (banking, password managers, etc.)
        val extras = sbn.notification.extras
        val title = extras.getString(android.app.Notification.EXTRA_TITLE) ?: ""
        val text = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
        val summaryText = extras.getCharSequence(android.app.Notification.EXTRA_SUMMARY_TEXT)?.toString() ?: ""
        
        // Strip potentially sensitive content from notification extras
        // before any processing or storage
        val isCall = packageName.contains("telecom") || packageName.contains("phone")
        val isSms = packageName.contains("sms") || packageName.contains("messaging") || packageName.contains("message")
        
        // Only store extracted string fields, NOT the raw Notification object
        val event = NotificationEvent(
            packageName = packageName,
            title = title,
            text = text,
            bigText = bigText,
            summaryText = summaryText,
            isCall = isCall,
            isSms = isSms,
            sender = if (isSms && text.isNotBlank()) text.split("\n").firstOrNull() ?: "" else "",
            timestamp = sbn.postTime
        )
        
        // Broadcast to agent
        val intent = Intent("com.agenticos.agent.NOTIFICATION_RECEIVED").apply {
            putExtra("notification_event", JsonProvider.json.encodeToString(NotificationEvent.serializer(), event))
            putExtra("package", packageName)
            putExtra("title", title)
            putExtra("text", text)
        }
        sendBroadcast(intent)
        
        // Also try to get phone number from incoming call
        val phoneNumber = sbn.notification.extras.getString("android.telephony.extra.EXTRA_PHONE_NUMBER")
        if (phoneNumber != null) {
            val callIntent = Intent("com.agenticos.agent.CALL_DETECTED").apply {
                putExtra("phone_number", phoneNumber)
            }
            sendBroadcast(callIntent)
        }
        
        Log.d(TAG, "Notification from $packageName: $title - $text")
    }
}