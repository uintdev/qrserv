package dev.uint.qrserv.net

import org.junit.Assert.assertEquals
import org.junit.Test

class WifiQrTest {

    @Test
    fun wpaNetwork() {
        assertEquals(
            "WIFI:T:WPA;S:AndroidShare_4821;P:k7m3px9q2wra4tn;;",
            WifiQr.payload("AndroidShare_4821", "k7m3px9q2wra4tn", HotspotSecurity.WPA),
        )
    }

    @Test
    fun wpa3OnlyNetworkUsesSae() {
        assertEquals(
            "WIFI:T:SAE;S:Net;P:secret;;",
            WifiQr.payload("Net", "secret", HotspotSecurity.SAE),
        )
    }

    @Test
    fun openNetworkHasNoPassword() {
        assertEquals("WIFI:T:nopass;S:Net;;", WifiQr.payload("Net", "", HotspotSecurity.OPEN))
    }

    @Test
    fun specialCharactersAreEscaped() {
        assertEquals(
            """WIFI:T:WPA;S:a\;b\,c\:d\"e\\f;P:p\;w;;""",
            WifiQr.payload("""a;b,c:d"e\f""", "p;w", HotspotSecurity.WPA),
        )
    }

    @Test
    fun passphraseGroupsOfFive() {
        assertEquals("k7m3p x9q2w ra4tn", WifiQr.groupForDisplay("k7m3px9q2wra4tn"))
        assertEquals("abcde fg", WifiQr.groupForDisplay("abcdefg"))
        assertEquals("", WifiQr.groupForDisplay(""))
    }
}
