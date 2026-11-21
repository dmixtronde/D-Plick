package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.ServerPreferencesRepository
import com.example.domain.ScannerUrlNormalizer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface AppDestination {
    data object QrScanner : AppDestination
    data class WebViewSession(val url: String) : AppDestination
}

data class ScannerUiState(
    val destination: AppDestination = AppDestination.QrScanner,
    val lastServerUrl: String? = null,
    val isFlashEnabled: Boolean = false,
    val isManualSheetOpen: Boolean = false,
    val manualInputText: String = "",
    val manualInputError: Boolean = false,
    val invalidQrSignal: Int = 0,
    val showInvalidQrBanner: Boolean = false
)

class ScannerViewModel(
    private val repository: ServerPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScannerUiState())
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    private var errorDismissJob: Job? = null
    private var lastInvalidQrTimeMs: Long = 0L

    init {
        viewModelScope.launch {
            repository.lastServerUrl.collect { savedUrl ->
                _uiState.update { it.copy(lastServerUrl = savedUrl) }
            }
        }
    }

    /**
     * Handles raw QR string from ZXing analyzer.
     * Returns true if valid and navigation to WebView started.
     */
    fun onQrCodeScanned(rawValue: String): Boolean {
        if (_uiState.value.destination is AppDestination.WebViewSession) {
            return true
        }

        val normalized = ScannerUrlNormalizer.normalize(
            rawInput = rawValue,
            assumeHttpsIfMissing = false
        )

        return normalized.fold(
            onSuccess = { url ->
                connectToUrl(url)
                true
            },
            onFailure = {
                val now = System.currentTimeMillis()
                if (now - lastInvalidQrTimeMs > 1800L) {
                    lastInvalidQrTimeMs = now
                    showTemporaryInvalidQrError()
                }
                false
            }
        )
    }

    fun onConnectLastServer() {
        val lastUrl = _uiState.value.lastServerUrl ?: return
        val normalized = ScannerUrlNormalizer.normalize(lastUrl, assumeHttpsIfMissing = true)
            .getOrDefault(lastUrl)
        connectToUrl(normalized)
    }

    fun onOpenManualSheet() {
        val prefill = _uiState.value.lastServerUrl?.let {
            ScannerUrlNormalizer.toEditableBaseUrl(it)
        } ?: "https://192.168.1."

        _uiState.update {
            it.copy(
                isManualSheetOpen = true,
                manualInputText = prefill,
                manualInputError = false
            )
        }
    }

    fun onDismissManualSheet() {
        _uiState.update {
            it.copy(
                isManualSheetOpen = false,
                manualInputError = false
            )
        }
    }

    fun onManualInputChanged(input: String) {
        _uiState.update {
            it.copy(
                manualInputText = input,
                manualInputError = false
            )
        }
    }

    fun onSubmitManualUrl() {
        val normalized = ScannerUrlNormalizer.normalize(
            rawInput = _uiState.value.manualInputText,
            assumeHttpsIfMissing = true
        )
        normalized.fold(
            onSuccess = { url ->
                _uiState.update {
                    it.copy(
                        isManualSheetOpen = false,
                        manualInputError = false
                    )
                }
                connectToUrl(url)
            },
            onFailure = {
                _uiState.update { it.copy(manualInputError = true) }
            }
        )
    }

    fun onToggleFlash() {
        _uiState.update { it.copy(isFlashEnabled = !it.isFlashEnabled) }
    }

    fun onReturnToScanner() {
        _uiState.update {
            it.copy(
                destination = AppDestination.QrScanner,
                isFlashEnabled = false,
                showInvalidQrBanner = false
            )
        }
    }

    private fun connectToUrl(normalizedUrl: String) {
        errorDismissJob?.cancel()
        _uiState.update {
            it.copy(
                destination = AppDestination.WebViewSession(normalizedUrl),
                isFlashEnabled = false,
                showInvalidQrBanner = false
            )
        }
        viewModelScope.launch {
            repository.saveLastServerUrl(normalizedUrl)
        }
    }

    private fun showTemporaryInvalidQrError() {
        errorDismissJob?.cancel()
        _uiState.update {
            it.copy(
                showInvalidQrBanner = true,
                invalidQrSignal = it.invalidQrSignal + 1
            )
        }
        errorDismissJob = viewModelScope.launch {
            delay(2800L)
            _uiState.update { it.copy(showInvalidQrBanner = false) }
        }
    }

    class Factory(
        private val repository: ServerPreferencesRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ScannerViewModel(repository) as T
        }
    }
}
