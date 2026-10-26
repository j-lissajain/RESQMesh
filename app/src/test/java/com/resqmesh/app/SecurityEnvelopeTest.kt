package com.resqmesh.app

import com.resqmesh.app.data.EmergencyReport
import com.resqmesh.app.data.EmergencySeverity
import com.resqmesh.app.data.EmergencyType
import com.resqmesh.app.data.MeshRecordCodec
import com.resqmesh.app.data.MeshRecord
import com.resqmesh.app.data.MeshRecordType
import org.json.JSONObject
import com.resqmesh.app.security.MeshTrustState
import com.resqmesh.app.security.MeshReplayGuard
import com.resqmesh.app.security.SecureMeshEnvelopeCodec
import com.resqmesh.app.security.SoftwareDeviceIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityEnvelopeTest {
    private val report = EmergencyReport(
        id = "secure-emergency",
        type = EmergencyType.MEDICAL,
        severity = EmergencySeverity.HIGH,
        description = "Need help",
        createdAtMillis = 1_000L
    )

    @Test
    fun authenticatedEnvelopeRoundTripsAndIdentifiesUnknownPeer() {
        val sender = SoftwareDeviceIdentity.generate()
        val receiver = SoftwareDeviceIdentity.generate()
        val payload = MeshRecordCodec.encode(
            MeshRecord(
                recordId = report.id,
                type = MeshRecordType.EMERGENCY_UPDATE,
                originId = "node-a",
                revision = 1,
                createdAtMillis = report.createdAtMillis,
                updatedAtMillis = report.updatedAtMillis,
                body = JSONObject().put("emergency_id", report.id)
            )
        )
        val encoded = SecureMeshEnvelopeCodec.encode(payload, sender, 10_000L, 60_000L)

        val decoded = SecureMeshEnvelopeCodec.decode(encoded, receiver, 20_000L)

        assertNotNull(decoded)
        assertEquals(MeshTrustState.AUTHENTICATED_UNKNOWN, decoded?.trustState)
        assertEquals(sender.snapshot.fingerprint, decoded?.fingerprint)
        assertTrue(payload.contentEquals(decoded!!.payload))
    }

    @Test
    fun tamperingAndExpiryAreRejected() {
        val sender = SoftwareDeviceIdentity.generate()
        val receiver = SoftwareDeviceIdentity.generate()
        val encoded = SecureMeshEnvelopeCodec.encode("payload".toByteArray(), sender, 10_000L, 60_000L)
        val tampered = encoded.copyOf().also { it[it.lastIndex] = if (it[it.lastIndex].toInt() == 65) 66 else 65 }

        assertNull(SecureMeshEnvelopeCodec.decode(tampered, receiver, 20_000L))
        assertNull(SecureMeshEnvelopeCodec.decode(encoded, receiver, 70_001L))
    }

    @Test
    fun legacyPayloadIsExplicitlyMarkedUnsigned() {
        val receiver = SoftwareDeviceIdentity.generate()
        val legacy = "legacy".toByteArray()

        val decoded = SecureMeshEnvelopeCodec.decode(legacy, receiver, 1_000L, legacyPayload = legacy)

        assertEquals(MeshTrustState.LEGACY_UNSIGNED, decoded?.trustState)
    }

    @Test
    fun authenticatedEnvelopeReplayIsRejectedByTheReplayGuard() {
        val sender = SoftwareDeviceIdentity.generate()
        val receiver = SoftwareDeviceIdentity.generate()
        val encoded = SecureMeshEnvelopeCodec.encode("payload".toByteArray(), sender, 10_000L, 60_000L)
        val decoded = checkNotNull(SecureMeshEnvelopeCodec.decode(encoded, receiver, 20_000L))
        val guard = MeshReplayGuard()

        assertTrue(guard.accept(decoded))
        assertTrue(!guard.accept(decoded))
    }
}
