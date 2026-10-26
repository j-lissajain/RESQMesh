package com.resqmesh.app.data

object EmergencyReportMerge {
    fun upsert(existing: List<EmergencyReport>, incoming: EmergencyReport): List<EmergencyReport> {
        val current = existing.firstOrNull { sameEmergency(it, incoming) }
        val merged = when {
            current == null -> incoming
            incoming.contentRevision < current.contentRevision -> current
            incoming.contentRevision > current.contentRevision -> {
                if (isTrustedLocalContent(current) && isUnverifiedRemoteContent(incoming)) {
                    current
                } else {
                    incoming
                }
            }
            isTrustedLocalContent(current) && isUnverifiedRemoteContent(incoming) -> current
            isTrustedLocalContent(current) && isTrustedLocalContent(incoming) ->
                current.copy(status = incoming.status)
            else -> current
        }
        return (existing.filterNot { sameEmergency(it, incoming) } + merged)
            .sortedByDescending(EmergencyReport::createdAtMillis)
    }

    fun sameEmergency(first: EmergencyReport, second: EmergencyReport): Boolean =
        first.id == second.id

    fun isLocalOriginator(report: EmergencyReport): Boolean =
        isTrustedLocalContent(report) &&
            report.status != EmergencyReportStatus.RECEIVED_FROM_PEER &&
            report.receivedFromPeerId == null

    fun isRelayedCopy(report: EmergencyReport): Boolean =
        report.status == EmergencyReportStatus.RECEIVED_FROM_PEER ||
            report.receivedFromPeerId != null

    fun isTrustedLocalContent(report: EmergencyReport): Boolean =
        report.contentTrust == EmergencyContentTrust.LOCAL_ORIGINATED && !isRelayedCopy(report)

    fun isUnverifiedRemoteContent(report: EmergencyReport): Boolean =
        report.contentTrust == EmergencyContentTrust.REMOTE_UNVERIFIED || isRelayedCopy(report)
}
