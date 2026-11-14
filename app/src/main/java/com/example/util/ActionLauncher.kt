package com.example.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import com.example.data.model.ParsedContact
import com.example.data.model.ParsedEmail

object ActionLauncher {

    fun copyToClipboard(context: Context, label: String, text: String): Boolean {
        return runCatching {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                ?: return false
            clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
            true
        }.getOrDefault(false)
    }

    fun shareText(context: Context, text: String, title: String = "Share Scanned Content"): Boolean {
        return runCatching {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            val chooser = Intent.createChooser(intent, title).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            true
        }.getOrDefault(false)
    }

    fun openUrl(context: Context, url: String): Result<Unit> = runCatching {
        val normalized = if (url.startsWith("http://", ignoreCase = true) ||
            url.startsWith("https://", ignoreCase = true)
        ) {
            url
        } else {
            "https://$url"
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(normalized)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun dialPhone(context: Context, phoneNumber: String): Result<Unit> = runCatching {
        val clean = phoneNumber.removePrefix("tel:").trim()
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(clean)}")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun sendEmail(context: Context, email: ParsedEmail): Result<Unit> = runCatching {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:${Uri.encode(email.address)}")
            if (email.subject.isNotBlank()) putExtra(Intent.EXTRA_SUBJECT, email.subject)
            if (email.body.isNotBlank()) putExtra(Intent.EXTRA_TEXT, email.body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun insertContact(context: Context, contact: ParsedContact): Result<Unit> = runCatching {
        val intent = Intent(ContactsContract.Intents.Insert.ACTION).apply {
            type = ContactsContract.RawContacts.CONTENT_TYPE
            if (contact.name.isNotBlank()) {
                putExtra(ContactsContract.Intents.Insert.NAME, contact.name)
            }
            if (contact.phone.isNotBlank()) {
                putExtra(ContactsContract.Intents.Insert.PHONE, contact.phone)
            }
            if (contact.email.isNotBlank()) {
                putExtra(ContactsContract.Intents.Insert.EMAIL, contact.email)
            }
            if (contact.organization.isNotBlank()) {
                putExtra(ContactsContract.Intents.Insert.COMPANY, contact.organization)
            }
            if (contact.address.isNotBlank()) {
                putExtra(ContactsContract.Intents.Insert.POSTAL, contact.address)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun openGeoLocation(context: Context, geoUri: String): Result<Unit> = runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(geoUri)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun openAppSettings(context: Context) {
        runCatching {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null)
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    fun findTorchCameraId(context: Context): String? {
        return runCatching {
            val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return null
            manager.cameraIdList.firstOrNull { id ->
                val chars = manager.getCameraCharacteristics(id)
                val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                val lensFacing = chars.get(CameraCharacteristics.LENS_FACING)
                hasFlash && lensFacing == CameraCharacteristics.LENS_FACING_BACK
            } ?: manager.cameraIdList.firstOrNull { id ->
                manager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        }.getOrNull()
    }

    fun setTorchEnabled(context: Context, enabled: Boolean): Result<Boolean> = runCatching {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            ?: error("Camera service unavailable.")
        val cameraId = findTorchCameraId(context)
            ?: error("No flashlight unit found on this device.")
        manager.setTorchMode(cameraId, enabled)
        enabled
    }
}
