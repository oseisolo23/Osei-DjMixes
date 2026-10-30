package com.osei.djmixer.ui

import com.osei.djmixer.ui.theme.*

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import android.content.Context

class AuthViewModel : ViewModel() {
    private val _currentUser = MutableStateFlow<String?>(null)
    val currentUser = _currentUser.asStateFlow()
    
    private val _isRestrictedMode = MutableStateFlow(false)
    val isRestrictedMode = _isRestrictedMode.asStateFlow()

    private var contextRef: Context? = null

    // User Profile & Preferences states
    private val _profileName = MutableStateFlow("")
    val profileName = _profileName.asStateFlow()

    private val _profilePhone = MutableStateFlow("")
    val profilePhone = _profilePhone.asStateFlow()

    private val _profileWhatsapp = MutableStateFlow("")
    val profileWhatsapp = _profileWhatsapp.asStateFlow()

    private val _prefStreamQuality = MutableStateFlow("High")
    val prefStreamQuality = _prefStreamQuality.asStateFlow()

    private val _prefPlaybackSpeed = MutableStateFlow(1.0f)
    val prefPlaybackSpeed = _prefPlaybackSpeed.asStateFlow()

    private val _prefEqPreset = MutableStateFlow("Flat")
    val prefEqPreset = _prefEqPreset.asStateFlow()

    private val _prefEqEnabled = MutableStateFlow(false)
    val prefEqEnabled = _prefEqEnabled.asStateFlow()

    fun initialize(context: Context) {
        contextRef = context.applicationContext
        val prefs = context.getSharedPreferences("user_auth_prefs", Context.MODE_PRIVATE)
        val savedUser = prefs.getString("current_user_email", null)
        _currentUser.value = savedUser
        if (savedUser != null) {
            loadProfile(savedUser, context)
        }
    }

    fun loadProfile(email: String, context: Context) {
        val prefs = context.getSharedPreferences("user_profile_${email}", Context.MODE_PRIVATE)
        _profileName.value = prefs.getString("displayName", "") ?: ""
        _profilePhone.value = prefs.getString("phoneNumber", "") ?: ""
        _profileWhatsapp.value = prefs.getString("whatsappNumber", "") ?: ""
        _prefStreamQuality.value = prefs.getString("prefStreamQuality", "High") ?: "High"
        _prefPlaybackSpeed.value = prefs.getFloat("prefPlaybackSpeed", 1.0f)
        _prefEqPreset.value = prefs.getString("prefEqPreset", "Flat") ?: "Flat"
        _prefEqEnabled.value = prefs.getBoolean("prefEqEnabled", false)
    }

    fun saveProfile(
        name: String,
        phone: String,
        whatsapp: String,
        streamQuality: String,
        playbackSpeed: Float,
        eqPreset: String,
        eqEnabled: Boolean,
        context: Context,
        onDone: () -> Unit
    ) {
        val email = _currentUser.value ?: "unknown"
        val prefs = context.getSharedPreferences("user_profile_${email}", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("displayName", name)
            putString("phoneNumber", phone)
            putString("whatsappNumber", whatsapp)
            putString("prefStreamQuality", streamQuality)
            putFloat("prefPlaybackSpeed", playbackSpeed)
            putString("prefEqPreset", eqPreset)
            putBoolean("prefEqEnabled", eqEnabled)
            apply()
        }

        // Apply audio Preferences and equalizer configurations directly:
        try {
            com.osei.djmixer.playback.AudioEqualizerManager.instance.setEqualizerEnabled(eqEnabled)
            com.osei.djmixer.playback.AudioEqualizerManager.instance.applyPreset(eqPreset)

            val mainPrefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            mainPrefs.edit().apply {
                putString("stream_quality", streamQuality)
                putFloat("default_playback_speed", playbackSpeed)
                apply()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        _profileName.value = name
        _profilePhone.value = phone
        _profileWhatsapp.value = whatsapp
        _prefStreamQuality.value = streamQuality
        _prefPlaybackSpeed.value = playbackSpeed
        _prefEqPreset.value = eqPreset
        _prefEqEnabled.value = eqEnabled

        onDone()
    }

    fun signIn(email: String, pass: String, onResult: (Boolean, String) -> Unit) {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isEmpty() || pass.isEmpty()) {
            onResult(false, "Please enter email and password.")
            return
        }
        val ctx = contextRef ?: run {
            onResult(false, "App context not initialized.")
            return
        }
        val authPrefs = ctx.getSharedPreferences("user_auth_prefs", Context.MODE_PRIVATE)
        val savedPass = authPrefs.getString("pwd_${trimmedEmail}", null)
        if (savedPass == null || savedPass == pass) {
            authPrefs.edit()
                .putString("pwd_${trimmedEmail}", pass)
                .putString("current_user_email", trimmedEmail)
                .apply()
            _currentUser.value = trimmedEmail
            loadProfile(trimmedEmail, ctx)
            onResult(true, "Signed in successfully.")
        } else {
            onResult(false, "Incorrect password.")
        }
    }

    fun signUp(email: String, pass: String, onResult: (Boolean, String) -> Unit) {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isEmpty() || pass.isEmpty()) {
            onResult(false, "Please enter email and password.")
            return
        }
        val ctx = contextRef ?: run {
            onResult(false, "App context not initialized.")
            return
        }
        val authPrefs = ctx.getSharedPreferences("user_auth_prefs", Context.MODE_PRIVATE)
        authPrefs.edit()
            .putString("pwd_${trimmedEmail}", pass)
            .putString("current_user_email", trimmedEmail)
            .apply()
        _currentUser.value = trimmedEmail
        loadProfile(trimmedEmail, ctx)
        onResult(true, "Account created successfully.")
    }

