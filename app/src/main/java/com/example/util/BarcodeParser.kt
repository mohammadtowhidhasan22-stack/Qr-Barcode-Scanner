package com.example.util

import android.net.Uri
import android.util.Patterns
import com.example.data.model.ParsedContact
import com.example.data.model.ParsedEmail
import com.example.data.model.ParsedSms
import com.example.data.model.ParsedWifi
import com.example.data.model.ScanContentType
import com.example.data.model.ScanResultData
import com.example.data.model.ScanSource
import com.google.mlkit.vision.barcode.common.Barcode
import java.net.URLDecoder

object BarcodeParser {

    fun formatName(formatCode: Int): String = when (formatCode) {
        Barcode.FORMAT_QR_CODE -> "QR Code"
        Barcode.FORMAT_EAN_13 -> "EAN-13"
        Barcode.FORMAT_EAN_8 -> "EAN-8"
        Barcode.FORMAT_UPC_A -> "UPC-A"
        Barcode.FORMAT_UPC_E -> "UPC-E"
        Barcode.FORMAT_CODE_128 -> "Code 128"
        Barcode.FORMAT_CODE_39 -> "Code 39"
        Barcode.FORMAT_CODE_93 -> "Code 93"
        Barcode.FORMAT_CODABAR -> "Codabar"
        Barcode.FORMAT_ITF -> "ITF"
        Barcode.FORMAT_PDF417 -> "PDF417"
        Barcode.FORMAT_AZTEC -> "Aztec"
        Barcode.FORMAT_DATA_MATRIX -> "Data Matrix"
        else -> "Barcode"
    }

    fun isProductBarcodeFormat(formatCode: Int): Boolean = formatCode in setOf(
        Barcode.FORMAT_EAN_13,
        Barcode.FORMAT_EAN_8,
        Barcode.FORMAT_UPC_A,
        Barcode.FORMAT_UPC_E
    )

    fun is1DOrDataBarcodeFormat(formatCode: Int): Boolean =
        formatCode != Barcode.FORMAT_QR_CODE && formatCode != Barcode.FORMAT_UNKNOWN

