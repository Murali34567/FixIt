package uk.ac.tees.mad.fixit.domain.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages sync status and operations between local and remote data sources
 */
class SyncManager {

    private val _syncState = MutableStateFlow<SyncState>(SyncState.IDLE)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val _lastSyncTime = MutableStateFlow<Long?>(null)
    val lastSyncTime: StateFlow<Long?> = _lastSyncTime.asStateFlow()

    fun setSyncState(state: SyncState) {
        _syncState.value = state
        if (state == SyncState.COMPLETED) {
            _lastSyncTime.value = System.currentTimeMillis()
        }
    }

    fun setSyncError(error: String) {
        _syncState.value = SyncState.ERROR(error)
    }
}

sealed class SyncState {
    object IDLE : SyncState()
    object SYNCING : SyncState()
    object COMPLETED : SyncState()
    data class ERROR(val message: String) : SyncState()
}