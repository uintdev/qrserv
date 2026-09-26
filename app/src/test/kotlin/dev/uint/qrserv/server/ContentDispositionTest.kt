package dev.uint.qrserv.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ContentDispositionTest {

    @Test
    fun plainName() {
        assertEquals("attachment; filename=\"report.pdf\"; filename*=UTF-8''report.pdf", contentDispositionHeader("report.pdf"))
    }

    @Test
    fun spacesAreEncodedInTheExtendedName() {
        assertEquals("attachment; filename=\"my file.txt\"; filename*=UTF-8''my%20file.txt", contentDispositionHeader("my file.txt"))
    }

    @Test
    fun pathSeparatorsAreFlattened() {
        assertEquals(
            "attachment; filename=\".._.._etc_passwd\"; filename*=UTF-8''.._.._etc_passwd",
            contentDispositionHeader("../../etc/passwd"),
        )
        assertEquals(
            "attachment; filename=\"C:_Windows_win.ini\"; filename*=UTF-8''C%3A_Windows_win.ini",
            contentDispositionHeader("C:\\Windows\\win.ini"),
        )
    }

    @Test
    fun namesThatAreOnlyAPathFallBack() {
        for (name in listOf("", "   ", ".", "..")) {
            assertEquals(
                "attachment; filename=\"download\"; filename*=UTF-8''download",
                contentDispositionHeader(name),
            )
        }
    }

    @Test
    fun quotesAreEscapedInThePlainName() {
        assertEquals(
            "attachment; filename=\"a\\\"b.txt\"; filename*=UTF-8''a%22b.txt",
            contentDispositionHeader("a\"b.txt"),
        )
    }

    @Test
    fun nonAsciiIsReplacedInThePlainNameAndEncodedInTheExtendedOne() {
        assertEquals(
            "attachment; filename=\"r_sum_.pdf\"; filename*=UTF-8''r%C3%A9sum%C3%A9.pdf",
            contentDispositionHeader("résumé.pdf"),
        )
        assertEquals(
            "attachment; filename=\"__.png\"; filename*=UTF-8''%F0%9F%98%80.png",
            contentDispositionHeader("\uD83D\uDE00.png"),
        )
    }

    @Test
    fun lineBreaksCannotInjectHeaders() {
        val header = contentDispositionHeader("a\r\nSet-Cookie: x=1.txt")
        assertFalse(header.contains('\r'))
        assertFalse(header.contains('\n'))
        assertEquals(
            "attachment; filename=\"a__Set-Cookie: x=1.txt\"; filename*=UTF-8''a%0D%0ASet-Cookie%3A%20x%3D1.txt",
            header,
        )
    }
}