    fun parse(
        rawValue: String,
        formatCode: Int,
        timestamp: Long = System.currentTimeMillis(),
        source: ScanSource = ScanSource.CAMERA,
        historyId: Long? = null
    ): ScanResultData {
        val trimmed = rawValue.trim()
        val fmtName = formatName(formatCode)

        // 1. Wi-Fi (WIFI:T:WPA;S:MyNetwork;P:Secret;H:false;;)
        if (trimmed.startsWith("WIFI:", ignoreCase = true)) {
            val wifi = parseWifi(trimmed)
            return ScanResultData(
                rawValue = rawValue,
                formatName = fmtName,
                formatCode = formatCode,
                contentType = ScanContentType.WIFI,
                displayTitle = wifi.ssid.ifBlank { "Wi-Fi Network" },
                timestamp = timestamp,
                source = source,
                historyId = historyId,
                parsedWifi = wifi
            )
        }

        // 2. Contact (vCard or MECARD)
        if (trimmed.startsWith("BEGIN:VCARD", ignoreCase = true)) {
            val contact = parseVCard(trimmed)
            return ScanResultData(
                rawValue = rawValue,
                formatName = fmtName,
                formatCode = formatCode,
                contentType = ScanContentType.CONTACT,
                displayTitle = contact.name.ifBlank { contact.organization.ifBlank { "Contact Card" } },
                timestamp = timestamp,
                source = source,
                historyId = historyId,
                parsedContact = contact
            )
        }

        if (trimmed.startsWith("MECARD:", ignoreCase = true)) {
            val contact = parseMeCard(trimmed)
            return ScanResultData(
                rawValue = rawValue,
                formatName = fmtName,
                formatCode = formatCode,
                contentType = ScanContentType.CONTACT,
                displayTitle = contact.name.ifBlank { "Contact Card" },
                timestamp = timestamp,
                source = source,
                historyId = historyId,
                parsedContact = contact
            )
        }

        // 3. Email (mailto: or plain email)
        if (trimmed.startsWith("mailto:", ignoreCase = true) ||
            trimmed.startsWith("MATMSG:", ignoreCase = true) ||
            Patterns.EMAIL_ADDRESS.matcher(trimmed).matches()
        ) {
            val email = parseEmail(trimmed)
            return ScanResultData(
                rawValue = rawValue,
                formatName = fmtName,
                formatCode = formatCode,
                contentType = ScanContentType.EMAIL,
                displayTitle = email.address.ifBlank { trimmed },
                timestamp = timestamp,
                source = source,
                historyId = historyId,
                parsedEmail = email
            )
        }

        // 4. SMS (smsto: or sms:)
        if (trimmed.startsWith("smsto:", ignoreCase = true) ||
            trimmed.startsWith("sms:", ignoreCase = true)
        ) {
            val sms = parseSms(trimmed)
            return ScanResultData(
                rawValue = rawValue,
                formatName = fmtName,
                formatCode = formatCode,
                contentType = ScanContentType.SMS,
                displayTitle = sms.number.ifBlank { "SMS Message" },
                timestamp = timestamp,
                source = source,
                historyId = historyId,
                parsedSms = sms,
                parsedPhone = sms.number.ifBlank { null }
            )
        }

        // 5. Product / 1D Barcodes (check before plain-digit phone heuristic so EAN-13/UPC-A aren't misclassified as phone numbers)
        if (isProductBarcodeFormat(formatCode) ||
            (is1DOrDataBarcodeFormat(formatCode) && trimmed.all { it.isDigit() || it == '-' })
        ) {
            return ScanResultData(
                rawValue = rawValue,
                formatName = fmtName,
                formatCode = formatCode,
                contentType = ScanContentType.PRODUCT_BARCODE,
                displayTitle = "$fmtName • $trimmed",
                timestamp = timestamp,
                source = source,
                historyId = historyId
            )
        }

        // 6. Phone (tel: or phone number format in QR codes)
        if (trimmed.startsWith("tel:", ignoreCase = true) ||
            (!is1DOrDataBarcodeFormat(formatCode) && isPlainPhoneNumber(trimmed))
        ) {
            val phone = trimmed.removePrefix("tel:").removePrefix("TEL:").trim()
            return ScanResultData(
                rawValue = rawValue,
                formatName = fmtName,
                formatCode = formatCode,
                contentType = ScanContentType.PHONE,
                displayTitle = phone,
                timestamp = timestamp,
                source = source,
                historyId = historyId,
                parsedPhone = phone
            )
        }

        // 6. Geo location (geo:)
        if (trimmed.startsWith("geo:", ignoreCase = true)) {
            return ScanResultData(
                rawValue = rawValue,
                formatName = fmtName,
                formatCode = formatCode,
                contentType = ScanContentType.GEO,
                displayTitle = trimmed.removePrefix("geo:").substringBefore("?"),
                timestamp = timestamp,
                source = source,
                historyId = historyId
            )
        }

        // 7. URL
        val normalizedUrl = extractUrlIfValid(trimmed)
        if (normalizedUrl != null) {
            val (suspicious, reason) = evaluateUrlSafety(normalizedUrl, trimmed)
            val host = runCatching { Uri.parse(normalizedUrl).host }.getOrNull()
            return ScanResultData(
                rawValue = rawValue,
                formatName = fmtName,
                formatCode = formatCode,
                contentType = ScanContentType.URL,
                displayTitle = host?.takeIf { it.isNotBlank() } ?: normalizedUrl,
                timestamp = timestamp,
                source = source,
                historyId = historyId,
                parsedUrl = normalizedUrl,
                isSuspiciousUrl = suspicious,
                suspiciousReason = reason
            )
        }

        // 8. Fallback Plain Text
        return ScanResultData(
            rawValue = rawValue,
            formatName = fmtName,
            formatCode = formatCode,
            contentType = ScanContentType.TEXT,
            displayTitle = trimmed.lineSequence().firstOrNull()?.take(60) ?: fmtName,
            timestamp = timestamp,
            source = source,
            historyId = historyId
        )
    }

