package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.QrGeneratorType
import com.example.data.model.SocialPlatform
import com.example.ui.viewmodel.QrGeneratorUiState
import com.example.ui.viewmodel.QrGeneratorViewModel
import com.example.util.ActionLauncher
import kotlin.math.abs

private val ForegroundColorOptions = listOf(
    "Black" to Color(0xFF0F172A),
    "Blue" to Color(0xFF1D4ED8),
    "Sky" to Color(0xFF0284C7),
    "Emerald" to Color(0xFF059669),
    "Crimson" to Color(0xFFDC2626),
    "Indigo" to Color(0xFF4338CA)
)

private val BackgroundColorOptions = listOf(
    "White" to Color(0xFFFFFFFF),
    "Ice Blue" to Color(0xFFEFF6FF),
    "Slate" to Color(0xFFF1F5F9),
    "Cream" to Color(0xFFFFFBEB),
    "Dark Slate" to Color(0xFF0F172A)
)

private val SizeOptions = listOf(512, 1024, 1536, 2048)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrGeneratorScreen(
    uiState: QrGeneratorUiState,
    viewModel: QrGeneratorViewModel,
    onBack: () -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/png")
    ) { uri ->
        if (uri != null) {
            viewModel.savePngToSafUri(context, uri)
        }
    }

    LaunchedEffect(uiState.statusMessage) {
        uiState.statusMessage?.let { msg ->
            onShowMessage(msg)
            viewModel.clearStatusMessage()
        }
    }

    val lowContrast = abs(uiState.foregroundColor.luminance() - uiState.backgroundColor.luminance()) < 0.25f

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("QR Code Generator") },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("generator_back_button")
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
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // QR Type Selector
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Select QR Type",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            QrGeneratorType.entries.forEach { type ->
                                FilterChip(
                                    selected = uiState.selectedType == type,
                                    onClick = { viewModel.selectType(type) },
                                    label = { Text(type.label) },
                                    modifier = Modifier.testTag("generator_type_${type.name.lowercase()}")
                                )
                            }
                        }
                    }
                }

                // Dynamic Form Inputs
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            when (uiState.selectedType) {
                                QrGeneratorType.URL -> {
                                    OutlinedTextField(
                                        value = uiState.urlInput,
                                        onValueChange = viewModel::updateUrl,
                                        label = { Text("Website URL") },
                                        placeholder = { Text("https://example.com") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("generator_url_input")
                                    )
                                }

                                QrGeneratorType.TEXT -> {
                                    OutlinedTextField(
                                        value = uiState.textInput,
                                        onValueChange = viewModel::updateText,
                                        label = { Text("Text Message") },
                                        placeholder = { Text("Enter any note or message...") },
                                        minLines = 3,
                                        maxLines = 5,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("generator_text_input")
                                    )
                                }

                                QrGeneratorType.PHONE -> {
                                    OutlinedTextField(
                                        value = uiState.phoneInput,
                                        onValueChange = viewModel::updatePhone,
                                        label = { Text("Phone Number") },
                                        placeholder = { Text("+1 555 012 3456") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("generator_phone_input")
                                    )
                                }

                                QrGeneratorType.EMAIL -> {
                                    OutlinedTextField(
                                        value = uiState.emailAddress,
                                        onValueChange = { viewModel.updateEmail(address = it) },
                                        label = { Text("Email Address") },
                                        placeholder = { Text("name@example.com") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("generator_email_address_input")
                                    )
                                    OutlinedTextField(
                                        value = uiState.emailSubject,
                                        onValueChange = { viewModel.updateEmail(subject = it) },
                                        label = { Text("Subject (Optional)") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = uiState.emailBody,
                                        onValueChange = { viewModel.updateEmail(body = it) },
                                        label = { Text("Message Body (Optional)") },
                                        minLines = 2,
                                        maxLines = 4,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                QrGeneratorType.WIFI -> {
                                    OutlinedTextField(
                                        value = uiState.wifiSsid,
                                        onValueChange = { viewModel.updateWifi(ssid = it) },
                                        label = { Text("Network Name (SSID)") },
                                        singleLine = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("generator_wifi_ssid_input")
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        listOf("WPA", "WEP", "Open").forEach { enc ->
                                            FilterChip(
                                                selected = uiState.wifiEncryption == enc,
                                                onClick = { viewModel.updateWifi(encryption = enc) },
                                                label = { Text(enc) }
                                            )
                                        }
                                    }
                                    if (uiState.wifiEncryption != "Open") {
                                        OutlinedTextField(
                                            value = uiState.wifiPassword,
                                            onValueChange = { viewModel.updateWifi(password = it) },
                                            label = { Text("Wi-Fi Password") },
                                            singleLine = true,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("generator_wifi_password_input")
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Hidden Network", style = MaterialTheme.typography.bodyMedium)
                                        Switch(
                                            checked = uiState.wifiHidden,
                                            onCheckedChange = { viewModel.updateWifi(hidden = it) }
                                        )
                                    }
                                }

                                QrGeneratorType.CONTACT -> {
                                    OutlinedTextField(
                                        value = uiState.contactName,
                                        onValueChange = { viewModel.updateContact(name = it) },
                                        label = { Text("Full Name") },
                                        singleLine = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("generator_contact_name_input")
                                    )
                                    OutlinedTextField(
                                        value = uiState.contactPhone,
                                        onValueChange = { viewModel.updateContact(phone = it) },
                                        label = { Text("Phone Number") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = uiState.contactEmail,
                                        onValueChange = { viewModel.updateContact(email = it) },
                                        label = { Text("Email Address") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = uiState.contactOrg,
                                        onValueChange = { viewModel.updateContact(org = it) },
                                        label = { Text("Organization (Optional)") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                QrGeneratorType.SOCIAL -> {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        SocialPlatform.entries.forEach { platform ->
                                            FilterChip(
                                                selected = uiState.socialPlatform == platform,
                                                onClick = { viewModel.updateSocial(platform = platform) },
                                                label = { Text(platform.displayName) }
                                            )
                                        }
                                    }
                                    OutlinedTextField(
                                        value = uiState.socialHandle,
                                        onValueChange = { viewModel.updateSocial(handle = it) },
                                        label = { Text(uiState.socialPlatform.displayName) },
                                        placeholder = { Text(uiState.socialPlatform.hint) },
                                        singleLine = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("generator_social_input")
                                    )
                                }
                            }
                        }
                    }
                }

                // Live QR Preview Card
                item {
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Live QR Preview",
                                style = MaterialTheme.typography.titleMedium
                            )

                            if (uiState.previewBitmap != null) {
                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    shadowElevation = 2.dp
                                ) {
                                    Image(
                                        bitmap = uiState.previewBitmap.asImageBitmap(),
                                        contentDescription = "Generated QR Code Preview",
                                        modifier = Modifier
                                            .size(220.dp)
                                            .padding(8.dp)
                                    )
                                }
                                if (lowContrast) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.errorContainer
                                    ) {
                                        Text(
                                            text = "Low contrast between QR and background colors may make scanning difficult.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.padding(10.dp)
                                        )
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(200.dp)
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.padding(16.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.QrCode2,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Text(
                                            text = uiState.validationHint ?: "Enter details above",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Customization (QR Color, Background Color, Size)
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text("Customize Style", style = MaterialTheme.typography.titleSmall)

                            Text(
                                text = "QR Color",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.horizontalScroll(rememberScrollState())
                            ) {
                                ForegroundColorOptions.forEach { (name, color) ->
                                    ColorSwatch(
                                        color = color,
                                        selected = uiState.foregroundColor == color,
                                        contentDescription = "QR color $name",
                                        onClick = { viewModel.setForegroundColor(color) }
                                    )
                                }
                            }

                            Text(
                                text = "Background Color",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.horizontalScroll(rememberScrollState())
                            ) {
                                BackgroundColorOptions.forEach { (name, color) ->
                                    ColorSwatch(
                                        color = color,
                                        selected = uiState.backgroundColor == color,
                                        contentDescription = "Background color $name",
                                        onClick = { viewModel.setBackgroundColor(color) }
                                    )
                                }
                            }

                            Text(
                                text = "Export Size",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SizeOptions.forEach { size ->
                                    FilterChip(
                                        selected = uiState.sizePx == size,
                                        onClick = { viewModel.setSizePx(size) },
                                        label = { Text("${size}px") },
                                        modifier = Modifier.testTag("generator_size_$size")
                                    )
                                }
                            }
                        }
                    }
                }

                // Export Actions (Save PNG, Share, Copy Content)
                item {
                    val hasValidQr = uiState.previewBitmap != null && uiState.encodedPayload.isNotBlank()
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                viewModel.savePng(context) {
                                    createDocumentLauncher.launch("QRCode_${System.currentTimeMillis()}.png")
                                }
                            },
                            enabled = hasValidQr,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("generator_save_png_button")
                        ) {
                            Icon(Icons.Rounded.Download, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Save as PNG (${uiState.sizePx}px)")
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            FilledTonalButton(
                                onClick = { viewModel.sharePng(context) },
                                enabled = hasValidQr,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("generator_share_button")
                            ) {
                                Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Share QR")
                            }

                            OutlinedButton(
                                onClick = {
                                    if (ActionLauncher.copyToClipboard(context, "QR Content", uiState.encodedPayload)) {
                                        onShowMessage("Generated content copied.")
                                    }
                                },
                                enabled = hasValidQr,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("generator_copy_content_button")
                            ) {
                                Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Copy Content")
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(12.dp)) }
            }
        }
    }
}

@Composable
private fun ColorSwatch(
    color: Color,
    selected: Boolean,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape = CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = contentDescription,
                tint = if (color.luminance() > 0.5f) Color.Black else Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
