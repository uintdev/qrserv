package dev.uint.qrserv.viewmodel

import androidx.annotation.StringRes

/** A one-off, translatable message to surface to the user. */
data class ToastEvent(@StringRes val resId: Int, val args: List<String> = emptyList())