    private fun isPlainPhoneNumber(value: String): Boolean {
        if (value.length !in 7..20) return false
        val digits = value.count { it.isDigit() }
        if (digits < 7) return false
        return value.all { it.isDigit() || it in setOf('+', '-', ' ', '(', ')') }
    }

    private fun extractUrlIfValid(value: String): String? {
        val lower = value.lowercase()
        return when {
            lower.startsWith("https://") || lower.startsWith("http://") -> value
            lower.startsWith("www.") && !value.contains(" ") -> "https://$value"
            !value.contains(" ") && Patterns.WEB_URL.matcher(value).matches() && value.contains(".") -> {
                "https://$value"
            }
            else -> null
        }
    }

    fun evaluateUrlSafety(normalizedUrl: String, originalInput: String): Pair<Boolean, String?> {
        val uri = runCatching { Uri.parse(normalizedUrl) }.getOrNull()
        val scheme = uri?.scheme?.lowercase() ?: ""
        val host = uri?.host?.lowercase() ?: ""
        val userInfo = uri?.userInfo

        if (!userInfo.isNullOrEmpty()) {
            return true to "This link contains embedded credentials (@), which can disguise the true destination."
        }
        if (scheme == "http" || originalInput.lowercase().startsWith("http://")) {
            return true to "This link uses unencrypted HTTP instead of HTTPS."
        }
        val ipRegex = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")
        if (ipRegex.matches(host)) {
            return true to "This link points directly to a numeric IP address rather than a domain name."
        }
        if (host.contains("xn--")) {
            return true to "This domain uses Punycode encoding (xn--), which can sometimes spoof lookalike domains."
        }
        return false to null
    }

    private fun parseWifi(raw: String): ParsedWifi {
        val body = raw.substringAfter(":", "")
        val fields = splitEscapedFields(body, ';')
        var ssid = ""
        var password = ""
        var encryption = "Open"
        var hidden = false

        for (field in fields) {
            val key = field.substringBefore(":", "").uppercase()
            val value = unescapeQrValue(field.substringAfter(":", ""))
            when (key) {
                "S" -> ssid = value
                "P" -> password = value
                "T" -> encryption = when (value.uppercase()) {
                    "WPA", "WPA2", "WPA3" -> "WPA/WPA2"
                    "WEP" -> "WEP"
                    "NOPASS", "" -> "Open"
                    else -> value
                }
                "H" -> hidden = value.equals("true", ignoreCase = true)
            }
        }
        return ParsedWifi(ssid = ssid, password = password, encryption = encryption, isHidden = hidden)
    }

    private fun parseVCard(raw: String): ParsedContact {
        var name = ""
        var phone = ""
        var email = ""
        var org = ""
        var address = ""

        raw.lines().forEach { line ->
            val trimmed = line.trim()
            val upper = trimmed.uppercase()
            val value = trimmed.substringAfter(":", "").trim()
            when {
                upper.startsWith("FN:") || upper.startsWith("FN;") -> {
                    if (name.isBlank()) name = value
                }
                upper.startsWith("N:") || upper.startsWith("N;") -> {
                    if (name.isBlank()) {
                        val parts = value.split(";").filter { it.isNotBlank() }
                        name = parts.reversed().joinToString(" ").trim()
                    }
                }
                upper.startsWith("TEL:") || upper.startsWith("TEL;") -> {
                    if (phone.isBlank()) phone = value
                }
                upper.startsWith("EMAIL:") || upper.startsWith("EMAIL;") -> {
                    if (email.isBlank()) email = value
                }
                upper.startsWith("ORG:") || upper.startsWith("ORG;") -> {
                    if (org.isBlank()) org = value.replace(";", " ").trim()
                }
                upper.startsWith("ADR:") || upper.startsWith("ADR;") -> {
                    if (address.isBlank()) {
                        address = value.split(";").filter { it.isNotBlank() }.joinToString(", ")
                    }
                }
            }
        }
        return ParsedContact(name = name, phone = phone, email = email, organization = org, address = address)
    }

