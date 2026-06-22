package com.enmooy.deepseno.ui.viewmodel

import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import com.enmooy.deepseno.service.LiveTranscriber
import com.enmooy.deepseno.service.NsdBrowser
import com.enmooy.deepseno.service.TranscriptCorrector
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

sealed class SettingsSheet {
    data object Pairing : SettingsSheet()
    data object ManualConnect : SettingsSheet()
    data object BonjourConnect : SettingsSheet()
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    val nsdBrowser: NsdBrowser,
    private val prefs: SharedPreferences,
) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true }

    private val _correctionEnabled = MutableStateFlow(
        prefs.getBoolean(TranscriptCorrector.CORRECTION_ENABLED_KEY, true)
    )
    val correctionEnabled: StateFlow<Boolean> = _correctionEnabled

    fun setCorrectionEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(TranscriptCorrector.CORRECTION_ENABLED_KEY, enabled).apply()
        _correctionEnabled.value = enabled
    }

    /** Live-transcription language: "" = auto, "zh-Hans", "en-US", "multilingual". */
    private val _transcriptionLocale = MutableStateFlow(
        prefs.getString(LiveTranscriber.TRANSCRIPTION_LOCALE_KEY, "") ?: ""
    )
    val transcriptionLocale: StateFlow<String> = _transcriptionLocale

    fun setTranscriptionLocale(value: String) {
        prefs.edit().putString(LiveTranscriber.TRANSCRIPTION_LOCALE_KEY, value).apply()
        _transcriptionLocale.value = value
    }

    private val _activeSheet = MutableStateFlow<SettingsSheet?>(null)
    val activeSheet: StateFlow<SettingsSheet?> = _activeSheet

    private val _showQueueManagement = MutableStateFlow(false)
    val showQueueManagement: StateFlow<Boolean> = _showQueueManagement

    private val _manualHost = MutableStateFlow("")
    val manualHost: StateFlow<String> = _manualHost

    private val _manualPort = MutableStateFlow("18526")
    val manualPort: StateFlow<String> = _manualPort

    private val _manualToken = MutableStateFlow("")
    val manualToken: StateFlow<String> = _manualToken

    private val _manualPublicHost = MutableStateFlow("")
    val manualPublicHost: StateFlow<String> = _manualPublicHost

    private val _manualPublicPort = MutableStateFlow("")
    val manualPublicPort: StateFlow<String> = _manualPublicPort

    private val _manualFingerprint = MutableStateFlow("")
    val manualFingerprint: StateFlow<String> = _manualFingerprint

    private val _allowPublicAccess = MutableStateFlow(
        prefs.getBoolean(ALLOW_PUBLIC_ACCESS_KEY, false)
    )
    val allowPublicAccess: StateFlow<Boolean> = _allowPublicAccess

    private val _connectError = MutableStateFlow<String?>(null)
    val connectError: StateFlow<String?> = _connectError

    private val _selectedBonjourDevice = MutableStateFlow<String?>(null)
    val selectedBonjourDevice: StateFlow<String?> = _selectedBonjourDevice

    fun setActiveSheet(sheet: SettingsSheet?) {
        _activeSheet.value = sheet
    }

    fun setShowQueueManagement(show: Boolean) {
        _showQueueManagement.value = show
    }

    fun setManualHost(host: String) {
        _manualHost.value = host
    }

    fun setManualPort(port: String) {
        _manualPort.value = port
    }

    fun setManualToken(token: String) {
        _manualToken.value = token
    }

    fun setManualPublicHost(host: String) {
        _manualPublicHost.value = host
    }

    fun setManualPublicPort(port: String) {
        _manualPublicPort.value = port
    }

    fun setManualFingerprint(fingerprint: String) {
        _manualFingerprint.value = fingerprint
    }

    fun setAllowPublicAccess(allow: Boolean) {
        prefs.edit().putBoolean(ALLOW_PUBLIC_ACCESS_KEY, allow).apply()
        _allowPublicAccess.value = allow
    }

    fun setSelectedBonjourDevice(name: String?) {
        _selectedBonjourDevice.value = name
    }

    /**
     * Reads the optional public-relay fields from the form, but only when the
     * "Public Access" toggle is on and all three (host/port/fingerprint) are
     * present. Returns Triple(publicHost, publicPort, fingerprint) or all-null.
     */
    private fun publicFieldsOrNull(): Triple<String?, Int?, String?> {
        if (!_allowPublicAccess.value) return Triple(null, null, null)
        val ph = _manualPublicHost.value.trim()
        val pp = _manualPublicPort.value.trim().toIntOrNull()
        val fp = _manualFingerprint.value.trim()
        return if (ph.isNotEmpty() && pp != null && pp in 1..65535 && fp.isNotEmpty()) {
            Triple(ph, pp, fp)
        } else {
            Triple(null, null, null)
        }
    }

    fun manualConnect(appState: AppState) {
        val host = _manualHost.value.trim()
        val token = _manualToken.value.trim()
        val portStr = _manualPort.value.trim()

        if (token.isEmpty()) {
            _connectError.value = "Token is required"
            return
        }

        // Public-relay candidate (only when toggle on AND all three fields valid).
        val (publicHost, publicPort, fingerprint) = publicFieldsOrNull()
        val hasPublic = publicHost != null && publicPort != null && fingerprint != null

        // LAN optional when a complete public endpoint is supplied — supports the
        // "away from home, only know the relay address" case. But if LAN host IS
        // provided, the port must be valid.
        var port = 0
        if (host.isNotEmpty()) {
            val p = portStr.toIntOrNull()
            if (p == null || p < 1 || p > 65535) {
                _connectError.value = "Invalid port number"
                return
            }
            port = p
        } else if (!hasPublic) {
            _connectError.value = "Provide a LAN host or enable Public Access"
            return
        }

        _connectError.value = null
        appState.connect(host, port, token, publicHost, publicPort, fingerprint)
        _activeSheet.value = null
    }

    fun connectFromBonjour(appState: AppState) {
        val host = _manualHost.value.trim()
        val token = _manualToken.value.trim()
        val portStr = _manualPort.value.trim()

        if (host.isEmpty() || token.isEmpty()) {
            _connectError.value = "Host and token are required"
            return
        }

        val port = portStr.toIntOrNull()
        if (port == null || port < 1 || port > 65535) {
            _connectError.value = "Invalid port number"
            return
        }

        _connectError.value = null
        val (publicHost, publicPort, fingerprint) = publicFieldsOrNull()
        appState.connect(host, port, token, publicHost, publicPort, fingerprint)
        _activeSheet.value = null
    }

    fun pasteConnectionJSON(context: Context) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboard.primaryClip
            val text = clip?.getItemAt(0)?.text?.toString()

            if (text.isNullOrBlank()) {
                _connectError.value = "Clipboard has no valid JSON"
                return
            }

            val jsonObj = json.parseToJsonElement(text).jsonObject
            val host = jsonObj["host"]?.jsonPrimitive?.content ?: ""
            val port = jsonObj["port"]?.jsonPrimitive?.content ?: "18526"
            val token = jsonObj["token"]?.jsonPrimitive?.content ?: ""
            val publicHost = jsonObj["publicHost"]?.jsonPrimitive?.content ?: ""
            val publicPort = jsonObj["publicPort"]?.jsonPrimitive?.content ?: ""
            val fingerprint = jsonObj["fingerprint"]?.jsonPrimitive?.content ?: ""

            _manualHost.value = host
            _manualPort.value = port
            _manualToken.value = token
            _manualPublicHost.value = publicHost
            _manualPublicPort.value = publicPort
            _manualFingerprint.value = fingerprint
            // Auto-enable the toggle when the pasted JSON carries a full public endpoint.
            if (publicHost.isNotEmpty() && publicPort.isNotEmpty() && fingerprint.isNotEmpty()) {
                setAllowPublicAccess(true)
            }
            _connectError.value = null
        } catch (_: Exception) {
            _connectError.value = "Clipboard has no valid JSON"
        }
    }

    fun resetForm() {
        _manualHost.value = ""
        _manualPort.value = "18526"
        _manualToken.value = ""
        _manualPublicHost.value = ""
        _manualPublicPort.value = ""
        _manualFingerprint.value = ""
        _connectError.value = null
        _selectedBonjourDevice.value = null
    }

    companion object {
        const val ALLOW_PUBLIC_ACCESS_KEY = "allow_public_access"
    }
}
