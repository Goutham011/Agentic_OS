package com.agenticos.agent.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.agenticos.agent.models.ToolResult
import com.agenticos.agent.utils.JsonProvider
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

class ScreenReaderAccessibilityService : AccessibilityService() {
    companion object {
        private const val TAG = "ScreenReaderService"
        const val ACTION_SCREEN_CHANGED = "com.agenticos.agent.SCREEN_CHANGED"
        const val EXTRA_SCREEN_CONTENT = "screen_content"
        const val EXTRA_PACKAGE_NAME = "package_name"
        const val EXTRA_EVENT_TYPE = "event_type"
        
        private val contentQueue = ArrayDeque<ScreenContent>(10)
        private const val MAX_QUEUE_SIZE = 10
        
        // FIX #7: Add depth limit to prevent stack overflow from unbounded recursion
        private const val MAX_DEPTH = 50
        
        // Notification timeout in milliseconds
        private const val NOTIFICATION_TIMEOUT_MS = 100
        
        fun getLatestContent(): ScreenContent? = contentQueue.lastOrNull()
        fun getAllContent(): List<ScreenContent> = contentQueue.toList()
    }
    
    private var lastPackageName = ""
    
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
                contentQueue.addLast(screenContent)
                while (contentQueue.size > MAX_QUEUE_SIZE) {
                    contentQueue.removeFirst()
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
        
        // FIX #7: Use iterative traversal with depth limit instead of unbounded recursion
        traverseNodeIterative(root, textBuilder, clickableElements, editTexts)
        
        return ScreenContent(
            packageName = packageName,
            fullText = textBuilder.toString().trim(),
            clickableElements = clickableElements,
            editTexts = editTexts,
            timestamp = System.currentTimeMillis(),
            eventType = eventType
        )
    }
    
    // FIX #7: Iterative breadth-first traversal with depth limit to prevent stack overflow
    private fun traverseNodeIterative(
        root: AccessibilityNodeInfo,
        textBuilder: StringBuilder,
        clickableElements: MutableList<ClickableElement>,
        editTexts: MutableList<EditTextElement>
    ) {
        // Use a queue for BFS: Pair(node, depth)
        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        queue.add(root to 0)
        
        val visited = mutableSetOf<Int>() // Track node identity hashes to avoid cycles
        
        while (queue.isNotEmpty()) {
            val (node, depth) = queue.removeFirst()
            
            // Skip if too deep or already visited (detect cycles)
            if (depth > MAX_DEPTH || visited.contains(node.hashCode())) {
                continue
            }
            visited.add(node.hashCode())
            
            // Get text
            val text = node.text?.toString()?.trim()
            if (!text.isNullOrBlank()) {
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
            
            // Add children to queue (reverse order to maintain left-to-right traversal)
            for (i in node.childCount - 1 downTo 0) {
                node.getChild(i)?.let { child ->
                    queue.add(child to depth + 1)
                }
            }
        }
    }
    
    // Keep original recursive method for simple single-level use cases
    private fun traverseNode(
        node: AccessibilityNodeInfo,
        textBuilder: StringBuilder,
        clickableElements: MutableList<ClickableElement>,
        editTexts: MutableList<EditTextElement>,
        depth: Int = 0
    ) {
        if (depth > MAX_DEPTH) return
        
        // Get text
        val text = node.text?.toString()?.trim()
        if (!text.isNullOrBlank()) {
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
        
        // Recurse children with depth limit
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                traverseNode(child, textBuilder, clickableElements, editTexts, depth + 1)
            }
        }
    }
    
    private fun broadcastScreenChange(content: ScreenContent) {
        val json = JsonProvider.json.encodeToString(ScreenContent.serializer(), content)
        val intent = Intent(ACTION_SCREEN_CHANGED).apply {
            putExtra(EXTRA_SCREEN_CONTENT, json)
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
            notificationTimeout = NOTIFICATION_TIMEOUT_MS.toLong()
        }
        setServiceInfo(info)
        Log.d(TAG, "Accessibility service connected")
    }
    
    /** Perform click on element matching description */
    fun performClick(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        // FIX #7: Use iterative search with depth limit
        return findAndClickIterative(root, text)
    }
    
    // FIX #7: Iterative search to prevent stack overflow
    private fun findAndClickIterative(root: AccessibilityNodeInfo, targetText: String): Boolean {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        val visited = mutableSetOf<Int>()
        var depth = 0
        
        while (queue.isNotEmpty() && depth <= MAX_DEPTH) {
            val node = queue.removeFirst()
            
            if (visited.contains(node.hashCode())) continue
            visited.add(node.hashCode())
            
            val nodeText = node.text?.toString()?.lowercase() ?: ""
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""
            val target = targetText.lowercase()
            
            if (nodeText.contains(target) || desc.contains(target)) {
                if (node.isClickable) {
                    return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }
            }
            
            // Add children to queue
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { child ->
                    queue.add(child)
                }
            }
            
            // Track depth (approximate based on queue size)
            if (queue.isEmpty()) depth++
        }
        return false
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
        return findAndSetText(root, text, 0)
    }
    
    private fun findAndSetText(node: AccessibilityNodeInfo, text: String, depth: Int): Boolean {
        if (depth > MAX_DEPTH) return false
        
        if (node.isEditable && node.isFocused) {
            return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, android.os.Bundle().apply { 
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) 
            })
        }
        
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                if (findAndSetText(child, text, depth + 1)) return true
            }
        }
        return false
    }
    
    /** Scroll in direction */
    fun scroll(direction: Int): Boolean {
        val root = rootInActiveWindow ?: return false
        return findAndScroll(root, direction, 0)
    }
    
    private fun findAndScroll(node: AccessibilityNodeInfo, direction: Int, depth: Int): Boolean {
        if (depth > MAX_DEPTH) return false
        
        if (node.isScrollable) {
            val action = when (direction) {
                1 -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                -1 -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                else -> return false
            }
            return node.performAction(action)
        }
        
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                if (findAndScroll(child, direction, depth + 1)) return true
            }
        }
        return false
    }
}

@Serializable
data class ScreenContent(
    val packageName: String,
    val fullText: String,
    val clickableElements: List<ClickableElement>,
    val editTexts: List<EditTextElement>,
    val timestamp: Long,
    val eventType: Int
)

@Serializable
data class ClickableElement(
    val className: String,
    val text: String,
    val contentDescription: String,
    @Contextual val bounds: android.graphics.Rect,
    val clickable: Boolean,
    val longClickable: Boolean,
    val enabled: Boolean
)

@Serializable
data class EditTextElement(
    val className: String,
    val hint: String,
    val text: String,
    @Contextual val bounds: android.graphics.Rect,
    val focused: Boolean
)