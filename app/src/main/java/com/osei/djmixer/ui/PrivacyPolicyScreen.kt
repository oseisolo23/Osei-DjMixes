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
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy Policy") },
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
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = """
                    Last updated: July 14, 2026

                    1. Introduction
                    Welcome to Osei DJ Mixer. We are committed to protecting your personal information and your right to privacy.

                    2. Information We Collect
                    We collect personal information that you voluntarily provide to us when you register on the App, express an interest in obtaining information about us or our products and Services, when you participate in activities on the App or otherwise when you contact us.

                    3. How We Use Your Information
                    We use personal information collected via our App for a variety of business purposes described below. We process your personal information for these purposes in reliance on our legitimate business interests, in order to enter into or perform a contract with you, with your consent, and/or for compliance with our legal obligations.

                    4. Will Your Information Be Shared With Anyone?
                    We only share information with your consent, to comply with laws, to provide you with services, to protect your rights, or to fulfill business obligations.

                    5. How Long Do We Keep Your Information?
                    We keep your information for as long as necessary to fulfill the purposes outlined in this privacy notice unless otherwise required by law.

                    6. How Do We Keep Your Information Safe?
                    We aim to protect your personal information through a system of organizational and technical security measures.

                    7. Contact Us
                    If you have questions or comments about this notice, you may email us at oseisolo59@gmail.com.
                """.trimIndent(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}
