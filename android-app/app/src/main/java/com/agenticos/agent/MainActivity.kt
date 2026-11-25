package com.agenticos.agent

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.icons.Icons
import androidx.compose.material3.icons.filled.CheckCircle
import androidx.compose.material3.icons.filled.Help
import androidx.compose.material3.icons.filled.Mic
import androidx.compose.material3.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    
    // Permission groups
    private val communicationPermissions = arrayOf(
        Manifest.permission.SEND_SMS,
        Manifest.permission.READ_SMS,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.READ_CONTACTS
    )
    
    private val locationPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_BACKGROUND_LOCATION
    )
    
    private val mediaPermissions = arrayOf(
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.MEDIA_CONTENT_CONTROL
    )
    
    private val systemPermissions = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.CAMERA,
        Manifest.permission.POST_NOTIFICATIONS,
        Manifest.permission.READ_CALENDAR,
        Manifest.permission.SCHEDULE_EXACT_ALARM,
        Manifest.permission.USE_EXACT_ALARM
    )
    
    // All runtime permissions
    private val allRuntimePermissions = communicationPermissions + locationPermissions + mediaPermissions + systemPermissions
    
    // Permission request launchers
    private val requestPermissionsLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        updatePermissionStates()
    }
    
    // Special permission launchers
    private val accessibilityLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ -> updatePermissionStates() }
    private val notificationListenerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ -> updatePermissionStates() }
    private val usageStatsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ -> updatePermissionStates() }
    private val overlayLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ -> updatePermissionStates() }
    private val exactAlarmLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ -> updatePermissionStates() }
    
    // UI State
    private var currentStep by remember { mutableStateOf(0) }
    private var permissionStates by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    private var isChecking by remember { mutableStateOf(false) }
    
    private val steps = listOf(
        OnboardingStep(0, "Welcome", "Welcome to Agentic OS", "Your personal phone assistant that runs entirely on-device. Let's set up permissions so I can help you.", Icons.Default.Mic),
        OnboardingStep(1, "Communication", "Calls, SMS & Contacts", "Send texts, make calls, read messages, and auto-reply to missed calls.", Icons.Default.Chat),
        OnboardingStep(2, "Location", "Location Triggers", "Automate actions when you arrive at work, leave home, or enter geofences.", Icons.Default.LocationOn),
        OnboardingStep(3, "Media & Files", "Photos, Music & Backups", "Play music, backup photos automatically, and control media playback.", Icons.Default.PhotoLibrary),
        OnboardingStep(4, "System", "Alarms, WiFi, Bluetooth & More", "Set alarms, toggle system settings, get notifications, and use voice commands.", Icons.Default.Settings),
        OnboardingStep(5, "Special Access", "Accessibility & Notifications", "Required for screen reading, auto-reply, and notification monitoring. These need manual enable in Settings.", Icons.Default.Security),
        OnboardingStep(6, "Ready", "You're All Set!", "Agentic OS is ready. Tap the mic button or say \"Hey Agent\" to start.", Icons.Default.CheckCircle)
    )
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = { Text("Agentic OS Setup") },
                                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                                )
                            )
                        }
                    ) { padding ->
                        OnboardingScreen(
                            modifier = Modifier.fillMaxSize(),
                            currentStep = currentStep,
                            steps = steps,
                            permissionStates = permissionStates,
                            isChecking = isChecking,
                            onNext = { goToNextStep() },
                            onPrevious = { goToPreviousStep() },
                            onRequestPermissions = { requestRuntimePermissions() },
                            onOpenAccessibility = { openAccessibilitySettings() },
                            onOpenNotificationListener = { openNotificationListenerSettings() },
                            onOpenUsageStats = { openUsageStatsSettings() },
                            onOpenOverlay = { openOverlaySettings() },
                            onOpenExactAlarm = { openExactAlarmSettings() },
                            onStartAssistant = { startAssistant() }
                        )
                    }
                }
            }
        }
        
        updatePermissionStates()
    }
    
    private fun goToNextStep() {
        if (currentStep < steps.lastIndex) {
            currentStep++
            if (currentStep == 1) requestRuntimePermissionsForGroup(communicationPermissions)
            else if (currentStep == 2) requestRuntimePermissionsForGroup(locationPermissions)
            else if (currentStep == 3) requestRuntimePermissionsForGroup(mediaPermissions)
            else if (currentStep == 4) requestRuntimePermissionsForGroup(systemPermissions)
        }
    }
    
    private fun goToPreviousStep() {
        if (currentStep > 0) currentStep--
    }
    
    private fun requestRuntimePermissions() {
        val toRequest = allRuntimePermissions.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (toRequest.isNotEmpty()) {
            requestPermissionsLauncher.launch(toRequest.toTypedArray())
        }
    }
    
    private fun requestRuntimePermissionsForGroup(perms: Array<String>) {
        val toRequest = perms.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (toRequest.isNotEmpty()) {
            requestPermissionsLauncher.launch(toRequest.toTypedArray())
        }
    }
    
    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        accessibilityLauncher.launch(intent)
    }
    
    private fun openNotificationListenerSettings() {
        val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
        notificationListenerLauncher.launch(intent)
    }
    
    private fun openUsageStatsSettings() {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        usageStatsLauncher.launch(intent)
    }
    
    private fun openOverlaySettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            overlayLauncher.launch(intent)
        }
    }
    
    private fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            exactAlarmLauncher.launch(intent)
        }
    }
    
    private fun updatePermissionStates() {
        val states = mutableMapOf<String, Boolean>()
        
        // Runtime permissions
        for (perm in allRuntimePermissions) {
            states[perm] = ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED
        }
        
        // Special permissions
        states["accessibility"] = isAccessibilityServiceEnabled()
        states["notification_listener"] = isNotificationListenerEnabled()
        states["usage_stats"] = isUsageStatsEnabled()
        states["overlay"] = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(this) else true
        states["exact_alarm"] = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 
            (getSystemService(android.app.AlarmManager::class.java)?.canScheduleExactAlarms() == true) else true
        
        permissionStates = states
    }
    
    private fun isAccessibilityServiceEnabled(): Boolean {
        val accessibilityManager = getSystemService(android.view.accessibility.AccessibilityManager::class.java)
        val services = accessibilityManager.getEnabledAccessibilityServiceList(android.view.accessibility.AccessibilityServiceInfo.FEEDBACK_GENERIC)
        return services.any { it.id.contains(packageName) }
    }
    
    private fun isNotificationListenerEnabled(): Boolean {
        val nm = getSystemService(android.service.notification.NotificationManager::class.java)
        val enabledListeners = nm.enabledNotificationListeners
        return enabledListeners?.any { it?.flattenToString()?.contains(packageName) == true } == true
    }
    
    private fun isUsageStatsEnabled(): Boolean {
        val appOps = getSystemService(android.app.AppOpsManager::class.java)
        val mode = appOps.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }
    
    private fun startAssistant() {
        // Start the foreground service
        val intent = Intent(this, AgentForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        
        // Navigate to main UI (could be a different screen)
        // For now, just finish setup
        finish()
    }
}

