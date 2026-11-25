package com.agenticos.agent.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.agenticos.agent.utils.ScreenContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue

class ScreenReaderAccessibilityService : AccessibilityService() {
    companion object {
        private const val TAG = "ScreenReaderService"
        const val ACTION_SCREEN_CHANGED = "com.agenticos.agent.SCREEN_CHANGED"
        const val EXTRA_SCREEN_CONTENT = "screen_content"
        const val EXTRA_PACKAGE_NAME = "package_name"
        const val EXTRA_EVENT_TYPE = "event_type"
        
        // Queue for latest screen content
        private val contentQueue = ConcurrentLinkedQueue<ScreenContent>()
        private const val MAX_QUEUE_SIZE = 10
    }
    
    private var lastPackageName = ""
    private val handler = Handler(Looper.getMainLooper())
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "ScreenReaderAccessibilityService created")
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event?.let { e ->
            val packageName = e.packageName?.toString() ?: ""
            lastPackageName = packageName
            
            // Extract screen content based on event type
            val content = when (e.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                AccessibilityEvent.TYPE_VIEW_CLICKED,
                AccessibilityEvent.TYPE_VIEW_SCROLLED,
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                    extractScreenContent(rootInActiveWindow, packageName, e.eventType)
                }
                else -> null
            }
            
            content?.let { screenContent ->
                // Queue the content
                contentQueue.add(screenContent)
                while (contentQueue.size > MAX_QUEUE_SIZE) {
                    contentQueue.poll()
                }
                
                // Broadcast to agent
                broadcastScreenChange(screenContent)
            }
        }
    }
    
    private fun extractScreenContent(root: AccessibilityNodeInfo?, packageName: String, eventType: Int): ScreenContent? {
        root ?: return null
        
        val textBuilder = StringBuilder()
        val clickableElements = mutableListOf<ClickableElement>()
        val editTexts = mutableListOf<EditTextElement>()
        
        traverseNode(root, textBuilder, clickableElements, editTexts)
        
        return ScreenContent(
            packageName = packageName,
            fullText = textBuilder.toString().trim(),
            clickableElements = clickableElements,
            editTexts = editTexts,
            timestamp = System.currentTimeMillis(),
            eventType = eventType
        )
    }
    
    private fun traverseNode(
        node: AccessibilityNodeInfo,
        textBuilder: StringBuilder,
        clickableElements: MutableList<ClickableElement>,
        editTexts: MutableList<EditTextElement>
    ) {
        // Get text
        val text = node.text?.toString()?.trim()
        if (text.isNotBlank()) {
            textBuilder.append(text).append(" ")
        }
        
        // Check if clickable
        if (node.isClickable || node.isLongClickable || node.isFocusable) {
            val className = node.className?.toString() ?: ""
            val bounds = android.graphics.Rect()
            node.getBoundsInScreen(bounds)
            clickableElements.add(ClickableElement(
                className = className,
                text = text ?: "",
                contentDescription = node.contentDescription?.toString() ?: "",
                bounds = bounds,
                clickable = node.isClickable,
                longClickable = node.isLongClickable,
                enabled = node.isEnabled
            ))
        }
        
        // Check if editable
        if (node.isEditable) {
            val bounds = android.graphics.Rect()
            node.getBoundsInScreen(bounds)
            editTexts.add(EditTextElement(
                className = node.className?.toString() ?: "",
                hint = node.hintText?.toString() ?: "",
                text = text ?: "",
                bounds = bounds,
                focused = node.isFocused
            ))
        }
        
        // Recurse children
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                traverseNode(child, textBuilder, clickableElements, editTexts)
            }
        }
    }
    
    private fun broadcastScreenChange(content: ScreenContent) {
        val intent = Intent(ACTION_SCREEN_CHANGED).apply {
            putExtra(EXTRA_SCREEN_CONTENT, kotlinx.serialization.encodeToString(content))
            putExtra(EXTRA_PACKAGE_NAME, content.packageName)
            putExtra(EXTRA_EVENT_TYPE, content.eventType)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }
    
    override fun onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted")
    }
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 100
        }
        setServiceInfo(info)
        Log.d(TAG, "Accessibility service connected")
    }
    
    // ===== ACTION EXECUTION =====
    
    /** Perform click on element matching description */
    fun performClick(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        return findAndClick(root, text)
    }
    
    private fun findAndClick(node: AccessibilityNodeInfo, targetText: String): Boolean {
        val nodeText = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        val target = targetText.lowercase()
        
        if (nodeText.contains(target) || desc.contains(target)) {
            if (node.isClickable) {
                return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
        }
        
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                if (findAndClick(child, targetText)) return true
            }
        }
        return false
    }
    
    /** Set text in focused EditText */
    fun setText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        return findAndSetText(root, text)
    }
    
    private fun findAndSetText(node: AccessibilityNodeInfo, text: String): Boolean {
        if (node.isEditable && node.isFocused) {
            return node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION) // focus
                && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O
                && node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply { putCharSequence("ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE", text) })
        }
        
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                if (findAndSetText(child, text)) return true
            }
        }
        return false
    }
    
    /** Scroll in direction */
    fun scroll(direction: Int): Boolean {
        val root = rootInActiveWindow ?: return false
        return findAndScroll(root, direction)
    }
    
    private fun findAndScroll(node: AccessibilityNodeInfo, direction: Int): Boolean {
        if (node.isScrollable) {
            val action = when (direction) {
                1 -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD  // down
                -1 -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD // up
                else -> return false
            }
            return node.performAction(action)
        }
        
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                if (findAndScroll(child, direction)) return true
            }
        }
        return false
    }
    
    /** Get latest screen content */
    companion object {
        fun getLatestContent(): ScreenContent? = contentQueue.peekLast() ?: contentQueue.poll()
        fun getAllContent(): List<ScreenContent> = contentQueue.toList()
    }
}

// ===== DATA CLASSES =====

@kotlinx.serialization.Serializable
data class ScreenContent(
    val packageName: String,
    val fullText: String,
    val clickableElements: List<ClickableElement>,
    val editTexts: List<EditTextElement>,
    val timestamp: Long,
    val eventType: Int
)

@kotlinx.serialization.Serializable
data class ClickableElement(
    val className: String,
    val text: String,
    val contentDescription: String,
    val bounds: android.graphics.Rect,
    val clickable: Boolean,
    val longClickable: Boolean,
    val enabled: Boolean
)

@kotlinx.serialization.Serializable
data class EditTextElement(
    val className: String,
    val hint: String,
    val text: String,
    val bounds: android.graphics.Rect,
    val focused: Boolean
)