package com.example.npi_puntoinfo_etsiit.sensors

import android.app.Activity
import android.content.Context
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle

/**
 * Gestor del sensor NFC para la lectura y verificación física de la TUI de la UGR.
 * Emplea ReaderMode (Android API 19+) para capturar el UID único del chip de forma
 * directa en primer plano sin necesidad de filtros de Intent en segundo plano.
 */
class NfcCardReader(private val activity: Activity) {

    private val nfcAdapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(activity)

    var onCardDiscovered: ((uidHex: String) -> Unit)? = null

    /**
     * Indica si el dispositivo físico dispone de hardware NFC.
     */
    val isNfcAvailable: Boolean
        get() = nfcAdapter != null

    /**
     * Indica si el sensor NFC está habilitado en los ajustes del sistema.
     */
    val isNfcEnabled: Boolean
        get() = nfcAdapter?.isEnabled == true

    /**
     * Inicia la escucha activa de tarjetas NFC acercadas al dispositivo.
     */
    fun startListening() {
        val adapter = nfcAdapter ?: return
        if (!adapter.isEnabled) return

        val flags = NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or
                NfcAdapter.FLAG_READER_NFC_V or
                NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS

        adapter.enableReaderMode(
            activity,
            { tag: Tag ->
                val idBytes = tag.id
                val uidHex = idBytes.joinToString(":") { "%02X".format(it) }
                activity.runOnUiThread {
                    onCardDiscovered?.invoke(uidHex)
                }
            },
            flags,
            Bundle()
        )
    }

    /**
     * Detiene la escucha activa del lector NFC.
     */
    fun stopListening() {
        try {
            nfcAdapter?.disableReaderMode(activity)
        } catch (_: Exception) {
        }
    }

    companion object {
        /**
         * UID real de la TUI física UGR del dump Mifare Classic EV1 (MF1S70): EA:76:97:6E
         */
        fun generateMockUid(): String {
            return "EA:76:97:6E"
        }
    }
}

