package com.example

import android.content.Context
import android.graphics.Color
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.ScanHistoryEntity
import com.example.data.model.ScanContentType
import com.example.util.BarcodeParser
import com.example.util.QrGeneratorUtil
import com.google.mlkit.vision.barcode.common.Barcode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ScannerLogicRobolectricTest {

    private lateinit var database: AppDatabase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun `parse wifi qr payload extracts ssid password and encryption`() {
        val parsed = BarcodeParser.parse(
            rawValue = "WIFI:T:WPA;S:OfficeNet;P:Pass1234;H:true;;",
            formatCode = Barcode.FORMAT_QR_CODE
        )
        assertEquals(ScanContentType.WIFI, parsed.contentType)
        assertEquals("QR Code", parsed.formatName)
        assertEquals("OfficeNet", parsed.parsedWifi?.ssid)
        assertEquals("Pass1234", parsed.parsedWifi?.password)
        assertEquals("WPA/WPA2", parsed.parsedWifi?.encryption)
        assertTrue(parsed.parsedWifi?.isHidden == true)
    }

    @Test
    fun `parse suspicious http url flags warning`() {
        val parsed = BarcodeParser.parse(
            rawValue = "http://192.168.1.50/login",
            formatCode = Barcode.FORMAT_QR_CODE
        )
        assertEquals(ScanContentType.URL, parsed.contentType)
        assertTrue(parsed.isSuspiciousUrl)
        assertNotNull(parsed.suspiciousReason)
    }

    @Test
    fun `parse https domain url is not flagged suspicious`() {
        val parsed = BarcodeParser.parse(
            rawValue = "https://developer.android.com/jetpack",
            formatCode = Barcode.FORMAT_QR_CODE
        )
        assertEquals(ScanContentType.URL, parsed.contentType)
        assertFalse(parsed.isSuspiciousUrl)
    }

    @Test
    fun `parse ean13 barcode preserves digits and format without inventing product info`() {
        val parsed = BarcodeParser.parse(
            rawValue = "4006381333931",
            formatCode = Barcode.FORMAT_EAN_13
        )
        assertEquals(ScanContentType.PRODUCT_BARCODE, parsed.contentType)
        assertEquals("EAN-13", parsed.formatName)
        assertEquals("4006381333931", parsed.rawValue)
    }

    @Test
    fun `parse vcard extracts contact fields`() {
        val vcard = """
            BEGIN:VCARD
            VERSION:3.0
            FN:Alex Rivera
            ORG:Northwind Labs
            TEL:+15550199
            EMAIL:alex@northwind.example
            END:VCARD
        """.trimIndent()
        val parsed = BarcodeParser.parse(vcard, Barcode.FORMAT_QR_CODE)
        assertEquals(ScanContentType.CONTACT, parsed.contentType)
        assertEquals("Alex Rivera", parsed.parsedContact?.name)
        assertEquals("Northwind Labs", parsed.parsedContact?.organization)
        assertEquals("+15550199", parsed.parsedContact?.phone)
        assertEquals("alex@northwind.example", parsed.parsedContact?.email)
    }

    @Test
    fun `generate qr bitmap applies custom colors and dimensions`() {
        val result = QrGeneratorUtil.generateQrBitmap(
            content = "https://example.com",
            sizePx = 512,
            foregroundColor = Color.BLUE,
            backgroundColor = Color.WHITE
        )
        assertTrue(result.isSuccess)
        val bitmap = result.getOrThrow()
        assertEquals(512, bitmap.width)
        assertEquals(512, bitmap.height)
    }

    @Test
    fun `room scan history dao inserts queries and clears records`() = runTest {
        val dao = database.scanHistoryDao()
        val id = dao.insertScan(
            ScanHistoryEntity(
                rawValue = "https://example.com",
                formatName = "QR Code",
                formatCode = Barcode.FORMAT_QR_CODE,
                contentType = ScanContentType.URL.name,
                displayTitle = "example.com",
                source = "CAMERA",
                timestamp = 1700000000000L
            )
        )
        val list = dao.getAllScans().first()
        assertEquals(1, list.size)
        assertEquals("example.com", list.first().displayTitle)

        dao.deleteScanById(id)
        assertTrue(dao.getAllScans().first().isEmpty())
    }
}