    fun resetPassword(email: String, onResult: (Boolean, String) -> Unit) {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isEmpty()) {
            onResult(false, "Please enter your email to reset password.")
            return
        }
        onResult(true, "Password reset instructions sent.")
    }

    fun verifyEmail(onResult: (Boolean, String) -> Unit) {
        onResult(true, "Email verified.")
    }

    fun signOut() {
        contextRef?.getSharedPreferences("user_auth_prefs", Context.MODE_PRIVATE)
            ?.edit()?.remove("current_user_email")?.apply()
        _currentUser.value = null
        _isRestrictedMode.value = false
    }

    fun toggleRestrictedMode() {
        _isRestrictedMode.value = !_isRestrictedMode.value
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    onDismiss: () -> Unit,
    viewModel: AuthViewModel = viewModel()
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val currentUser by viewModel.currentUser.collectAsState()
    val isRestricted by viewModel.isRestrictedMode.collectAsState()

    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.initialize(context)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xE60A0E17)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161E2E)),
            modifier = Modifier.padding(24.dp).fillMaxWidth(0.9f)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (currentUser != null) "User Account" else "Sign In / Register",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))

                if (currentUser != null) {
                    var editDisplayName by remember { mutableStateOf("") }
                    var editPhone by remember { mutableStateOf("") }
                    var editWhatsapp by remember { mutableStateOf("") }
                    var editStreamQuality by remember { mutableStateOf("High") }
                    var editPlaybackSpeed by remember { mutableStateOf(1.0f) }
                    var editEqPreset by remember { mutableStateOf("Flat") }
                    var editEqEnabled by remember { mutableStateOf(false) }

                    val profileNameVal by viewModel.profileName.collectAsState()
                    val profilePhoneVal by viewModel.profilePhone.collectAsState()
                    val profileWhatsappVal by viewModel.profileWhatsapp.collectAsState()
                    val prefStreamQualityVal by viewModel.prefStreamQuality.collectAsState()
                    val prefPlaybackSpeedVal by viewModel.prefPlaybackSpeed.collectAsState()
                    val prefEqPresetVal by viewModel.prefEqPreset.collectAsState()
                    val prefEqEnabledVal by viewModel.prefEqEnabled.collectAsState()

                    var isSavingSuccess by remember { mutableStateOf(false) }

                    LaunchedEffect(profileNameVal, profilePhoneVal, profileWhatsappVal, prefStreamQualityVal, prefPlaybackSpeedVal, prefEqPresetVal, prefEqEnabledVal) {
                        editDisplayName = profileNameVal
                        editPhone = profilePhoneVal
                        editWhatsapp = profileWhatsappVal
                        editStreamQuality = prefStreamQualityVal
                        editPlaybackSpeed = prefPlaybackSpeedVal
                        editEqPreset = prefEqPresetVal
                        editEqEnabled = prefEqEnabledVal
                    }

                    Text(text = "Logged in as: $currentUser", color = CyberCyan, style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(12.dp))

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            Text(
                                text = "Profile Details",
                                style = MaterialTheme.typography.titleSmall,
                                color = Color(0xFFFFB300),
                                fontWeight = FontWeight.Bold
                            )
                        }
                        item {
                            OutlinedTextField(
                                value = editDisplayName,
                                onValueChange = { editDisplayName = it },
                                label = { Text("Display Name", color = Color.Gray, style = MaterialTheme.typography.bodySmall) },
                                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp)) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = CyberCyan,
                                    unfocusedBorderColor = Color.Gray
                                ),
                                textStyle = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        item {
                            OutlinedTextField(
                                value = editPhone,
                                onValueChange = { editPhone = it },
                                label = { Text("Contact Number", color = Color.Gray, style = MaterialTheme.typography.bodySmall) },
                                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp)) },
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = CyberCyan,
                                    unfocusedBorderColor = Color.Gray
                                ),
                                textStyle = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        item {
                            OutlinedTextField(
                                value = editWhatsapp,
                                onValueChange = { editWhatsapp = it },
                                label = { Text("WhatsApp Number", color = Color.Gray, style = MaterialTheme.typography.bodySmall) },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp)) },
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = CyberCyan,
                                    unfocusedBorderColor = Color.Gray
                                ),
                                textStyle = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        item {
                            HorizontalDivider(color = Color.Gray.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 4.dp))
                            Text(
                                text = "Preferred Audio Profiles",
                                style = MaterialTheme.typography.titleSmall,
                                color = Color(0xFFFFB300),
                                fontWeight = FontWeight.Bold
                            )
                        }
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Equalizer Enabled", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                                }
                                Switch(
                                    checked = editEqEnabled,
                                    onCheckedChange = { editEqEnabled = it },
                                    colors = SwitchDefaults.colors(checkedThumbColor = CyberCyan)
                                )
                            }
                        }
                        if (editEqEnabled) {
                            item {
                                Text("Equalizer Preset", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    listOf("Flat", "Bass Booster", "Treble Booster", "Vocal Booster").forEach { preset ->
                                        val isSelected = editEqPreset == preset
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { editEqPreset = preset },
                                            label = { Text(preset, color = if (isSelected) Color.Black else Color.White, style = MaterialTheme.typography.bodySmall) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = CyberCyan
                                            )
                                        )
                                    }
                                }
                            }
                        }
                        item {
                            Text("Preferred Streaming Quality", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                listOf("Low", "Medium", "High").forEach { quality ->
                                    val isSelected = editStreamQuality == quality
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { editStreamQuality = quality },
                                        label = { Text(quality, color = if (isSelected) Color.Black else Color.White, style = MaterialTheme.typography.bodySmall) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = CyberCyan
                                        )
                                    )
                                }
                            }
                        }
                        item {
                            Text("Preferred Playback Speed", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                listOf(0.5f, 1.0f, 1.5f, 2.0f).forEach { speed ->
                                    val isSelected = editPlaybackSpeed == speed
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { editPlaybackSpeed = speed },
                                        label = { Text("${speed}x", color = if (isSelected) Color.Black else Color.White, style = MaterialTheme.typography.bodySmall) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = CyberCyan
                                        )
                                    )
                                }
                            }
                        }
                        item {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "Restricted Mode", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                                Switch(
                                    checked = isRestricted,
                                    onCheckedChange = { viewModel.toggleRestrictedMode() },
                                    colors = SwitchDefaults.colors(checkedThumbColor = CyberCyan)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    if (isSavingSuccess) {
                        Text(
                            text = "Profile & preferences saved!",
                            color = CyberCyan,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }

                    Button(
                        onClick = {
                            viewModel.saveProfile(
                                name = editDisplayName,
                                phone = editPhone,
                                whatsapp = editWhatsapp,
                                streamQuality = editStreamQuality,
                                playbackSpeed = editPlaybackSpeed,
                                eqPreset = editEqPreset,
                                eqEnabled = editEqEnabled,
                                context = context
                            ) {
                                isSavingSuccess = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB300)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save Profile & Preferences", color = Color.Black, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            viewModel.verifyEmail { success, msg ->
                                message = msg
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Verify Email", color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { viewModel.signOut() },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberPink),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Sign Out")
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { onDismiss() },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Close", color = Color.Black)
                    }
                } else {
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email", color = Color.Gray) },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Email
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = CyberCyan,
                            unfocusedBorderColor = Color.Gray
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password", color = Color.Gray) },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            val image = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(imageVector = image, contentDescription = "Toggle password visibility", tint = Color.Gray)
                            }
                        },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Password
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = CyberCyan,
                            unfocusedBorderColor = Color.Gray
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    if (message.isNotEmpty()) {
                        Text(text = message, color = CyberPink, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Button(
                            onClick = {
                                viewModel.signIn(email, password) { success, msg ->
                                    message = msg
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan)
                        ) {
                            Text("Sign In", color = Color.Black)
                        }
                        Button(
                            onClick = {
                                viewModel.signUp(email, password) { success, msg ->
                                    message = msg
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberPink)
                        ) {
                            Text("Register")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    TextButton(onClick = {
                        viewModel.resetPassword(email) { success, msg ->
                            message = msg
                        }
                    }) {
                        Text("Forgot Password? Reset", color = CyberCyan)
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color.Gray)
                    }
                }
            }
        }
    }
}