    private fun parseMeCard(raw: String): ParsedContact {
        val body = raw.substringAfter(":", "")
        val fields = splitEscapedFields(body, ';')
        var name = ""
        var phone = ""
        var email = ""
        var org = ""
        var address = ""

        for (field in fields) {
            val key = field.substringBefore(":", "").uppercase()
            val value = unescapeQrValue(field.substringAfter(":", ""))
            when (key) {
                "N" -> name = value.replace(",", " ").trim()
                "TEL" -> if (phone.isBlank()) phone = value
                "EMAIL" -> if (email.isBlank()) email = value
                "ORG" -> org = value
                "ADR" -> address = value.replace(",", ", ").trim()
            }
        }
        return ParsedContact(name = name, phone = phone, email = email, organization = org, address = address)
    }

    private fun parseEmail(raw: String): ParsedEmail {
        if (raw.startsWith("MATMSG:", ignoreCase = true)) {
            val body = raw.substringAfter(":", "")
            val fields = splitEscapedFields(body, ';')
            var to = ""
            var sub = ""
            var msg = ""
            for (field in fields) {
                val key = field.substringBefore(":", "").uppercase()
                val value = unescapeQrValue(field.substringAfter(":", ""))
                when (key) {
                    "TO" -> to = value
                    "SUB" -> sub = value
                    "BODY" -> msg = value
                }
            }
            return ParsedEmail(address = to, subject = sub, body = msg)
        }

        if (raw.startsWith("mailto:", ignoreCase = true)) {
            val withoutScheme = raw.substringAfter(":", "")
            val address = withoutScheme.substringBefore("?").trim()
            val query = withoutScheme.substringAfter("?", "")
            var subject = ""
            var body = ""
            if (query.isNotEmpty()) {
                query.split("&").forEach { param ->
                    val k = param.substringBefore("=").lowercase()
                    val v = runCatching {
                        URLDecoder.decode(param.substringAfter("=", ""), "UTF-8")
                    }.getOrDefault(param.substringAfter("=", ""))
                    when (k) {
                        "subject" -> subject = v
                        "body" -> body = v
                    }
                }
            }
            return ParsedEmail(address = address, subject = subject, body = body)
        }

        return ParsedEmail(address = raw.trim(), subject = "", body = "")
    }

    private fun parseSms(raw: String): ParsedSms {
        val withoutScheme = raw.substringAfter(":", "")
        val number = withoutScheme.substringBefore(":").substringBefore("?").trim()
        val message = when {
            withoutScheme.contains(":") -> withoutScheme.substringAfter(":")
            withoutScheme.contains("body=", ignoreCase = true) -> {
                withoutScheme.substringAfter("body=").substringBefore("&")
            }
            else -> ""
        }
        return ParsedSms(number = number, message = message)
    }

    private fun splitEscapedFields(input: String, delimiter: Char): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var escaped = false
        for (ch in input) {
            if (escaped) {
                current.append('\\').append(ch)
                escaped = false
            } else if (ch == '\\') {
                escaped = true
            } else if (ch == delimiter) {
                if (current.isNotEmpty()) {
                    result.add(current.toString())
                    current.clear()
                }
            } else {
                current.append(ch)
            }
        }
        if (current.isNotEmpty()) {
            result.add(current.toString())
        }
        return result
    }

    private fun unescapeQrValue(value: String): String =
        value.replace("\\;", ";")
            .replace("\\:", ":")
            .replace("\\,", ",")
            .replace("\\\\", "\\")
}
