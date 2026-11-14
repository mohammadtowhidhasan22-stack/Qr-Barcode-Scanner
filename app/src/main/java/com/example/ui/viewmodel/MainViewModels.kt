package com.example.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.ScanHistoryEntity
import com.example.data.model.QrGeneratorType
import com.example.data.model.ScanContentType
import com.example.data.model.ScanResultData
import com.example.data.model.ScanSource
import com.example.data.model.SocialPlatform
import com.example.data.model.ThemeMode
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.preferences.UserSettings
import com.example.data.repository.ScanHistoryRepository
import com.example.util.BarcodeParser
import com.example.util.GalleryBarcodeScanner
import com.example.util.QrGeneratorUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val preferencesRepository: UserPreferencesRepository
) : ViewModel() {

    val settings: StateFlow<UserSettings> = preferencesRepository.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UserSettings()
        )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferencesRepository.setThemeMode(mode) }
    }

    fun setSoundEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setSoundEnabled(enabled) }
    }

    fun setVibrationEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setVibrationEnabled(enabled) }
    }

    fun setAutoScanEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setAutoScanEnabled(enabled) }
    }

    fun setSaveHistoryEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setSaveHistoryEnabled(enabled) }
    }
}

enum class HistoryFilter(val label: String) {
    ALL("All"),
    URL("Links"),
    BARCODE("Barcodes"),
    WIFI("Wi-Fi"),
    CONTACT("Contacts"),
    TEXT("Text & Other")
}

data class HistoryUiState(
    val items: List<ScanHistoryEntity> = emptyList(),
    val searchQuery: String = "",
    val selectedFilter: HistoryFilter = HistoryFilter.ALL,
    val errorMessage: String? = null
)

