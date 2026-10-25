package com.resqmesh.app.data

object EmergencyReportMerge {
    fun upsert(existing: List<EmergencyReport>, incoming: EmergencyReport): List<EmergencyReport> =
        (existing.filterNot { sameEmergency(it, incoming) } + incoming)
            .sortedByDescending(EmergencyReport::createdAtMillis)

    fun sameEmergency(first: EmergencyReport, second: EmergencyReport): Boolean =
        first.id == second.id
}
