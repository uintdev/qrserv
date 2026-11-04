package dev.uint.qrserv.util

import android.provider.MediaStore

// A content URI's authority can start with a user ID, as in 0@media.
fun isMediaStoreAuthority(authority: String?): Boolean =
    authority?.substringAfterLast('@') == MediaStore.AUTHORITY
