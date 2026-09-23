package dev.uint.qrserv.net

enum class HotspotSecurity {
    OPEN,

    WPA,

    SAE,
}

object WifiQr {

    fun payload(ssid: String, passphrase: String, security: HotspotSecurity): String = buildString {
        append("WIFI:")
        when (security) {
            HotspotSecurity.OPEN -> append("T:nopass;")
            HotspotSecurity.WPA -> append("T:WPA;")
            HotspotSecurity.SAE -> append("T:SAE;")
        }
        append("S:").append(escape(ssid)).append(';')
        if (security != HotspotSecurity.OPEN) append("P:").append(escape(passphrase)).append(';')
        append(';')
    }

    private fun escape(value: String): String = buildString {
        for (char in value) {
            if (char == '\\' || char == ';' || char == ',' || char == ':' || char == '"') append('\\')
            append(char)
        }
    }

    fun groupForDisplay(passphrase: String): String = passphrase.chunked(5).joinToString(" ")
}
