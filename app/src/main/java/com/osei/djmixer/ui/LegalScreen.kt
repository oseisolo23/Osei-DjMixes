package com.osei.djmixer.ui

import com.osei.djmixer.ui.theme.*

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegalScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy Policy & Terms") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Privacy Policy",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Last updated: June 25, 2026\n\n" +
                        "This Privacy Policy describes how we collect, use, and handle your information when you use our Osei DJ Mixer application.\n\n" +
                        "1. Information Collection\n" +
                        "We may collect information you provide directly to us, such as when you create an account, save playlists, or contact support. The app accesses local media files on your device with your permission, and these remain on your device unless explicitly shared.\n\n" +
                        "2. Use of Information\n" +
                        "We use the information we collect to operate, maintain, and improve our services, and to provide you with the app's core features (e.g., DJ mixing and playback).\n\n" +
                        "3. Data Security\n" +
                        "We take reasonable measures to help protect information about you from loss, theft, misuse, and unauthorized access.\n\n" +
                        "4. Contact Us\n" +
                        "If you have any questions about this Privacy Policy, please contact us at oseisolo59@gmail.com.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Terms of Service",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "By using Osei DJ Mixer, you agree to these terms. If you don't agree, do not use the app.\n\n" +
                        "1. License\n" +
                        "We grant you a limited, non-exclusive, non-transferable, and revocable license to use our app.\n\n" +
                        "2. User Content\n" +
                        "You are responsible for the media you mix or play using our app. We do not claim ownership of your content.\n\n" +
                        "3. Prohibited Conduct\n" +
                        "You agree not to violate any laws, rights, or the Terms of Service. You may not reverse engineer, decompile, or disassemble any aspect of the app.\n\n" +
                        "4. Disclaimer\n" +
                        "The app is provided \"as is\" without warranties of any kind.\n\n" +
                        "5. Limitation of Liability\n" +
                        "We shall not be liable for any indirect, incidental, special, consequential, or punitive damages.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}
