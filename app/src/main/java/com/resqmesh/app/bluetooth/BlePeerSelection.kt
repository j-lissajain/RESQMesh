package com.resqmesh.app.bluetooth

internal object BlePeerSelection {
    fun retainOrChoose(currentPeerId: String?, activePeerIds: List<String>): String? {
        if (currentPeerId != null && currentPeerId in activePeerIds) return currentPeerId
        return activePeerIds.firstOrNull()
    }
}
