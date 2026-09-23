package dev.uint.qrserv.server

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

sealed interface ServingNotice {
    data object Preparing : ServingNotice

    data object StartingHotspot : ServingNotice

    data class Sharing(val fileName: String, val address: String, val hotspotSsid: String? = null) : ServingNotice
}

object ServingState {
    private val _notice = MutableStateFlow<ServingNotice?>(null)
    val notice: StateFlow<ServingNotice?> = _notice.asStateFlow()

    private val _activeTransfers = MutableStateFlow(0)
    val activeTransfers: StateFlow<Int> = _activeTransfers.asStateFlow()

    private val _stopRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val stopRequests: SharedFlow<Unit> = _stopRequests.asSharedFlow()

    fun setNotice(notice: ServingNotice?) {
        _notice.value = notice
    }

    fun transferStarted() = _activeTransfers.update { it + 1 }

    fun transferEnded() = _activeTransfers.update { (it - 1).coerceAtLeast(0) }

    fun resetTransfers() {
        _activeTransfers.value = 0
    }

    /** False when nothing is listening; the caller then has to stop the service itself. */
    fun requestStop(): Boolean =
        _stopRequests.subscriptionCount.value > 0 && _stopRequests.tryEmit(Unit)
}
