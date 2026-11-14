package com.example.data.model

enum class ScannerMode(val label: String) {
    ALL("All Codes"),
    QR_ONLY("QR Only"),
    BARCODE_ONLY("Barcodes")
}

enum class ScanContentType(val displayLabel: String) {
    URL("Website URL"),
    WIFI("Wi-Fi Network"),
    CONTACT("Contact Card"),
    EMAIL("Email Address"),
    PHONE("Phone Number"),
    SMS("SMS Message"),
    GEO("Location"),
    PRODUCT_BARCODE("Product Barcode"),
    TEXT("Plain Text")
}

enum class ScanSource(val label: String) {
    CAMERA("Camera"),
    GALLERY("Gallery")
}

enum class ThemeMode(val label: String) {
    SYSTEM("System Default"),
    LIGHT("Light Mode"),
    DARK("Dark Mode")
}

enum class QrGeneratorType(val label: String) {
    URL("Website URL"),
    TEXT("Text"),
    PHONE("Phone"),
    EMAIL("Email"),
    WIFI("Wi-Fi"),
    CONTACT("Contact"),
    SOCIAL("Social Media")
}

enum class SocialPlatform(val displayName: String, val baseUrl: String, val hint: String) {
    INSTAGRAM("Instagram", "https://instagram.com/", "username"),
    X_TWITTER("X / Twitter", "https://x.com/", "username"),
    LINKEDIN("LinkedIn", "https://linkedin.com/in/", "username"),
    YOUTUBE("YouTube", "https://youtube.com/@", "handle"),
    TIKTOK("TikTok", "https://tiktok.com/@", "username"),
    GITHUB("GitHub", "https://github.com/", "username"),
    FACEBOOK("Facebook", "https://facebook.com/", "username"),
    CUSTOM("Custom Social Link", "", "https://...")
}

data class ParsedWifi(
    val ssid: String,
    val password: String,
    val encryption: String,
    val isHidden: Boolean
)

data class ParsedContact(
    val name: String,
    val phone: String,
    val email: String,
    val organization: String,
    val address: String
)

data class ParsedEmail(
    val address: String,
    val subject: String,
    val body: String
)

data class ParsedSms(
    val number: String,
    val message: String
)

data class ScanResultData(
    val rawValue: String,
    val formatName: String,
    val formatCode: Int,
    val contentType: ScanContentType,
    val displayTitle: String,
    val timestamp: Long,
    val source: ScanSource,
    val historyId: Long? = null,
    val parsedUrl: String? = null,
    val isSuspiciousUrl: Boolean = false,
    val suspiciousReason: String? = null,
    val parsedWifi: ParsedWifi? = null,
    val parsedContact: ParsedContact? = null,
    val parsedEmail: ParsedEmail? = null,
    val parsedPhone: String? = null,
    val parsedSms: ParsedSms? = null
)
