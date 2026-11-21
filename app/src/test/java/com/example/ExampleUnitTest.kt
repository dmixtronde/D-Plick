package com.example

import com.example.domain.ScannerUrlNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun `appends scanner path when missing`() {
        val result = ScannerUrlNormalizer.normalize("https://192.168.1.11:5000")
        assertEquals("https://192.168.1.11:5000/scanner", result.getOrNull())
    }

    @Test
    fun `appends scanner path cleanly when trailing slash present`() {
        val result = ScannerUrlNormalizer.normalize("https://192.168.1.11:5000/")
        assertEquals("https://192.168.1.11:5000/scanner", result.getOrNull())
    }

    @Test
    fun `preserves existing scanner path without duplicating`() {
        val result = ScannerUrlNormalizer.normalize("https://192.168.1.11:5000/scanner")
        assertEquals("https://192.168.1.11:5000/scanner", result.getOrNull())
    }

    @Test
    fun `strips trailing slash on existing scanner path`() {
        val result = ScannerUrlNormalizer.normalize("https://192.168.1.11:5000/scanner/")
        assertEquals("https://192.168.1.11:5000/scanner", result.getOrNull())
    }

    @Test
    fun `adds https and scanner for manual ip entry`() {
        val result = ScannerUrlNormalizer.normalize(
            rawInput = "192.168.1.11:5000",
            assumeHttpsIfMissing = true
        )
        assertEquals("https://192.168.1.11:5000/scanner", result.getOrNull())
    }

    @Test
    fun `rejects non-url qr payloads`() {
        assertTrue(ScannerUrlNormalizer.normalize("hello d-plick").isFailure)
        assertTrue(ScannerUrlNormalizer.normalize("tel:0901234567").isFailure)
        assertTrue(ScannerUrlNormalizer.normalize("").isFailure)
    }
}
