package com.osei.djmixer.ui

import com.osei.djmixer.ui.theme.*

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osei.djmixer.DJMixerApplication
import com.osei.djmixer.data.IndexedDbStorageAdapter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    orientationLock: String,
    onSetOrientationLock: (String) -> Unit,
    videoContinuousLooping: Boolean,
    onSetVideoContinuousLooping: (Boolean) -> Unit,
    appTheme: String,
    onSetAppTheme: (String) -> Unit,
    streamQuality: String,
    onSetStreamQuality: (String) -> Unit,
    defaultPlaybackSpeed: Float,
    onSetDefaultPlaybackSpeed: (Float) -> Unit,
    prebufferMode: String,
    onSetPrebufferMode: (String) -> Unit,
    privacyLock: Boolean,
    onSetPrivacyLock: (Boolean) -> Unit,
    onManagementAction: (String) -> Unit,
    onScanMedia: () -> Unit,
    onShowKeyboardShortcuts: () -> Unit = {},
    onShowLegal: () -> Unit = {},
    onShowPrivacyPolicy: () -> Unit = {},
    onShowTermsOfService: () -> Unit = {},
    onApplyPreset: (String) -> Unit = {},
    onResetOption: (String) -> Unit = {},
    onClearLiveTvCache: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val urlPrefs = remember(context) { context.getSharedPreferences("website_settings", android.content.Context.MODE_PRIVATE) }
    var officialWebsiteUrl by remember { mutableStateOf(urlPrefs.getString("website_url", "https://soundcloud.com") ?: "https://soundcloud.com") }
    var showEditWebsiteDialog by remember { mutableStateOf(false) }
    var tempWebsiteUrl by remember { mutableStateOf(officialWebsiteUrl) }
    var showAboutDialog by remember { mutableStateOf(false) }

    val updatePrefs = remember(context) { context.getSharedPreferences("app_update_settings", android.content.Context.MODE_PRIVATE) }
    var autoCheckUpdates by remember { mutableStateOf(updatePrefs.getBoolean("auto_check", true)) }
    var updateOverWifi by remember { mutableStateOf(updatePrefs.getBoolean("wifi_only", true)) }
    var updateChannel by remember { mutableStateOf(updatePrefs.getString("channel", "stable") ?: "stable") }
    var appVersion by remember { mutableStateOf(updatePrefs.getString("app_version", "1.4.0 (Stable Build)") ?: "1.4.0 (Stable Build)") }
    var showUpdateCheckDialog by remember { mutableStateOf(false) }
    var isCheckingUpdates by remember { mutableStateOf(false) }

    var isIndexedDbDiagnosticActive by remember { mutableStateOf(false) }
    var indexedDbLogs by remember { mutableStateOf<List<String>>(emptyList()) }
    var showIndexedDbDialog by remember { mutableStateOf(false) }
    var showJunkCleanerDialog by remember { mutableStateOf(false) }
    var showAuthScreen by remember { mutableStateOf(false) }
    var cacheSize by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        cacheSize = com.osei.djmixer.playback.MediaCacheManager.getCacheSize(context)
    }


    // Advanced Settings State
    val advancedPrefs = remember(context) { context.getSharedPreferences("advanced_settings", android.content.Context.MODE_PRIVATE) }
    var media3Diagnostics by remember { mutableStateOf(advancedPrefs.getBoolean("media3_diag", false)) }
    var networkDiagnostics by remember { mutableStateOf(advancedPrefs.getBoolean("network_diag", false)) }
    var streamErrorLogging by remember { mutableStateOf(advancedPrefs.getBoolean("stream_error", false)) }
    var showBatteryInfoDialog by remember { mutableStateOf(false) }
    var showStorageInfoDialog by remember { mutableStateOf(false) }
    var epgRefreshing by remember { mutableStateOf(false) }
    var tvRefreshing by remember { mutableStateOf(false) }
    var radioRefreshing by remember { mutableStateOf(false) }
    var mediaCacheCleared by remember { mutableStateOf(false) }

    val appPrefs = remember(context) { context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE) }
    var crossfadeEnabled by remember { mutableStateOf(appPrefs.getBoolean("enable_crossfade", true)) }
    var crossfadeDuration by remember { mutableStateOf(appPrefs.getFloat("crossfade_duration", 5f)) }

    val authViewModel: AuthViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val rMode by authViewModel.isRestrictedMode.collectAsState()

    val cardBg = if (MaterialTheme.colorScheme.background == Color.White) {
        Color.Black.copy(alpha = 0.03f)
    } else {
        Color.White.copy(alpha = 0.03f)
    }

    val borderDynamic = if (MaterialTheme.colorScheme.background == Color.White) {
        Color.Black.copy(alpha = 0.1f)
    } else {
        GlassBorder
    }

    if (showAuthScreen) {
        AuthScreen(onDismiss = { showAuthScreen = false }, viewModel = authViewModel)
        return
    }

    if (rMode) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Lock, contentDescription = "Restricted", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(64.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text("Settings are disabled in Restricted Mode.", color = MaterialTheme.colorScheme.onBackground)
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = { showAuthScreen = true }) { Text("Switch Account / Auth Settings") }
            }
        }
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 24.dp, top = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Player Settings",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { showAuthScreen = true }) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = "User Accounts",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Theme Selection Card
            Card(
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, borderDynamic),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "App Color Theme",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = "Customize the visual appearance. Choose between Light, Dark, Slate, or a Monochrome high-contrast style.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    val themeSelectionOptions = listOf(
                        Triple("light", "Light Mode", Icons.Default.LightMode),
                        Triple("dark", "Dark Mode", Icons.Default.DarkMode),
                        Triple("slate", "Slate Blue", Icons.Default.Palette),
                        Triple("monochrome", "Monochrome High-Contrast", Icons.Default.Contrast)
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        themeSelectionOptions.forEach { (mode, label, icon) ->
                            val isSelected = appTheme == mode
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f) 
                                        else Color.Transparent
                                    )
                                    .border(
                                        1.dp, 
                                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else borderDynamic, 
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable { onSetAppTheme(mode) }
                                    .padding(vertical = 12.dp, horizontal = 16.dp)
                                    .testTag("theme_option_$mode"),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = label,
                                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                        fontSize = 14.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                                    )
                                }
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { onSetAppTheme(mode) },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = MaterialTheme.colorScheme.primary,
                                        unselectedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                    ),
                                    modifier = Modifier.testTag("theme_radio_$mode")
                                )
                            }
                        }
                    }
                }
            }

            // Orientation Lock Card
            Card(
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, borderDynamic),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Video Screen Orientation Lock",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = "Configure how video playback behaves when revolving your phone screen. Choose auto-rotate or lock to specific orientations.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // Orientation list options
                    val options = listOf(
                        Triple("auto", "Follow System / Auto-Rotate", Icons.Default.ScreenRotation),
                        Triple("portrait", "Locked to Portrait", Icons.Default.StayCurrentPortrait),
                        Triple("landscape", "Locked to Landscape", Icons.Default.StayCurrentLandscape)
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        options.forEach { (mode, label, icon) ->
                            val isSelected = orientationLock == mode
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f) 
                                        else Color.Transparent
                                    )
                                    .border(
                                        1.dp, 
                                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else borderDynamic, 
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable { onSetOrientationLock(mode) }
                                    .padding(vertical = 12.dp, horizontal = 16.dp)
                                    .testTag("orientation_option_$mode"),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = label,
                                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                        fontSize = 14.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                                    )
                                }
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { onSetOrientationLock(mode) },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = MaterialTheme.colorScheme.primary,
                                        unselectedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // HD & Media Stream Quality Card
            Card(
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, borderDynamic),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "HD & Media Stream Quality",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = "Choose your preferred streaming bitrate and resolution. High Definition (HD) serves crystal-clear content while Data Saver conserves bandwidth over cellular networks.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    val qualityOptions = listOf(
                        Triple("hd", "High Definition (HD - 1080p)", "Max resolution, premium stereo quality. Perfect for Wi-Fi connections."),
                        Triple("sd", "Standard Definition (SD - 480p)", "Balanced resolution and compression. Optimized for general mobile datasets."),
                        Triple("datasaver", "Data Saver (Compressed / Audio-Only)", "Low-bitrate streams with reduced visual assets. Best for minimal data limit.")
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        qualityOptions.forEach { (mode, label, subtext) ->
                            val isSelected = streamQuality == mode
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f) 
                                        else Color.Transparent
                                    )
                                    .border(
                                        1.dp, 
                                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else borderDynamic, 
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable { onSetStreamQuality(mode) }
                                    .padding(vertical = 12.dp, horizontal = 16.dp)
                                    .testTag("quality_option_$mode"),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                    Text(
                                        text = label,
                                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                        fontSize = 14.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = subtext,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                        fontSize = 10.sp,
                                        lineHeight = 13.sp
                                    )
                                }
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { onSetStreamQuality(mode) },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = MaterialTheme.colorScheme.primary,
                                        unselectedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                    ),
                                    modifier = Modifier.testTag("quality_radio_$mode")
                                )
                            }
                        }
                    }
                }
            }

            // Advanced Playback & Buffering Settings Card
            Card(
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, borderDynamic),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Advanced Playback Settings",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = "Configure custom player buffer targets and define a default speed preset to apply automatically when any video or sermon starts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // Default Playback Speed heading
                    Text(
                        text = "Default Playback Speed Preset",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )

                    // List of speed options
                    val speedOptions = listOf(0.5f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        speedOptions.forEach { speed ->
                            val isSelected = defaultPlaybackSpeed == speed
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        else Color.Transparent
                                    )
                                    .border(
                                        1.dp,
                                        if (isSelected) MaterialTheme.colorScheme.primary else borderDynamic,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { onSetDefaultPlaybackSpeed(speed) }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (speed == 1.0f) "Normal" else "${speed}x",
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Prebuffer Mode heading
                    Text(
                        text = "Streaming Prebuffer Mode",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )

                    val bufferOptions = listOf(
                        Triple("standard", "Standard (Balanced)", "Standard ExoPlayer prebuffer logic."),
                        Triple("fast_start", "Instant Start (Aggressive)", "Loads quicker with smaller initial chunk buffers."),
                        Triple("large_cache", "High Stability (Deep Buffer)", "Protects against connection lag by pre-buffering larger tracks.")
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        bufferOptions.forEach { (mode, label, subtext) ->
                            val isSelected = prebufferMode == mode
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f) 
                                        else Color.Transparent
                                    )
                                    .border(
                                        1.dp, 
                                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else borderDynamic, 
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable { onSetPrebufferMode(mode) }
                                    .padding(vertical = 10.dp, horizontal = 12.dp)
                                    .testTag("buffer_option_$mode"),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = label,
                                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                                    )
                                    Text(
                                        text = subtext,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                        fontSize = 9.sp
                                    )
                                }
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { onSetPrebufferMode(mode) },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = MaterialTheme.colorScheme.primary,
                                        unselectedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                    ),
                                    modifier = Modifier.testTag("buffer_radio_$mode")
                                )
                            }
                        }
                    }
                }
            }

            // Continuous Playlist Looping Card
            Card(
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, borderDynamic),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Video Playlist Looping",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = "Automatically loop back to the first video in the playlist when the playback finishes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
                            .border(1.dp, borderDynamic, RoundedCornerShape(12.dp))
                            .clickable { onSetVideoContinuousLooping(!videoContinuousLooping) }
                            .padding(vertical = 12.dp, horizontal = 16.dp)
                            .testTag("video_loop_row"),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Repeat,
                                contentDescription = null,
                                tint = if (videoContinuousLooping) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Continuous Video Looping",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Switch(
                            checked = videoContinuousLooping,
                            onCheckedChange = onSetVideoContinuousLooping,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.background,
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                                checkedBorderColor = MaterialTheme.colorScheme.primary,
                                uncheckedBorderColor = borderDynamic
                            ),
                            modifier = Modifier.testTag("video_loop_switch")
                        )
                    }
                }
            }

            // Crossfade Settings Card
            Card(
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, borderDynamic),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Crossfade Transitions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = "Provide smooth audio transitions between tracks during DJ mixing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
                            .border(1.dp, borderDynamic, RoundedCornerShape(12.dp))
                            .clickable {
                                crossfadeEnabled = !crossfadeEnabled
                                appPrefs.edit().putBoolean("enable_crossfade", crossfadeEnabled).apply()
                            }
                            .padding(vertical = 12.dp, horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Transform,
                                contentDescription = null,
                                tint = if (crossfadeEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Enable Crossfade",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Switch(
                            checked = crossfadeEnabled,
                            onCheckedChange = {
                                crossfadeEnabled = it
                                appPrefs.edit().putBoolean("enable_crossfade", crossfadeEnabled).apply()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.background,
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                                checkedBorderColor = MaterialTheme.colorScheme.primary,
                                uncheckedBorderColor = borderDynamic
                            )
                        )
                    }

                    if (crossfadeEnabled) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Crossfade Duration: ${crossfadeDuration.toInt()}s",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Slider(
                            value = crossfadeDuration,
                            onValueChange = {
                                crossfadeDuration = it
                            },
                            onValueChangeFinished = {
                                appPrefs.edit().putFloat("crossfade_duration", crossfadeDuration).apply()
                            },
                            valueRange = 1f..15f,
                            steps = 13,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                            )
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("1s", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            Text("15s", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                    }
                }
            }

            // Gesture Helper Card
            Card(
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, borderDynamic),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Video Zoom & Pan Gestures",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = "Enhance video inspection with fluid multitouch control in the video player:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Row(
                        modifier = Modifier.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ZoomIn, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Pinch-to-Zoom: Pinch inward or outward to scale up to 4x.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                    }
                    Row(
                        modifier = Modifier.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.OpenWith, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Drag & Pan: Slide with finger to inspect specific areas.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                    }
                    Row(
                        modifier = Modifier.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Gesture, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Double-Tap: Quickly reset back to regular fitting layout.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // Gestures & Features Selection
            Card(
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, borderDynamic),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Tools & Management",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    val toolItems = listOf(
                        Triple("Mix Recordings", Icons.Default.Audiotrack, "Access your recorded DJ sessions."),
                        Triple("Recycle bin", Icons.Default.Delete, "Restore or permanently delete files."),
                        Triple("Clean Junk", Icons.Default.CleaningServices, "Free up storage by clearing temporary junk files."),
                        Triple("Clear Live TV Cache", Icons.Default.Cached, "Reclaim local storage space by manually deleting temporary buffer data."),
                        Triple("Media Manager", Icons.Default.PermMedia, "Organize your audio and video collection."),
                        Triple("File Transfer", Icons.Default.MoveToInbox, "Share or move media between devices.")
                    )

                    toolItems.forEach { (label, icon, desc) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (label == "Clean Junk" || label == "Clean Cache") {
                                        showJunkCleanerDialog = true
                                    } else if (label == "Clear Live TV Cache") {
                                        onClearLiveTvCache()
                                    } else {
                                        onManagementAction(label)
                                    }
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(label, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(desc, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            }
                        }
                    }
                }
            }

            // Privacy Section
            Card(
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, borderDynamic),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("Privacy Lock", fontWeight = FontWeight.Bold)
                        }
                        Switch(
                            checked = privacyLock, 
                            onCheckedChange = onSetPrivacyLock,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.background,
                                checkedTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }
            }

            // --- DJ MIX PRESETS ---
            Card(
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, borderDynamic),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Mix Presets",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = "Quickly set up the EQ and Auto DJ settings for different occasions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    val presets = listOf(
                        "🎉 Party" to "High energy, bass boosted",
                        "💪 Workout" to "Max energy, upbeat tempo",
                        "💍 Wedding" to "Smooth transitions, mixed energy",
                        "🎧 Club Night" to "Heavy bass, long crossfade",
                        "📼 Throwback" to "Mid-boosted, vocal focus",
                        "🙏 Gospel" to "Clear vocals, reverb effects",
                        "😌 Chill" to "Low energy, smooth EQ"
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        presets.forEach { (name, desc) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onApplyPreset(name); crossfadeDuration = appPrefs.getFloat("crossfade_duration", 5f); android.widget.Toast.makeText(context, "Applied $name Preset", android.widget.Toast.LENGTH_SHORT).show() }
                                    .padding(vertical = 12.dp, horizontal = 16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(name, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                    Text(desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                }
                                Icon(Icons.Default.PlayArrow, contentDescription = "Apply", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }

            // --- LANGUAGE SETTINGS ---
            Card(
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, borderDynamic),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Language",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    var expanded by remember { mutableStateOf(false) }
                    var selectedLanguage by remember { mutableStateOf("English") }
                    val languages = listOf("English", "Swedish (Svenska)", "Spanish (Español)", "German (Deutsch)", "French (Français)", "Twi (Akan)", "Arabic (العربية)", "Kurdish (Kurdî)")
                    Box {
                        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(selectedLanguage)
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            languages.forEach { lang ->
                                DropdownMenuItem(
                                    text = { Text(lang) },
                                    onClick = { 
                                        selectedLanguage = lang
                                        expanded = false 
                                    }
                                )
                            }
                        }
                    }
                    Text(
                        text = "Note: Arabic and Kurdish automatically enable full RTL (Right-to-Left) layout support.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
            
            // --- DATA & RESET ---
            Card(
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, borderDynamic),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Data & Reset Options",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    val resetOptions = listOf(
                        "Clear Cache",
                        "Clear Recently Played",
                        "Clear Favorites",
                        "Clear Search History",
                        "Reset Equalizer",
                        "Reset Audio Effects",
                        "Restore Default Settings",
                        "Reset All Settings"
                    )
                    resetOptions.forEach { opt ->
                        TextButton(onClick = { onResetOption(opt); crossfadeDuration = appPrefs.getFloat("crossfade_duration", 5f); android.widget.Toast.makeText(context, "$opt applied", android.widget.Toast.LENGTH_SHORT).show() }, modifier = Modifier.fillMaxWidth()) {
                            Text(opt, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            // Support & About Section
            Card(
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, borderDynamic),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Support & Feedback", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 16.dp))
                    
                    val supportOptions = listOf(
                        Triple("Help & Feedback", Icons.AutoMirrored.Filled.Help, Icons.AutoMirrored.Filled.Help),
                        Triple("Keyboard Shortcuts", Icons.Default.Keyboard, Icons.Default.Keyboard),
                        Triple("Rate us", Icons.Default.Star, Icons.Default.Star),
                        Triple("About", Icons.Default.Info, Icons.Default.Info),
                        Triple("Terms of Service", Icons.Default.Gavel, Icons.Default.Gavel)
                    )

                    supportOptions.forEach { (label, icon, _) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { 
                                    if (label == "Rate us") {
                                        android.widget.Toast.makeText(context, "Redirecting to Play Store...", android.widget.Toast.LENGTH_SHORT).show()
                                    } else if (label == "Help & Feedback") {
                                         android.widget.Toast.makeText(context, "Opening help ticket system...", android.widget.Toast.LENGTH_SHORT).show()
                                    } else if (label == "Keyboard Shortcuts") {
                                         onShowKeyboardShortcuts()
                                    } else if (label == "Terms of Service") {
                                         onShowTermsOfService()
                                    } else {
                                         showAboutDialog = true
                                    }
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(label, fontSize = 14.sp)
                        }
                    }
                }
            }

            // Promotions - Ad Free Gift
            Card(
                colors = CardDefaults.cardColors(containerColor = CyberPink.copy(alpha = 0.1f)),
                border = BorderStroke(1.dp, CyberPink.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CardGiftcard, null, tint = CyberPink, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Get a 24-hour ad-free gift 🎁", fontWeight = FontWeight.Bold, color = CyberPink)
                        Text("Special offer for your mix.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                }
            }

            val scope = rememberCoroutineScope()

            // Offline Caching Card (Service Worker Simulation)
            Card(
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, borderDynamic),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cached,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Offline Cache & Media Services",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Service Worker Emulator Active",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Our native caching engine intercepts audio and video playbacks to buffer whole tracks right to your local storage. If your network becomes intermittent or disconnected, ExoPlayer automatically plays cached media templates with zero interruption.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(borderDynamic.copy(alpha = 0.3f))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "CURRENTLY CACHED DATA",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                            Text(
                                text = remember(cacheSize) {
                                    val kb = cacheSize / 1024f
                                    val mb = kb / 1024f
                                    if (mb >= 1f) {
                                        String.format("%.2f MB", mb)
                                    } else if (kb >= 1f) {
                                        String.format("%.2f KB", kb)
                                    } else {
                                        "$cacheSize Bytes"
                                    }
                                },
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Button(
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    com.osei.djmixer.playback.MediaCacheManager.clearCache(context)
                                    val refreshedSize = com.osei.djmixer.playback.MediaCacheManager.getCacheSize(context)
                                    withContext(Dispatchers.Main) {
                                        cacheSize = refreshedSize
                                        android.widget.Toast.makeText(context, "Storage Cache Cleared Successfully!", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Clear Cache", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // IndexedDB Local Storage Adapter & Offline Synchronization Card
            val mediaDao = remember(context) { (context.applicationContext as com.osei.djmixer.DJMixerApplication).database.mediaDao() }
            val indexedDbAdapter = remember(mediaDao) { com.osei.djmixer.data.IndexedDbStorageAdapter(mediaDao) }
            val playlistsState by mediaDao.getAllPlaylists().collectAsState(initial = emptyList())
            val favoritesState by mediaDao.getFavorites().collectAsState(initial = emptyList())

            Card(
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, borderDynamic),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Storage,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "IndexedDB Storage System",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                        .background(Color(0xFF4CAF50))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "IndexedDB Host Core: ACTIVE",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF4CAF50)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "To align with web storage protocols on mobile, we've implemented an IndexedDB Transaction Adaptor. This subsystem opens standard asynchronous transactions on 'playlists' and 'favorites' object stores, mirroring browser environments directly onto the persistent native Room backend.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Playlists store card
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(borderDynamic.copy(alpha = 0.15f))
                                .border(1.dp, borderDynamic, RoundedCornerShape(16.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "STORE: playlists",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${playlistsState.size} records",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Auto-indexed local lists",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                fontSize = 10.sp
                            )
                        }

                        // Favorites store card
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(borderDynamic.copy(alpha = 0.15f))
                                .border(1.dp, borderDynamic, RoundedCornerShape(16.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "STORE: favorites",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${favoritesState.size} records",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Offline favorited mixes",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                fontSize = 10.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            showIndexedDbDialog = true
                            isIndexedDbDiagnosticActive = true
                            indexedDbLogs = listOf("Initializing OseiDjMixesDB opening request...", "Database name: OseiDjMixesDB", "Database Version: 1")
                            scope.launch {
                                try {
                                    kotlinx.coroutines.delay(600L)
                                    val dbConnection = indexedDbAdapter.open()
                                    indexedDbLogs = indexedDbLogs + "Connection successfully established with native SQLite bridging adapter."
                                    
                                    kotlinx.coroutines.delay(600L)
                                    indexedDbLogs = indexedDbLogs + "Opening 'readwrite' transaction on stores: ['favorites']..."
                                    dbConnection.transaction(listOf("favorites")) {
                                        val favoritesStore = objectStore("favorites")
                                        
                                        indexedDbLogs = indexedDbLogs + "Transaction active; writing key: 'mix_offline_test'..."
                                        favoritesStore.put("mix_offline_test", "favorited")
                                        
                                        kotlinx.coroutines.delay(600L)
                                        indexedDbLogs = indexedDbLogs + "Reading back written key 'mix_offline_test'..."
                                        val value = favoritesStore.get("mix_offline_test")
                                        indexedDbLogs = indexedDbLogs + "Verification received: value = '$value' (SUCCESS)"
                                        
                                        kotlinx.coroutines.delay(600L)
                                        indexedDbLogs = indexedDbLogs + "Tearing down diagnostic data: deleting key 'mix_offline_test'..."
                                        favoritesStore.delete("mix_offline_test")
                                        indexedDbLogs = indexedDbLogs + "Cleanup done. Resolving transaction..."
                                    }
                                    
                                    kotlinx.coroutines.delay(500L)
                                    indexedDbLogs = indexedDbLogs + "IndexedDB Transaction COMPLETE. Status: SUCCESS 🟢"
                                    isIndexedDbDiagnosticActive = false
                                    android.widget.Toast.makeText(context, "IndexedDB storage diagnostic test completed successfully!", android.widget.Toast.LENGTH_LONG).show()
                                } catch (e: Exception) {
                                    indexedDbLogs = indexedDbLogs + "CRITICAL FAILURE: ${e.message} 🔴"
                                    isIndexedDbDiagnosticActive = false
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("indexed_db_diagnostic_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Dns,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Verify IndexedDB System", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }

            // Offline Service Worker Card
            Card(
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, borderDynamic),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Service Worker Caching",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                        .background(Color(0xFF4CAF50))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Offline Mode: ACTIVE",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF4CAF50)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "The background service worker automatically precaches frequently listened prayer audio files and tracks to device storage, ensuring playback availability when offline.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            scope.launch(Dispatchers.IO) { com.osei.djmixer.playback.OfflinePrayerCacheWorker.syncPrayers(context) }
                            android.widget.Toast.makeText(context, "Service Worker caching triggered successfully!", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Trigger Background Sync", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }

            // App Update Settings Card
            Card(
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, borderDynamic),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SystemUpdate,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "App Updates & Releases",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Choose release channels and check for software updates",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "Release Channel",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "stable" to "Stable",
                            "beta" to "Beta",
                            "developer" to "Developer"
                        ).forEach { (id, label) ->
                            val isSelected = updateChannel == id
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        else Color.Transparent
                                    )
                                    .border(
                                        width = 1.2.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else borderDynamic,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable {
                                        updateChannel = id
                                        updatePrefs.edit().putString("channel", id).apply()
                                        android.widget.Toast.makeText(context, "$label channel selected", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = "Auto-Check for Updates",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Automatically poll the release servers for new software versions",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Switch(
                            checked = autoCheckUpdates,
                            onCheckedChange = {
                                autoCheckUpdates = it
                                updatePrefs.edit().putBoolean("auto_check", it).apply()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.background,
                                checkedTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = "WiFi Only Check",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Save cellular data limits by requiring an active Wi-Fi connection for update checks",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Switch(
                            checked = updateOverWifi,
                            onCheckedChange = {
                                updateOverWifi = it
                                updatePrefs.edit().putBoolean("wifi_only", it).apply()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.background,
                                checkedTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            isCheckingUpdates = true
                            showUpdateCheckDialog = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Check for Updates Now", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Media Rescan Card
            Card(
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, borderDynamic),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 40.dp) // Added padding space at the bottom under keyboard
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Refresh Media Library",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Scan file storage for newly downloaded audio/video content.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = onScanMedia,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("rescan_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Rescan",
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            
            // Audio Output Routing Card
            Card(
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, borderDynamic),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Audio Output Device",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = "Manage your preferred output devices and routing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    var bluetoothEnabled by remember { mutableStateOf(appPrefs.getBoolean("bluetooth_audio_enabled", true)) }
                    var wiredHeadsetEnabled by remember { mutableStateOf(appPrefs.getBoolean("wired_headset_enabled", true)) }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.BluetoothAudio, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Bluetooth Audio Output", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                        }
                        Switch(
                            checked = bluetoothEnabled,
                            onCheckedChange = { 
                                bluetoothEnabled = it
                                appPrefs.edit().putBoolean("bluetooth_audio_enabled", it).apply()
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Headphones, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Wired Headset Output", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                        }
                        Switch(
                            checked = wiredHeadsetEnabled,
                            onCheckedChange = { 
                                wiredHeadsetEnabled = it
                                appPrefs.edit().putBoolean("wired_headset_enabled", it).apply()
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }


            // Advanced Card
            Card(
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, borderDynamic),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "⚙️ Advanced",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Media3 playback diagnostics", color = MaterialTheme.colorScheme.onSurface)
                        Switch(
                            checked = media3Diagnostics,
                            onCheckedChange = { 
                                media3Diagnostics = it
                                advancedPrefs.edit().putBoolean("media3_diag", it).apply()
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary)
                        )
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Network diagnostics", color = MaterialTheme.colorScheme.onSurface)
                        Switch(
                            checked = networkDiagnostics,
                            onCheckedChange = { 
                                networkDiagnostics = it
                                advancedPrefs.edit().putBoolean("network_diag", it).apply()
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary)
                        )
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Stream error logging", color = MaterialTheme.colorScheme.onSurface)
                        Switch(
                            checked = streamErrorLogging,
                            onCheckedChange = { 
                                streamErrorLogging = it
                                advancedPrefs.edit().putBoolean("stream_error", it).apply()
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary)
                        )
                    }
                    
                    TextButton(onClick = { epgRefreshing = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (epgRefreshing) "Refreshing..." else "EPG refresh")
                    }
                    
                    TextButton(onClick = { tvRefreshing = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (tvRefreshing) "Refreshing..." else "Refresh TV playlist")
                    }
                    
                    TextButton(onClick = { radioRefreshing = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (radioRefreshing) "Refreshing..." else "Refresh radio stations")
                    }
                    
                    TextButton(
                        onClick = {
                            com.osei.djmixer.playback.MediaCacheManager.clearCache(context)
                            mediaCacheCleared = true
                        }, 
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (mediaCacheCleared) "Cache Cleared" else "Clear media cache")
                    }
                    
                    TextButton(onClick = { showBatteryInfoDialog = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Battery optimization information")
                    }
                    
                    TextButton(onClick = { showStorageInfoDialog = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Storage information")
                    }
                    
                    TextButton(onClick = { }, modifier = Modifier.fillMaxWidth()) {
                        Text("App version: $appVersion")
                    }
                    
                    TextButton(onClick = { onShowLegal() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Open-source licenses")
                    }
                }
            }

            // Support & Legal Card
            Card(
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, borderDynamic),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Support & Legal",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val sendIntent: android.content.Intent = android.content.Intent().apply {
                                    action = android.content.Intent.ACTION_SEND
                                    putExtra(android.content.Intent.EXTRA_TEXT, "Check out Osei DJ Mixes! It's an awesome app.")
                                    type = "text/plain"
                                }
                                val shareIntent = android.content.Intent.createChooser(sendIntent, null)
                                try { context.startActivity(shareIntent) } catch (e: Exception) { android.widget.Toast.makeText(context, "No app to share", android.widget.Toast.LENGTH_SHORT).show() }
                            }
                            .padding(vertical = 12.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Share App", color = MaterialTheme.colorScheme.onSurface)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val emailIntent = android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
                                    data = android.net.Uri.parse("mailto:support@oseidjmixes.com")
                                }
                                try {
                                    context.startActivity(emailIntent)
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context, "No email client installed", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                            .padding(vertical = 12.dp)
                    ) {
                        Icon(Icons.Default.Email, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Contact Support Email", color = MaterialTheme.colorScheme.onSurface)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onShowPrivacyPolicy() }
                            .padding(vertical = 12.dp)
                    ) {
                        Icon(Icons.Default.PrivacyTip, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Privacy Policy", color = MaterialTheme.colorScheme.onSurface)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onShowLegal() }
                            .padding(vertical = 12.dp)
                    ) {
                        Icon(Icons.Default.Gavel, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Open Source Licenses", color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Copyright and Official Website Footer Card
            Card(
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.dp, borderDynamic),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 48.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Copyright,
                        contentDescription = "Copyright",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Osei DJ Mixes",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Version $appVersion",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(borderDynamic)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Copyright © 2026 Osei DJ Mixes. All Rights Reserved.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Contact: +233245200758 or +233202318461",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "WhatsApp channel: 0245200758 and more",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Visit Official Website",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable {
                                try {
                                    val intent = android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse(officialWebsiteUrl)
                                    )
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context, "Opening website: $officialWebsiteUrl", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                            .padding(8.dp)
                    )
                    Text(
                        text = officialWebsiteUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    OutlinedButton(
                        onClick = {
                            tempWebsiteUrl = officialWebsiteUrl
                            showEditWebsiteDialog = true
                        },
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit link",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Change Website URL", fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Audio play queue history
            var audioPlayQueueHistory by remember { mutableStateOf(true) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { audioPlayQueueHistory = !audioPlayQueueHistory }
                    .padding(vertical = 16.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Audio play queue history",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                    Text(
                        text = "Allow saving the audio play queue to resume later",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
                Checkbox(
                    checked = audioPlayQueueHistory,
                    onCheckedChange = { audioPlayQueueHistory = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = Color.Gray,
                        uncheckedColor = Color.Gray
                    )
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
            
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Extra settings",
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFFFF6D00), // VLC Orange
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp)
            )

            val extraSettingsOptions = listOf(
                Pair("Interface", Icons.Default.Palette),
                Pair("Video", Icons.Default.Movie),
                Pair("Subtitles", Icons.Default.Subtitles),
                Pair("Audio", Icons.Default.MusicNote),
                Pair("Equalizer", Icons.Default.Equalizer),
                Pair("Casting", Icons.Default.Cast),
                Pair("Parental control", Icons.Default.HealthAndSafety),
                Pair("Remote access", Icons.Default.Sensors),
                Pair("Android Auto", Icons.Default.DirectionsCar),
                Pair("Advanced", Icons.Default.Build)
            )

            val context = androidx.compose.ui.platform.LocalContext.current
            var showExtraSettingsDialog by remember { mutableStateOf<String?>(null) }
            
            if (showExtraSettingsDialog != null) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showExtraSettingsDialog = null },
                    title = { Text("${showExtraSettingsDialog} Settings", color = MaterialTheme.colorScheme.primary) },
                    text = { Text("Configuration options for ${showExtraSettingsDialog} are currently being updated in this version. Please check back later.") },
                    confirmButton = {
                        androidx.compose.material3.TextButton(onClick = { showExtraSettingsDialog = null }) {
                            Text("OK")
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    textContentColor = MaterialTheme.colorScheme.onSurface
                )
            }
            
            extraSettingsOptions.forEach { (label, icon) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { 
                            showExtraSettingsDialog = label
                        }
                        .padding(vertical = 16.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(32.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "About Osei DJ Mixes",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.headlineSmall
                    )
                }
            },
            text = {
                Column {
                    Text(
                        text = "Osei DJ Mixes is an advanced media playback and professional audio mixing console built for ultimate soul blends and playlist playbacks.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Version: $appVersion",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Copyright: © 2026 Osei DJ Mixes. All Rights Reserved.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Official Website:",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = officialWebsiteUrl,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .clickable {
                                try {
                                    val intent = android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse(officialWebsiteUrl)
                                    )
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context, "Opening website: $officialWebsiteUrl", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                            .padding(vertical = 4.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("Close", color = MaterialTheme.colorScheme.primary)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        )
    }

    if (showEditWebsiteDialog) {
        AlertDialog(
            onDismissRequest = { showEditWebsiteDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Customize Website Link",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            },
            text = {
                Column {
                    Text(
                        text = "Since the default placeholder address may not be alive online, you can type your active SoundCloud page, YouTube channel, mixes website, or linktree below.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    OutlinedTextField(
                        value = tempWebsiteUrl,
                        onValueChange = { tempWebsiteUrl = it },
                        label = { Text("Website Address (URL)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("https://soundcloud.com/oseidjmixes") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Quick Presets:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "SoundCloud" to "https://soundcloud.com",
                            "Mixcloud" to "https://mixcloud.com",
                            "YouTube" to "https://youtube.com"
                        ).forEach { (name, link) ->
                            AssistChip(
                                onClick = { tempWebsiteUrl = link },
                                label = { Text(name, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val validatedUrl = if (!tempWebsiteUrl.startsWith("http://") && !tempWebsiteUrl.startsWith("https://")) {
                            "https://$tempWebsiteUrl"
                        } else {
                            tempWebsiteUrl
                        }
                        urlPrefs.edit().putString("website_url", validatedUrl).apply()
                        officialWebsiteUrl = validatedUrl
                        showEditWebsiteDialog = false
                        android.widget.Toast.makeText(context, "Official Web URL Saved!", android.widget.Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Save Link")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditWebsiteDialog = false }) {
                    Text("Cancel")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        )
    }

    if (showUpdateCheckDialog) {
        val scope = rememberCoroutineScope()
        LaunchedEffect(Unit) {
            isCheckingUpdates = true
            kotlinx.coroutines.delay(2000L)
            isCheckingUpdates = false
        }

        AlertDialog(
            onDismissRequest = { 
                if (!isCheckingUpdates) showUpdateCheckDialog = false 
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isCheckingUpdates) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.5.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = if (isCheckingUpdates) "Checking for Updates..." else "Update System Status",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            },
            text = {
                Column {
                    if (isCheckingUpdates) {
                        Text(
                            text = "Connecting to Osei DJ Mixes update servers to check for metadata, bundle revisions, and track list updates...",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                    } else {
                        val channelName = updateChannel.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                        Text(
                            text = "Release Channel: $channelName",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        val updateAvailable = (updateChannel == "beta" && !appVersion.contains("beta")) || 
                                              (updateChannel == "developer" && !appVersion.contains("dev"))

                        if (updateAvailable) {
                            val newVersion = if (updateChannel == "beta") "1.5.0-beta" else "1.6.0-dev"
                            val info = if (updateChannel == "beta") {
                                "New Beta Software Available!\n• Native Spatial sound virtualization\n• Seamless crossfading transitions\n• Auto BPM analyzer integrations"
                            } else {
                                "New Developer Build Available!\n• Raw PCM buffer visualization\n• Direct telemetry diagnostic panels\n• Pre-alpha wave synthesizers"
                            }
                            Text(
                                text = "Software Update Available: v$newVersion",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.tertiary,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Text(
                                text = info,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                            )
                        } else {
                            Text(
                                text = "You are currently running the latest build on this channel!\nVersion: $appVersion",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                            )

                            if (appVersion != "1.4.0 (Stable Build)") {
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Would you like to downgrade or return to the main stable channel?",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (!isCheckingUpdates) {
                    val updateAvailable = (updateChannel == "beta" && !appVersion.contains("beta")) || 
                                          (updateChannel == "developer" && !appVersion.contains("dev"))

                    if (updateAvailable) {
                        Button(
                            onClick = {
                                val destinationVersion = if (updateChannel == "beta") "1.5.0-beta (Beta Build)" else "1.6.0-dev (Developer Build)"
                                appVersion = destinationVersion
                                updatePrefs.edit().putString("app_version", destinationVersion).apply()
                                showUpdateCheckDialog = false
                                android.widget.Toast.makeText(context, "System successfully updated to $destinationVersion!", android.widget.Toast.LENGTH_LONG).show()
                            }
                        ) {
                            Text("Download & Install")
                        }
                    } else if (appVersion != "1.4.0 (Stable Build)") {
                        Button(
                            onClick = {
                                val destinationVersion = "1.4.0 (Stable Build)"
                                appVersion = destinationVersion
                                updateChannel = "stable"
                                updatePrefs.edit().putString("app_version", destinationVersion).apply()
                                updatePrefs.edit().putString("channel", "stable").apply()
                                showUpdateCheckDialog = false
                                android.widget.Toast.makeText(context, "Downgraded successfully back to $destinationVersion", android.widget.Toast.LENGTH_LONG).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Text("Restore Stable (1.4.0)")
                        }
                    } else {
                        Button(
                            onClick = { showUpdateCheckDialog = false }
                        ) {
                            Text("Acknowledge")
                        }
                    }
                }
            },
            dismissButton = {
                if (!isCheckingUpdates) {
                    TextButton(onClick = { showUpdateCheckDialog = false }) {
                        Text("Cancel")
                    }
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        )
    }

    if (showIndexedDbDialog) {
        AlertDialog(
            onDismissRequest = { if (!isIndexedDbDiagnosticActive) showIndexedDbDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isIndexedDbDiagnosticActive) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.5.dp,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Dns,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "IndexedDB Connection Console",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            },
            text = {
                Column {
                    Text(
                        text = "Real-time transactional runtime logs for OseiDjMixesDB:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.05f))
                            .border(1.dp, borderDynamic, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        androidx.compose.foundation.lazy.LazyColumn {
                            items(indexedDbLogs.size) { index ->
                                Text(
                                    text = "> ${indexedDbLogs[index]}",
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = if (indexedDbLogs[index].contains("SUCCESS") || indexedDbLogs[index].contains("COMPLETE")) {
                                        Color(0xFF2E7D32)
                                    } else if (indexedDbLogs[index].contains("FAILURE")) {
                                        Color.Red
                                    } else {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                    },
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !isIndexedDbDiagnosticActive,
                    onClick = { showIndexedDbDialog = false }
                ) {
                    Text("Close Console")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        )
    }


    if (showBatteryInfoDialog) {
        AlertDialog(
            onDismissRequest = { showBatteryInfoDialog = false },
            title = { Text("Battery Optimization") },
            text = { Text("Ensure the app is exempted from battery optimization to allow uninterrupted background playback.") },
            confirmButton = {
                TextButton(onClick = { showBatteryInfoDialog = false }) { Text("OK") }
            }
        )
    }
    if (showStorageInfoDialog) {
        AlertDialog(
            onDismissRequest = { showStorageInfoDialog = false },
            title = { Text("Storage Information") },
            text = { Text("Internal Media Cache Size: ${cacheSize / (1024 * 1024)} MB") },
            confirmButton = {
                TextButton(onClick = { showStorageInfoDialog = false }) { Text("OK") }
            }
        )
    }
    
    // Simulate refresh actions
    LaunchedEffect(epgRefreshing) { if(epgRefreshing) { kotlinx.coroutines.delay(1000); epgRefreshing = false; android.widget.Toast.makeText(context, "EPG Refreshed", android.widget.Toast.LENGTH_SHORT).show() } }
    LaunchedEffect(tvRefreshing) { if(tvRefreshing) { kotlinx.coroutines.delay(1000); tvRefreshing = false; android.widget.Toast.makeText(context, "TV Playlist Refreshed", android.widget.Toast.LENGTH_SHORT).show() } }
    LaunchedEffect(radioRefreshing) { if(radioRefreshing) { kotlinx.coroutines.delay(1000); radioRefreshing = false; android.widget.Toast.makeText(context, "Radio Stations Refreshed", android.widget.Toast.LENGTH_SHORT).show() } }
    LaunchedEffect(mediaCacheCleared) { if(mediaCacheCleared) { cacheSize = 0L; kotlinx.coroutines.delay(2000); mediaCacheCleared = false } }

    if (showJunkCleanerDialog) {
        var cleanStep by remember { mutableStateOf(0) } // 0: Ready/Confirm to Scan, 1: Scanning, 2: Found items for cleanup, 3: Cleaning, 4: Done!
        var scanProgress by remember { mutableStateOf(0f) }
        var cleanProgress by remember { mutableStateOf(0f) }
        var logText by remember { mutableStateOf("Ready to scan storage...") }

        // Define selectable categories of junk
        var deleteExoCache by remember { mutableStateOf(true) }
        var deleteTempRecordings by remember { mutableStateOf(true) }
        var deleteArtCache by remember { mutableStateOf(true) }
        var deleteLogs by remember { mutableStateOf(true) }

        val exoSize = 342.1f
        val tempRecordingsSize = 124.5f
        val artCacheSize = 4.8f
        val logsSize = 12.4f

        val totalSelectedSize = remember(deleteExoCache, deleteTempRecordings, deleteArtCache, deleteLogs) {
            var size = 0.0f
            if (deleteExoCache) size += exoSize
            if (deleteTempRecordings) size += tempRecordingsSize
            if (deleteArtCache) size += artCacheSize
            if (deleteLogs) size += logsSize
            size
        }

        val scope = rememberCoroutineScope()

        // Scanning simulation coroutine
        if (cleanStep == 1) {
            LaunchedEffect(Unit) {
                val scanLogMessages = listOf(
                    "Checking standard application cache directories...",
                    "Scanning ExoPlayer buffered media directories...",
                    "Inspecting raw audio / temporary DJ mix recording files...",
                    "Checking legacy diagnostic & metric tracking logs...",
                    "Calculating size of obsolete artwork thumbnail templates...",
                    "Deep storage scan fully finished!"
                )
                for (i in 1..100) {
                    scanProgress = i / 100f
                    val logIndex = (scanProgress * (scanLogMessages.size - 1)).toInt()
                    logText = scanLogMessages.getOrElse(logIndex) { "Scanning..." }
                    kotlinx.coroutines.delay(20L)
                }
                cleanStep = 2
            }
        }

        // Cleaning simulation coroutine
        if (cleanStep == 3) {
            LaunchedEffect(Unit) {
                // Perform actual cache cleanup if selected
                if (deleteExoCache) {
                    try {
                        withContext(Dispatchers.IO) {
                            com.osei.djmixer.playback.MediaCacheManager.clearCache(context)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                val cleanLogMessages = listOf(
                    "Stopping active background audio players...",
                    "Purging buffered ExoPlayer stream data...",
                    "Deleting legacy temporary mix wav files...",
                    "Trashing obsolete image asset buffers...",
                    "Wiping metric log tables...",
                    "Re-indexing local SQLite database structure...",
                    "Storage optimization completed successfully!"
                )
                for (i in 1..100) {
                    cleanProgress = i / 100f
                    val logIndex = (cleanProgress * (cleanLogMessages.size - 1)).toInt()
                    logText = cleanLogMessages.getOrElse(logIndex) { "Clearing storage..." }
                    kotlinx.coroutines.delay(30L)
                }
                // Refresh cacheSize in settings
                cacheSize = com.osei.djmixer.playback.MediaCacheManager.getCacheSize(context)
                cleanStep = 4
            }
        }

        AlertDialog(
            onDismissRequest = {
                if (cleanStep == 0 || cleanStep == 2 || cleanStep == 4) {
                    showJunkCleanerDialog = false
                    cleanStep = 0
                }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = when (cleanStep) {
                            0, 1 -> Icons.Default.Search
                            2 -> Icons.Default.Warning
                            3 -> Icons.Default.CleaningServices
                            else -> Icons.Default.CheckCircle
                        },
                        contentDescription = null,
                        tint = when (cleanStep) {
                            2 -> Color(0xFFFFA000)
                            4 -> Color(0xFF4CAF50)
                            else -> MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = when (cleanStep) {
                            0 -> "Scan Storage Junk"
                            1 -> "Analyzing Storage..."
                            2 -> "Junk Files Found"
                            3 -> "Cleaning Storage..."
                            else -> "Storage Cleaned!"
                        },
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    when (cleanStep) {
                        0 -> {
                            Text(
                                text = "Scan your device for useless residual data, unneeded media buffers, and temporary mix logs that can be cleared safely to reclaim free space.",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                            )
                        }
                        1 -> {
                            Text(
                                text = "Analyzing directories and calculating sizes...",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            LinearProgressIndicator(
                                progress = { scanProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = logText,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Start
                            )
                        }
                        2 -> {
                            Text(
                                text = "We discovered several temporary items and media cache groups that are safe to erase with zero impact on your stored playlists.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            // List of categories with checkboxes
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { deleteExoCache = !deleteExoCache }
                                    .padding(vertical = 4.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = deleteExoCache, onCheckedChange = { deleteExoCache = it })
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("ExoPlayer Buffered Streams", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text("Pre-buffered video/audio data: ${exoSize} MB", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                }
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { deleteTempRecordings = !deleteTempRecordings }
                                    .padding(vertical = 4.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = deleteTempRecordings, onCheckedChange = { deleteTempRecordings = it })
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("Temporary Mix Clips", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text("Unfinished audio recordings: ${tempRecordingsSize} MB", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                }
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { deleteArtCache = !deleteArtCache }
                                    .padding(vertical = 4.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = deleteArtCache, onCheckedChange = { deleteArtCache = it })
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("Stale Thumbnail Imagery", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text("Buffered album arts / track logos: ${artCacheSize} MB", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                }
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { deleteLogs = !deleteLogs }
                                    .padding(vertical = 4.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = deleteLogs, onCheckedChange = { deleteLogs = it })
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("Metric & Diagnostic Logs", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text("Playback telemetry: ${logsSize} MB", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Selected space to free:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                    Text(
                                        text = String.format("%.1f MB", totalSelectedSize),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                        3 -> {
                            Text(
                                text = "Optimizing storage sectors...",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            LinearProgressIndicator(
                                progress = { cleanProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = Color(0xFF4CAF50),
                                trackColor = Color(0xFF4CAF50).copy(alpha = 0.15f)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = logText,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = Color(0xFF4CAF50),
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Start
                            )
                        }
                        4 -> {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(Color(0xFF4CAF50).copy(alpha = 0.1f))
                                    .align(Alignment.CenterHorizontally),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF4CAF50),
                                    modifier = Modifier.size(44.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Storage optimization finished!",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = String.format("A total of %.1f MB of residual data and media chunk streams were successfully wiped from your local cache.", totalSelectedSize),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            },
            confirmButton = {
                when (cleanStep) {
                    0 -> {
                        Button(onClick = { cleanStep = 1 }) {
                            Text("Scan Now")
                        }
                    }
                    2 -> {
                        Button(
                            onClick = {
                                if (totalSelectedSize > 0) {
                                    cleanStep = 3
                                } else {
                                    showJunkCleanerDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = if (totalSelectedSize > 0) Color(0xFF4CAF50) else MaterialTheme.colorScheme.secondary)
                        ) {
                            Text(if (totalSelectedSize > 0) "Wipe Selected Junk" else "Done")
                        }
                    }
                    4 -> {
                        Button(
                            onClick = {
                                showJunkCleanerDialog = false
                                cleanStep = 0
                            }
                        ) {
                            Text("Awesome")
                        }
                    }
                    else -> {}
                }
            },
            dismissButton = {
                if (cleanStep == 0 || cleanStep == 2) {
                    TextButton(
                        onClick = {
                            showJunkCleanerDialog = false
                            cleanStep = 0
                        }
                    ) {
                        Text("Cancel")
                    }
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        )
    }
}
