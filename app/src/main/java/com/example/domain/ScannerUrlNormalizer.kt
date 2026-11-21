package com.example.domain

import java.net.URI

/**
 * Normalizes and validates D-Plick LAN server URLs scanned from QR codes or entered manually.
 * Ensures every valid server URL ends with the `/scanner` route required by Flask.
 */
object ScannerUrlNormalizer {

    private const val SCANNER_PATH = "/scanner"

    fun normalize(rawInput: String, assumeHttpsIfMissing: Boolean = false): Result<String> {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Empty URL"))
        }

        val withScheme = if (!trimmed.contains("://")) {
            if (assumeHttpsIfMissing) {
                "https://$trimmed"
            } else {
                return Result.failure(IllegalArgumentException("Missing URL scheme"))
            }
        } else {
            trimmed
        }

        val uri = try {
            URI(withScheme)
        } catch (e: Exception) {
            return Result.failure(IllegalArgumentException("Malformed URI", e))
        }

        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") {
            return Result.failure(IllegalArgumentException("Unsupported scheme: $scheme"))
        }

        val host = uri.host
        if (host.isNullOrBlank() || host.contains(" ")) {
            return Result.failure(IllegalArgumentException("Invalid host"))
        }

        val portPart = if (uri.port != -1) ":${uri.port}" else ""
        val cleanPath = (uri.rawPath ?: "").trimEnd('/')
        val normalizedPath = if (cleanPath.endsWith(SCANNER_PATH, ignoreCase = true)) {
            cleanPath
        } else {
            "$cleanPath$SCANNER_PATH"
        }

        val queryPart = uri.rawQuery?.takeIf { it.isNotEmpty() }?.let { "?$it" } ?: ""
        val normalizedUrl = "$scheme://$host$portPart$normalizedPath$queryPart"
        return Result.success(normalizedUrl)
    }

    /**
     * Strips `/scanner` suffix for friendlier editing inside the manual IP sheet.
     */
    fun toEditableBaseUrl(url: String): String {
        val trimmed = url.trim().trimEnd('/')
        return if (trimmed.endsWith(SCANNER_PATH, ignoreCase = true)) {
            trimmed.dropLast(SCANNER_PATH.length)
        } else {
            trimmed
        }
    }
}
