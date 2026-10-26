package com.resqmesh.app.data

object EmergencyRelayPolicy {
    const val MAX_ATTEMPTS = 5
    const val INITIAL_BACKOFF_MILLIS = 2_000L
    const val MAX_BACKOFF_MILLIS = 60_000L

    fun nextEnvelope(report: EmergencyReport, forwardingNodeId: String): EmergencyEnvelope? =
        EmergencyEnvelopeRules.nextHop(
            EmergencyEnvelope(report.hopCount, report.remainingTtl, report.lastHopId),
            forwardingNodeId
        )

    fun eligiblePeers(
        activePeerIds: Set<String>,
        immediateSenderId: String?,
        excludedPeerIds: Set<String>,
        deliveries: Map<String, RelayPeerDelivery>,
        nowMillis: Long,
        maxAttempts: Int = MAX_ATTEMPTS
    ): List<String> = activePeerIds
        .filter { peerId ->
            peerId != immediateSenderId &&
                peerId !in excludedPeerIds &&
                deliveries[peerId]?.acknowledged != true &&
                (deliveries[peerId]?.attempts ?: 0) < maxAttempts &&
                (deliveries[peerId]?.nextAttemptAtMillis ?: 0L) <= nowMillis
        }
        .sorted()

    fun backoffMillis(attempt: Int): Long {
        var value = INITIAL_BACKOFF_MILLIS
        repeat((attempt - 1).coerceIn(0, 5)) {
            value = (value * 2).coerceAtMost(MAX_BACKOFF_MILLIS)
        }
        return value
    }
}
