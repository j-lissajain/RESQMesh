package com.resqmesh.app.data

/**
 * Relay metadata carried with an emergency payload.
 * Hop-local BLE ACKs are separate; this envelope is for store-and-forward.
 */
data class EmergencyEnvelope(
    val hopCount: Int = 0,
    val remainingTtl: Int = EmergencyEnvelopeRules.MAX_HOP_LIMIT,
    val lastHopId: String? = null
)

object EmergencyEnvelopeRules {
    const val MAX_HOP_LIMIT = 3
    const val MAX_HOP_ID_CHARS = 100

    fun origin(): EmergencyEnvelope = EmergencyEnvelope(
        hopCount = 0,
        remainingTtl = MAX_HOP_LIMIT,
        lastHopId = null
    )

    fun normalize(lastHopId: String?): String? =
        lastHopId?.trim()?.takeIf { it.isNotEmpty() }

    fun validate(
        hopCount: Int,
        remainingTtl: Int,
        lastHopId: String?
    ): String? {
        if (hopCount !in 0..MAX_HOP_LIMIT) return "Invalid hop count"
        if (remainingTtl !in 0..MAX_HOP_LIMIT) return "Invalid remaining TTL"
        if (hopCount + remainingTtl > MAX_HOP_LIMIT) return "Hop count and TTL exceed the maximum hop limit"
        val hopId = normalize(lastHopId)
        if (hopId != null && hopId.length > MAX_HOP_ID_CHARS) return "Invalid previous-hop identifier"
        if (hopId != null && hopId.any { it.isISOControl() || it.isWhitespace() }) {
            return "Invalid previous-hop identifier"
        }
        if (hopCount == 0 && hopId != null) return "Origin messages cannot include a previous hop"
        if (hopCount > 0 && hopId == null) return "Relayed messages require a previous-hop identifier"
        return null
    }

    fun validate(envelope: EmergencyEnvelope): String? =
        validate(envelope.hopCount, envelope.remainingTtl, envelope.lastHopId)

    fun canForward(envelope: EmergencyEnvelope): Boolean =
        validate(envelope) == null &&
            envelope.hopCount < MAX_HOP_LIMIT &&
            envelope.remainingTtl > 0

    fun nextHop(envelope: EmergencyEnvelope, forwardingNodeId: String): EmergencyEnvelope? {
        if (!canForward(envelope)) return null
        val nodeId = normalize(forwardingNodeId) ?: return null
        return EmergencyEnvelope(
            hopCount = envelope.hopCount + 1,
            remainingTtl = envelope.remainingTtl - 1,
            lastHopId = nodeId
        ).takeIf { validate(it) == null }
    }
}
