package com.resqmesh.app.data

interface EmergencyReportRepository {
    fun list(): List<EmergencyReport>
    fun save(report: EmergencyReport): EmergencyReport?
    fun updateStatus(reportId: String, status: EmergencyReportStatus): EmergencyReport?
}

interface EmergencySeenRepository {
    fun recordIfNew(
        emergencyId: String,
        contentRevision: Int = EmergencyRevisionRules.INITIAL_REVISION,
        atMillis: Long = System.currentTimeMillis(),
        lastHopId: String? = null
    ): Boolean
}
