package com.osei.djmixer.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermsOfServiceScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Terms of Service") },
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
                text = "Terms of Service",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = """
                    Last updated: July 14, 2026

                    1. Acceptance of Terms
                    By downloading, installing, or using Osei DJ Mixer, you agree to be bound by these Terms of Service. If you do not agree to these terms, please do not use the application.

                    2. License to Use
                    We grant you a personal, non-exclusive, non-transferable, and revocable license to use the App for personal, non-commercial purposes in accordance with these Terms.

                    3. User Conduct
                    You agree not to use the App in any way that is unlawful, or harms Osei DJ Mixer, its service providers, its suppliers, or any other user. You must not use the App to distribute unauthorized copyrighted material.

                    4. Intellectual Property
                    The App, including its original content, features, and functionality, are owned by Osei DJ Mixer and are protected by international copyright, trademark, patent, trade secret, and other intellectual property or proprietary rights laws.

                    5. Disclaimer of Warranties
                    The App is provided on an "AS IS" and "AS AVAILABLE" basis. We make no warranties, expressed or implied, regarding the App's reliability, accuracy, or availability.

                    6. Limitation of Liability
                    In no event shall Osei DJ Mixer be liable for any indirect, incidental, special, consequential or punitive damages, including without limitation, loss of profits, data, use, goodwill, or other intangible losses, resulting from your access to or use of or inability to access or use the App.

                    7. Changes to Terms
                    We reserve the right to modify or replace these Terms at any time. We will provide notice of any significant changes.

                    8. Contact Us
                    If you have any questions about these Terms, please contact us at oseisolo59@gmail.com.
                """.trimIndent(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}