// ===== DATA CLASS =====

data class OnboardingStep(
    val index: Int,
    val key: String,
    val title: String,
    val description: String,
    val icon: ImageVector
)

// ===== COMPOSABLES =====

@Composable
fun OnboardingScreen(
    modifier: Modifier,
    currentStep: Int,
    steps: List<OnboardingStep>,
    permissionStates: Map<String, Boolean>,
    isChecking: Boolean,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onRequestPermissions: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenNotificationListener: () -> Unit,
    onOpenUsageStats: () -> Unit,
    onOpenOverlay: () -> Unit,
    onOpenExactAlarm: () -> Unit,
    onStartAssistant: () -> Unit
) {
    val step = steps[currentStep]
    val scrollState = rememberScrollState()
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Progress indicator
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            steps.forEachIndexed { index, s ->
                val isActive = index <= currentStep
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .size(40.dp)
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    if (index < currentStep) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = color)
                    } else if (index == currentStep) {
                        Text("${index + 1}", color = color, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    } else {
                        Text("${index + 1}", color = color, fontWeight = FontWeight.Normal, fontSize = 18.sp)
                    }
                }
            }
        }
        
        // Step content
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = 4.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(step.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 16.dp))
                Text(step.title, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 8.dp))
                Text(step.description, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                
                // Permission checklist for steps 1-5
                if (step.index in 1..4) {
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 16.dp))
                    PermissionChecklist(
                        stepIndex = step.index,
                        permissionStates = permissionStates,
                        isChecking = isChecking,
                        onRequest = onRequestPermissions
                    )
                }
                
                // Special permissions for step 5
                if (step.index == 5) {
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 16.dp))
                    SpecialPermissionChecklist(
                        permissionStates = permissionStates,
                        onOpenAccessibility = onOpenAccessibility,
                        onOpenNotificationListener = onOpenNotificationListener,
                        onOpenUsageStats = onOpenUsageStats,
                        onOpenOverlay = onOpenOverlay,
                        onOpenExactAlarm = onOpenExactAlarm
                    )
                }
            }
        }
        
        // Navigation buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (currentStep > 0) {
                Button(onClick = onPrevious) { Text("Back") }
            } else {
                androidx.compose.foundation.layout.Box(modifier = Modifier.weight(1f))
            }
            
            if (currentStep < steps.lastIndex) {
                Button(onClick = onNext, modifier = Modifier.weight(1f, fill = false)) {
                    Text(if (currentStep == steps.lastIndex - 1) "Finish" else "Next")
                }
            } else {
                Button(onClick = onStartAssistant, modifier = Modifier.weight(1f, fill = false), colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )) {
                    Text("Start Assistant")
                }
            }
        }
    }
}

