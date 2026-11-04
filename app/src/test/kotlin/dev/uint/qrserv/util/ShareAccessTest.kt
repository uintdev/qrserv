package dev.uint.qrserv.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareAccessTest {

    @Test
    fun recognizesMediaStoreWithOrWithoutAUserId() {
        assertTrue(isMediaStoreAuthority("media"))
        assertTrue(isMediaStoreAuthority("0@media"))
        assertTrue(isMediaStoreAuthority("10@media"))
    }

    @Test
    fun otherProvidersAreNotMediaStore() {
        assertFalse(isMediaStoreAuthority("com.example.files"))
        assertFalse(isMediaStoreAuthority("media.example"))
        assertFalse(isMediaStoreAuthority(null))
    }
}
