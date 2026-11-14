package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.BookmarkAdded
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.ScanContentType
import com.example.data.model.ScanResultData
import com.example.ui.components.FormatBadge
import com.example.ui.components.UrlConfirmationDialog
import com.example.ui.components.contentTypeIcon
import com.example.ui.components.formatTimestamp
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SuccessGreen
import com.example.util.ActionLauncher
import java.net.URLEncoder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanResultScreen(
    result: ScanResultData?,
    isSavedToHistory: Boolean,
    onSaveToHistory: () -> Unit,
    onBack: () -> Unit,
    onScanAnother: () -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var pendingUrlConfirmation by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var pendingSuspiciousReason by remember { mutableStateOf<String?>(null) }
    var showWifiPassword by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Scan Result") },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("result_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        if (result == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("No scan result selected.", style = MaterialTheme.typography.titleMedium)
                    Button(onClick = onBack) { Text("Go Back") }
                }
            }
            return@Scaffold
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header Summary Card
                item {
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = contentTypeIcon(result.contentType),
                                            contentDescription = result.contentType.displayLabel,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = result.contentType.displayLabel,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            text = "${formatTimestamp(result.timestamp)} • ${result.source.label}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FormatBadge(label = result.formatName)
                                if (isSavedToHistory) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = SuccessGreen.copy(alpha = 0.14f)
                                    ) {
                                        Text(
                                            text = "Saved",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = SuccessGreen,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Structured Content Details (Wi-Fi, Contact, Email, Product Barcode)
                result.parsedWifi?.let { wifi ->
                    item {
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text("Wi-Fi Credentials", style = MaterialTheme.typography.titleSmall)
                                DetailRow("Network (SSID)", wifi.ssid.ifBlank { "Unnamed Network" })
                                DetailRow("Security", wifi.encryption)
                                if (wifi.isHidden) {
                                    DetailRow("Visibility", "Hidden Network")
                                }
                                if (wifi.password.isNotBlank()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Password",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = if (showWifiPassword) wifi.password else "•".repeat(wifi.password.length.coerceAtLeast(8)),
                                                style = MaterialTheme.typography.bodyLarge.copy(
                                                    fontFamily = JetBrainsMonoFontFamily
                                                )
                                            )
                                        }
                                        IconButton(onClick = { showWifiPassword = !showWifiPassword }) {
                                            Icon(
                                                imageVector = if (showWifiPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                                contentDescription = if (showWifiPassword) "Hide password" else "Show password"
                                            )
                                        }
                                        FilledTonalButton(
                                            onClick = {
                                                if (ActionLauncher.copyToClipboard(context, "Wi-Fi Password", wifi.password)) {
                                                    onShowMessage("Wi-Fi password copied.")
                                                }
                                            },
                                            modifier = Modifier.testTag("result_copy_wifi_password_button")
                                        ) {
                                            Text("Copy")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                result.parsedContact?.let { contact ->
                    item {
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("Contact Details", style = MaterialTheme.typography.titleSmall)
                                if (contact.name.isNotBlank()) DetailRow("Name", contact.name)
                                if (contact.organization.isNotBlank()) DetailRow("Organization", contact.organization)
                                if (contact.phone.isNotBlank()) DetailRow("Phone", contact.phone)
                                if (contact.email.isNotBlank()) DetailRow("Email", contact.email)
                                if (contact.address.isNotBlank()) DetailRow("Address", contact.address)
                            }
                        }
                    }
                }

                result.parsedEmail?.let { email ->
                    item {
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("Email Details", style = MaterialTheme.typography.titleSmall)
                                DetailRow("To", email.address)
                                if (email.subject.isNotBlank()) DetailRow("Subject", email.subject)
                                if (email.body.isNotBlank()) DetailRow("Message", email.body)
                            }
                        }
                    }
                }

                // Detected Raw Content Card
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = if (result.contentType == ScanContentType.PRODUCT_BARCODE) {
                                    "Barcode Number"
                                } else {
                                    "Detected Content"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = result.rawValue,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontFamily = JetBrainsMonoFontFamily
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(14.dp)
                                )
                            }
                            if (result.contentType == ScanContentType.PRODUCT_BARCODE) {
                                Text(
                                    text = "Format: ${result.formatName}. No external product database is connected, so product names or prices are not guessed.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Context-Specific Primary Actions
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // URL Open Link (with mandatory security confirmation dialog)
                        result.parsedUrl?.let { url ->
                            Button(
                                onClick = {
                                    pendingSuspiciousReason = result.suspiciousReason
                                    pendingUrlConfirmation = url to result.isSuspiciousUrl
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("result_open_link_button")
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Open Link")
                            }
                        }

                        // Phone Call
                        result.parsedPhone?.let { phone ->
                            Button(
                                onClick = {
                                    ActionLauncher.dialPhone(context, phone).onFailure {
                                        onShowMessage("No dialer app available on this device.")
                                    }
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("result_call_button")
                            ) {
                                Icon(Icons.Rounded.Call, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Call $phone")
                            }
                        }

                        // Send Email
                        result.parsedEmail?.let { email ->
                            Button(
                                onClick = {
                                    ActionLauncher.sendEmail(context, email).onFailure {
                                        onShowMessage("No email app available on this device.")
                                    }
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("result_send_email_button")
                            ) {
                                Icon(Icons.Rounded.Email, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Send Email")
                            }
                        }

                        // Add Contact
                        result.parsedContact?.let { contact ->
                            Button(
                                onClick = {
                                    ActionLauncher.insertContact(context, contact).onFailure {
                                        onShowMessage("No contacts app available on this device.")
                                    }
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("result_add_contact_button")
                            ) {
                                Icon(Icons.Rounded.PersonAdd, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Add Contact")
                            }
                        }

                        // Geo location
                        if (result.contentType == ScanContentType.GEO) {
                            Button(
                                onClick = {
                                    ActionLauncher.openGeoLocation(context, result.rawValue).onFailure {
                                        onShowMessage("No maps app available to open location.")
                                    }
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("result_open_map_button")
                            ) {
                                Icon(Icons.Rounded.LocationOn, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Open in Maps")
                            }
                        }

                        // Product barcode optional web lookup (always asks confirmation first)
                        if (result.contentType == ScanContentType.PRODUCT_BARCODE) {
                            FilledTonalButton(
                                onClick = {
                                    val encoded = runCatching {
                                        URLEncoder.encode(result.rawValue, "UTF-8")
                                    }.getOrDefault(result.rawValue)
                                    val searchUrl = "https://www.google.com/search?q=$encoded"
                                    pendingSuspiciousReason = null
                                    pendingUrlConfirmation = searchUrl to false
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("result_search_barcode_button")
                            ) {
                                Icon(Icons.Rounded.Search, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Search Barcode Online")
                            }
                        }

                        // Standard Copy & Share row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    if (ActionLauncher.copyToClipboard(context, "Scanned Code", result.rawValue)) {
                                        onShowMessage("Copied to clipboard.")
                                    } else {
                                        onShowMessage("Unable to access clipboard.")
                                    }
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("result_copy_button")
                            ) {
                                Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Copy")
                            }

                            FilledTonalButton(
                                onClick = {
                                    if (!ActionLauncher.shareText(context, result.rawValue)) {
                                        onShowMessage("Unable to open share sheet.")
                                    }
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("result_share_button")
                            ) {
                                Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Share")
                            }
                        }

                        // Save to History + Scan Another
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = onSaveToHistory,
                                enabled = !isSavedToHistory,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("result_save_history_button")
                            ) {
                                Icon(
                                    imageVector = if (isSavedToHistory) Icons.Rounded.BookmarkAdded else Icons.Rounded.BookmarkAdd,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (isSavedToHistory) "Saved" else "Save to History")
                            }

                            OutlinedButton(
                                onClick = onScanAnother,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("result_scan_another_button")
                            ) {
                                Text("Scan Another")
                            }
                        }
                    }
                }
            }
        }
    }

    pendingUrlConfirmation?.let { (url, suspicious) ->
        UrlConfirmationDialog(
            url = url,
            isSuspicious = suspicious,
            suspiciousReason = pendingSuspiciousReason,
            onConfirmOpen = {
                pendingUrlConfirmation = null
                ActionLauncher.openUrl(context, url).onFailure {
                    onShowMessage("No browser app found to open this link.")
                }
            },
            onDismiss = { pendingUrlConfirmation = null }
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