class HistoryViewModel(
    private val historyRepository: ScanHistoryRepository
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val selectedFilter = MutableStateFlow(HistoryFilter.ALL)
    private val errorMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<HistoryUiState> = combine(
        historyRepository.allScans.catch { emit(emptyList()) },
        searchQuery,
        selectedFilter,
        errorMessage
    ) { allItems, query, filter, err ->
        val filtered = allItems.filter { item ->
            val matchesFilter = when (filter) {
                HistoryFilter.ALL -> true
                HistoryFilter.URL -> item.contentType == ScanContentType.URL.name
                HistoryFilter.BARCODE -> item.contentType == ScanContentType.PRODUCT_BARCODE.name
                HistoryFilter.WIFI -> item.contentType == ScanContentType.WIFI.name
                HistoryFilter.CONTACT -> item.contentType == ScanContentType.CONTACT.name
                HistoryFilter.TEXT -> item.contentType in setOf(
                    ScanContentType.TEXT.name,
                    ScanContentType.EMAIL.name,
                    ScanContentType.PHONE.name,
                    ScanContentType.SMS.name,
                    ScanContentType.GEO.name
                )
            }
            val q = query.trim()
            val matchesQuery = q.isBlank() ||
                item.rawValue.contains(q, ignoreCase = true) ||
                item.displayTitle.contains(q, ignoreCase = true) ||
                item.formatName.contains(q, ignoreCase = true)
            matchesFilter && matchesQuery
        }
        HistoryUiState(
            items = filtered,
            searchQuery = query,
            selectedFilter = filter,
            errorMessage = err
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HistoryUiState()
    )

    val recentScans: StateFlow<List<ScanHistoryEntity>> = historyRepository.recentScans(3)
        .catch { emit(emptyList()) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val totalCount: StateFlow<Int> = historyRepository.allScans
        .catch { emit(emptyList()) }
        .combine(MutableStateFlow(Unit)) { list, _ -> list.size }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    fun updateSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun selectFilter(filter: HistoryFilter) {
        selectedFilter.value = filter
    }

    fun deleteItem(id: Long) {
        viewModelScope.launch {
            historyRepository.deleteById(id).onFailure { e ->
                errorMessage.value = e.localizedMessage ?: "Failed to delete scan record."
            }
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            historyRepository.clearAll().onFailure { e ->
                errorMessage.value = e.localizedMessage ?: "Failed to clear scan history."
            }
        }
    }

    fun clearError() {
        errorMessage.value = null
    }
}

class ScanWorkflowViewModel(
    private val historyRepository: ScanHistoryRepository,
    private val preferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _currentResult = MutableStateFlow<ScanResultData?>(null)
    val currentResult: StateFlow<ScanResultData?> = _currentResult.asStateFlow()

    private val _isSavedToHistory = MutableStateFlow(false)
    val isSavedToHistory: StateFlow<Boolean> = _isSavedToHistory.asStateFlow()

    private val _galleryScanning = MutableStateFlow(false)
    val galleryScanning: StateFlow<Boolean> = _galleryScanning.asStateFlow()

    private val _galleryError = MutableStateFlow<String?>(null)
    val galleryError: StateFlow<String?> = _galleryError.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    fun onCodeDetected(
        rawValue: String,
        formatCode: Int,
        source: ScanSource,
        autoSave: Boolean,
        onReady: () -> Unit
    ) {
        val parsed = BarcodeParser.parse(
            rawValue = rawValue,
            formatCode = formatCode,
            timestamp = System.currentTimeMillis(),
            source = source
        )
        _currentResult.value = parsed
        _isSavedToHistory.value = false

        if (autoSave) {
            viewModelScope.launch {
                historyRepository.saveScan(parsed)
                    .onSuccess { insertedId ->
                        _currentResult.value = parsed.copy(historyId = insertedId)
                        _isSavedToHistory.value = true
                    }
                    .onFailure {
                        _statusMessage.value = "Could not save scan to local history."
                    }
                onReady()
            }
        } else {
            onReady()
        }
    }

    fun openFromHistory(entity: ScanHistoryEntity, onReady: () -> Unit) {
        val source = runCatching { ScanSource.valueOf(entity.source) }.getOrDefault(ScanSource.CAMERA)
        val parsed = BarcodeParser.parse(
            rawValue = entity.rawValue,
            formatCode = entity.formatCode,
            timestamp = entity.timestamp,
            source = source,
            historyId = entity.id
        )
        _currentResult.value = parsed
        _isSavedToHistory.value = true
        onReady()
    }

    fun saveCurrentResultToHistory() {
        val current = _currentResult.value ?: return
        if (_isSavedToHistory.value) return
        viewModelScope.launch {
            historyRepository.saveScan(current)
                .onSuccess { id ->
                    _currentResult.value = current.copy(historyId = id)
                    _isSavedToHistory.value = true
                    _statusMessage.value = "Saved to Scan History"
                }
                .onFailure { e ->
                    _statusMessage.value = e.localizedMessage ?: "Failed to save to database."
                }
        }
    }

    fun scanFromGalleryUri(
        context: Context,
        uri: Uri,
        autoSave: Boolean,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _galleryScanning.value = true
            _galleryError.value = null
            GalleryBarcodeScanner.scanFromUri(context, uri)
                .onSuccess { result ->
                    _galleryScanning.value = false
                    onCodeDetected(
                        rawValue = result.rawValue,
                        formatCode = result.formatCode,
                        source = ScanSource.GALLERY,
                        autoSave = autoSave,
                        onReady = onSuccess
                    )
                }
                .onFailure { error ->
                    _galleryScanning.value = false
                    _galleryError.value = error.message
                        ?: "No QR code or barcode could be detected in the selected image."
                }
        }
    }

    fun dismissGalleryError() {
        _galleryError.value = null
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }
}

data class QrGeneratorUiState(
    val selectedType: QrGeneratorType = QrGeneratorType.URL,
    val urlInput: String = "https://",
    val textInput: String = "",
    val phoneInput: String = "",
    val emailAddress: String = "",
    val emailSubject: String = "",
    val emailBody: String = "",
    val wifiSsid: String = "",
    val wifiPassword: String = "",
    val wifiEncryption: String = "WPA",
    val wifiHidden: Boolean = false,
    val contactName: String = "",
    val contactPhone: String = "",
    val contactEmail: String = "",
    val contactOrg: String = "",
    val socialPlatform: SocialPlatform = SocialPlatform.INSTAGRAM,
    val socialHandle: String = "",
    val foregroundColor: Color = Color(0xFF0F172A),
    val backgroundColor: Color = Color(0xFFFFFFFF),
    val sizePx: Int = 1024,
    val previewBitmap: Bitmap? = null,
    val encodedPayload: String = "",
    val validationHint: String? = "Enter a valid website URL to generate your QR code.",
    val statusMessage: String? = null
)

class QrGeneratorViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(QrGeneratorUiState())
    val uiState: StateFlow<QrGeneratorUiState> = _uiState.asStateFlow()

    fun selectType(type: QrGeneratorType) {
        _uiState.update { it.copy(selectedType = type) }
        regeneratePreview()
    }

    fun updateUrl(value: String) {
        _uiState.update { it.copy(urlInput = value) }
        regeneratePreview()
    }

    fun updateText(value: String) {
        _uiState.update { it.copy(textInput = value) }
        regeneratePreview()
    }

    fun updatePhone(value: String) {
        _uiState.update { it.copy(phoneInput = value) }
        regeneratePreview()
    }

    fun updateEmail(address: String? = null, subject: String? = null, body: String? = null) {
        _uiState.update {
            it.copy(
                emailAddress = address ?: it.emailAddress,
                emailSubject = subject ?: it.emailSubject,
                emailBody = body ?: it.emailBody
            )
        }
        regeneratePreview()
    }

    fun updateWifi(
        ssid: String? = null,
        password: String? = null,
        encryption: String? = null,
        hidden: Boolean? = null
    ) {
        _uiState.update {
            it.copy(
                wifiSsid = ssid ?: it.wifiSsid,
                wifiPassword = password ?: it.wifiPassword,
                wifiEncryption = encryption ?: it.wifiEncryption,
                wifiHidden = hidden ?: it.wifiHidden
            )
        }
        regeneratePreview()
    }

    fun updateContact(
        name: String? = null,
        phone: String? = null,
        email: String? = null,
        org: String? = null
    ) {
        _uiState.update {
            it.copy(
                contactName = name ?: it.contactName,
                contactPhone = phone ?: it.contactPhone,
                contactEmail = email ?: it.contactEmail,
                contactOrg = org ?: it.contactOrg
            )
        }
        regeneratePreview()
    }

    fun updateSocial(platform: SocialPlatform? = null, handle: String? = null) {
        _uiState.update {
            it.copy(
                socialPlatform = platform ?: it.socialPlatform,
                socialHandle = handle ?: it.socialHandle
            )
        }
        regeneratePreview()
    }

    fun setForegroundColor(color: Color) {
        _uiState.update { it.copy(foregroundColor = color) }
        regeneratePreview()
    }

    fun setBackgroundColor(color: Color) {
        _uiState.update { it.copy(backgroundColor = color) }
        regeneratePreview()
    }

    fun setSizePx(size: Int) {
        _uiState.update { it.copy(sizePx = size) }
        regeneratePreview()
    }

    fun savePng(context: Context, onNeedSafPicker: () -> Unit) {
        val state = _uiState.value
        val payload = state.encodedPayload
        if (payload.isBlank()) {
            _uiState.update { it.copy(statusMessage = "Enter content first to save a QR code.") }
            return
        }
        viewModelScope.launch {
            val exportBitmap = QrGeneratorUtil.generateQrBitmap(
                content = payload,
                sizePx = state.sizePx,
                foregroundColor = state.foregroundColor.toArgb(),
                backgroundColor = state.backgroundColor.toArgb()
            ).getOrElse {
                _uiState.update { it.copy(statusMessage = "Failed to render QR code image.") }
                return@launch
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                QrGeneratorUtil.saveQrToGallery(context, exportBitmap)
                    .onSuccess { msg ->
                        _uiState.update { it.copy(statusMessage = msg) }
                    }
                    .onFailure { err ->
                        _uiState.update {
                            it.copy(statusMessage = err.localizedMessage ?: "Failed to save PNG image.")
                        }
                    }
            } else {
                onNeedSafPicker()
            }
        }
    }

    fun savePngToSafUri(context: Context, uri: Uri) {
        val state = _uiState.value
        val payload = state.encodedPayload
        if (payload.isBlank()) return
        viewModelScope.launch {
            val exportBitmap = QrGeneratorUtil.generateQrBitmap(
                content = payload,
                sizePx = state.sizePx,
                foregroundColor = state.foregroundColor.toArgb(),
                backgroundColor = state.backgroundColor.toArgb()
            ).getOrElse {
                _uiState.update { it.copy(statusMessage = "Failed to render QR code image.") }
                return@launch
            }
            QrGeneratorUtil.writeBitmapToUri(context, uri, exportBitmap)
                .onSuccess {
                    _uiState.update { it.copy(statusMessage = "QR code PNG saved successfully.") }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(statusMessage = err.localizedMessage ?: "Failed to save PNG.")
                    }
                }
        }
    }

    fun sharePng(context: Context) {
        val state = _uiState.value
        val payload = state.encodedPayload
        if (payload.isBlank()) {
            _uiState.update { it.copy(statusMessage = "Enter content first to share a QR code.") }
            return
        }
        viewModelScope.launch {
            val exportBitmap = QrGeneratorUtil.generateQrBitmap(
                content = payload,
                sizePx = state.sizePx,
                foregroundColor = state.foregroundColor.toArgb(),
                backgroundColor = state.backgroundColor.toArgb()
            ).getOrElse {
                _uiState.update { it.copy(statusMessage = "Failed to render QR code for sharing.") }
                return@launch
            }
            QrGeneratorUtil.shareQrBitmap(context, exportBitmap)
                .onFailure { err ->
                    _uiState.update {
                        it.copy(statusMessage = err.localizedMessage ?: "Unable to share QR code.")
                    }
                }
        }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    private fun regeneratePreview() {
        val state = _uiState.value
        val (payload, hint) = buildPayload(state)
        if (payload.isBlank()) {
            _uiState.update {
                it.copy(
                    encodedPayload = "",
                    previewBitmap = null,
                    validationHint = hint
                )
            }
            return
        }

        // Preview uses 512px for instant rendering even when export size is 2048px
        val bitmapResult = QrGeneratorUtil.generateQrBitmap(
            content = payload,
            sizePx = 512,
            foregroundColor = state.foregroundColor.toArgb(),
            backgroundColor = state.backgroundColor.toArgb()
        )
        _uiState.update {
            it.copy(
                encodedPayload = payload,
                previewBitmap = bitmapResult.getOrNull(),
                validationHint = if (bitmapResult.isFailure) {
                    "Content is too long or invalid for QR encoding."
                } else {
                    null
                }
            )
        }
    }

    private fun buildPayload(state: QrGeneratorUiState): Pair<String, String?> {
        return when (state.selectedType) {
            QrGeneratorType.URL -> {
                val trimmed = state.urlInput.trim()
                if (trimmed.isBlank() || trimmed == "https://" || trimmed == "http://") {
                    "" to "Enter a website URL (e.g., https://example.com)."
                } else {
                    val normalized = if (trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true)) {
                        trimmed
                    } else {
                        "https://$trimmed"
                    }
                    normalized to null
                }
            }

            QrGeneratorType.TEXT -> {
                val trimmed = state.textInput.trim()
                if (trimmed.isBlank()) "" to "Enter text or a message to encode."
                else trimmed to null
            }

            QrGeneratorType.PHONE -> {
                val trimmed = state.phoneInput.trim()
                if (trimmed.isBlank()) "" to "Enter a phone number."
                else "tel:$trimmed" to null
            }

            QrGeneratorType.EMAIL -> {
                val addr = state.emailAddress.trim()
                if (addr.isBlank()) "" to "Enter a recipient email address."
                else {
                    val params = buildList {
                        if (state.emailSubject.isNotBlank()) {
                            add("subject=${Uri.encode(state.emailSubject.trim())}")
                        }
                        if (state.emailBody.isNotBlank()) {
                            add("body=${Uri.encode(state.emailBody.trim())}")
                        }
                    }
                    val suffix = if (params.isNotEmpty()) "?${params.joinToString("&")}" else ""
                    "mailto:$addr$suffix" to null
                }
            }

            QrGeneratorType.WIFI -> {
                val ssid = state.wifiSsid.trim()
                if (ssid.isBlank()) "" to "Enter the Wi-Fi network name (SSID)."
                else {
                    val enc = when (state.wifiEncryption) {
                        "Open" -> "nopass"
                        "WEP" -> "WEP"
                        else -> "WPA"
                    }
                    val escSsid = escapeWifi(ssid)
                    val escPass = if (enc == "nopass") "" else escapeWifi(state.wifiPassword)
                    val hiddenFlag = if (state.wifiHidden) "true" else "false"
                    "WIFI:T:$enc;S:$escSsid;P:$escPass;H:$hiddenFlag;;" to null
                }
            }

            QrGeneratorType.CONTACT -> {
                val name = state.contactName.trim()
                val phone = state.contactPhone.trim()
                val email = state.contactEmail.trim()
                val org = state.contactOrg.trim()
                if (name.isBlank() && phone.isBlank() && email.isBlank()) {
                    "" to "Enter at least a name, phone, or email."
                } else {
                    buildString {
                        appendLine("BEGIN:VCARD")
                        appendLine("VERSION:3.0")
                        if (name.isNotBlank()) appendLine("FN:$name")
                        if (org.isNotBlank()) appendLine("ORG:$org")
                        if (phone.isNotBlank()) appendLine("TEL:$phone")
                        if (email.isNotBlank()) appendLine("EMAIL:$email")
                        append("END:VCARD")
                    } to null
                }
            }

            QrGeneratorType.SOCIAL -> {
                val handle = state.socialHandle.trim().removePrefix("@")
                if (handle.isBlank()) {
                    "" to "Enter a username or social profile link."
                } else if (state.socialPlatform == SocialPlatform.CUSTOM) {
                    val url = if (handle.startsWith("http://", true) || handle.startsWith("https://", true)) {
                        handle
                    } else {
                        "https://$handle"
                    }
                    url to null
                } else if (handle.startsWith("http://", true) || handle.startsWith("https://", true)) {
                    handle to null
                } else {
                    "${state.socialPlatform.baseUrl}$handle" to null
                }
            }
        }
    }

    private fun escapeWifi(value: String): String = value
        .replace("\\", "\\\\")
        .replace(";", "\\;")
        .replace(",", "\\,")
        .replace(":", "\\:")
}

class AppViewModelFactory(
    private val historyRepository: ScanHistoryRepository,
    private val preferencesRepository: UserPreferencesRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(SettingsViewModel::class.java) ->
                SettingsViewModel(preferencesRepository) as T
            modelClass.isAssignableFrom(HistoryViewModel::class.java) ->
                HistoryViewModel(historyRepository) as T
            modelClass.isAssignableFrom(ScanWorkflowViewModel::class.java) ->
                ScanWorkflowViewModel(historyRepository, preferencesRepository) as T
            modelClass.isAssignableFrom(QrGeneratorViewModel::class.java) ->
                QrGeneratorViewModel() as T
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