@Composable
fun PermissionChecklist(
    stepIndex: Int,
    permissionStates: Map<String, Boolean>,
    isChecking: Boolean,
    onRequest: () -> Unit
) {
    val perms = when (stepIndex) {
        1 -> arrayOf(
            Manifest.permission.SEND_SMS to "Send SMS",
            Manifest.permission.READ_SMS to "Read SMS",
            Manifest.permission.CALL_PHONE to "Make Calls",
            Manifest.permission.READ_CALL_LOG to "Read Call Log",
            Manifest.permission.READ_CONTACTS to "Read Contacts"
        )
        2 -> arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION to "Precise Location",
            Manifest.permission.ACCESS_BACKGROUND_LOCATION to "Background Location"
        )
        3 -> arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE to "Read Media",
            Manifest.permission.MEDIA_CONTENT_CONTROL to "Control Media"
        )
        4 -> arrayOf(
            Manifest.permission.RECORD_AUDIO to "Microphone",
            Manifest.permission.SCHEDULE_EXACT_ALARM to "Exact Alarms",
            Manifest.permission.POST_NOTIFICATIONS to "Send Notifications"
        )
        else -> emptyArray<Pair<String, String>>()
    }
    
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        perms.forEach { (perm, label) ->
            val granted = permissionStates[perm] == true
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, fontSize = 14.sp)
                if (isChecking) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                } else {
                    androidx.compose.material3.Checkbox(
                        checked = granted,
                        onCheckedChange = null, // Read-only, request via button
                        colors = androidx.compose.material3.CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
        }
        
        if (perms.isNotEmpty()) {
            Button(onClick = onRequest, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text(if (perms.any { permissionStates[it.first] != true }) "Grant Permissions" else "All Granted ✓")
            }
        }
    }
}

@Composable
fun SpecialPermissionChecklist(
    permissionStates: Map<String, Boolean>,
    onOpenAccessibility: () -> Unit,
    onOpenNotificationListener: () -> Unit,
    onOpenUsageStats: () -> Unit,
    onOpenOverlay: () -> Unit,
    onOpenExactAlarm: () -> Unit
) {
    val items = listOf(
        SpecialItem("accessibility", "Accessibility Service", "Read screen & tap buttons", onOpenAccessibility),
        SpecialItem("notification_listener", "Notification Access", "Auto-reply to calls & read alerts", onOpenNotificationListener),
        SpecialItem("usage_stats", "Usage Stats", "Learn your app patterns", onOpenUsageStats),
        SpecialItem("overlay", "Display Over Apps", "Floating mic button", onOpenOverlay),
        SpecialItem("exact_alarm", "Exact Alarms", "Precise alarm timing", onOpenExactAlarm)
    )
    
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items.forEach { item ->
            val granted = permissionStates[item.key] == true
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.CardDefaults.cardColors(
                    containerColor = if (granted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(item.title, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                        Text(item.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (granted) {
                        androidx.compose.material3.Icon(androidx.compose.material3.icons.Icons.Default.CheckCircle, contentDescription = "Granted", tint = MaterialTheme.colorScheme.primary)
                    } else {
                        Button(onClick = item.onClick) { Text("Enable") }
                    }
                }
            }
        }
    }
}

data class SpecialItem(
    val key: String,
    val title: String,
    val description: String,
    val onClick: () -> Unit
)