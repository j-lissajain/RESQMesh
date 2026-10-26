package com.resqmesh.app

import com.resqmesh.app.data.EmergencyLocation
import com.resqmesh.app.data.EmergencyLocationSource
import com.resqmesh.app.data.EmergencyReport
import com.resqmesh.app.data.EmergencySeverity
import com.resqmesh.app.data.EmergencyType
import com.resqmesh.app.data.EmergencyWireCodec
import com.resqmesh.app.data.MeshRecordCodec
import com.resqmesh.app.data.MeshRecord
import com.resqmesh.app.data.MeshRecordType
import org.json.JSONObject
import com.resqmesh.app.security.MeshTrustState
import com.resqmesh.app.security.MeshReplayGuard
import com.resqmesh.app.security.PeerTrustStore
import com.resqmesh.app.security.SecureMeshEnvelopeCodec
import com.resqmesh.app.security.SoftwareDeviceIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun peerTrustWorkflowTransitionsBetweenUnknownAndKnownAndRejectsInvalidFingerprints() {
        val sender = SoftwareDeviceIdentity.generate()
        val receiver = SoftwareDeviceIdentity.generate()
        val trustStore = PeerTrustStore.inMemory()
        val encoded = SecureMeshEnvelopeCodec.encode("hello".toByteArray(), sender, 10_000L, 60_000L)

        // Initially unknown peer key
        val beforeTrust = checkNotNull(SecureMeshEnvelopeCodec.decode(encoded, receiver, 20_000L, trustStore))
        assertEquals(MeshTrustState.AUTHENTICATED_UNKNOWN, beforeTrust.trustState)
        assertFalse(trustStore.isKnown(sender.snapshot.fingerprint))

        // Explicitly mark key known
        trustStore.trust(sender.snapshot.fingerprint)
        assertTrue(trustStore.isKnown(sender.snapshot.fingerprint))
        assertEquals(listOf(sender.snapshot.fingerprint), trustStore.trustedFingerprints())

        val afterTrust = checkNotNull(SecureMeshEnvelopeCodec.decode(encoded, receiver, 20_000L, trustStore))
        assertEquals(MeshTrustState.AUTHENTICATED_KNOWN, afterTrust.trustState)

        // Forget key reverts to AUTHENTICATED_UNKNOWN
        trustStore.forget(sender.snapshot.fingerprint)
        assertFalse(trustStore.isKnown(sender.snapshot.fingerprint))
        val afterForget = checkNotNull(SecureMeshEnvelopeCodec.decode(encoded, receiver, 20_000L, trustStore))
        assertEquals(MeshTrustState.AUTHENTICATED_UNKNOWN, afterForget.trustState)

        // Invalid fingerprints are ignored
        trustStore.trust("not-a-valid-sha256-hex")
        assertTrue(trustStore.trustedFingerprints().isEmpty())
    }

    @Test
    fun maximumSupportedEmergencyAndMultibytePayloadsFitWithinSecureEnvelopeAndTransportLimits() {
        val sender = SoftwareDeviceIdentity.generate()
        val receiver = SoftwareDeviceIdentity.generate()
        val maxMultibyteReport = EmergencyReport(
            id = "max-multibyte-emergency-id-1234567890",
            type = EmergencyType.TRAPPED_PERSON,
            severity = EmergencySeverity.CRITICAL,
            description = "बाढ़ में फंसे लोग मदद चाहिए ".repeat(9).take(240),
            createdAtMillis = 1_700_000_000_000L,
            location = EmergencyLocation(
                latitude = 21.145800,
                longitude = 79.088200,
                accuracyMeters = 8.4f,
                timestampMillis = 1_700_000_000_100L,
                source = EmergencyLocationSource.GNSS
            ),
            locationDescription = "मुख्य प्रवेश द्वार के पास नागपुर ".repeat(6).take(180),
            originId = "origin-node-identifier-1234567890",
            hopCount = 1,
            remainingTtl = 2,
            lastHopId = "relay-node-identifier-1234567890",
            contentRevision = 12
        )

        val wireBytes = EmergencyWireCodec.encode(maxMultibyteReport)
        assertTrue(wireBytes.size <= EmergencyWireCodec.MAX_WIRE_BYTES)
        assertTrue(wireBytes.size <= SecureMeshEnvelopeCodec.MAX_PAYLOAD_BYTES)

        val envelopeBytes = SecureMeshEnvelopeCodec.encode(wireBytes, sender, 10_000L, 60_000L)
        assertTrue(envelopeBytes.size <= SecureMeshEnvelopeCodec.MAX_BYTES)
        assertTrue(envelopeBytes.size <= SecureMeshEnvelopeCodec.maxEncodedSizeForPayload(wireBytes.size))

        val decodedEnvelope = checkNotNull(SecureMeshEnvelopeCodec.decode(envelopeBytes, receiver, 20_000L))
        val decodedWire = checkNotNull(EmergencyWireCodec.decode(decodedEnvelope.payload))
        assertEquals(maxMultibyteReport.description, decodedWire.description)
        assertEquals(maxMultibyteReport.locationDescription, decodedWire.locationDescription)
    }

    @Test
    fun oversizedPayloadIsRejectedDeterministically() {
        val sender = SoftwareDeviceIdentity.generate()
        val receiver = SoftwareDeviceIdentity.generate()
        val maxAllowed = ByteArray(SecureMeshEnvelopeCodec.MAX_PAYLOAD_BYTES) { 'a'.code.toByte() }
        val encodedMax = SecureMeshEnvelopeCodec.encode(maxAllowed, sender, 10_000L, 60_000L)
        assertNotNull(SecureMeshEnvelopeCodec.decode(encodedMax, receiver, 20_000L))

        val tooLarge = ByteArray(SecureMeshEnvelopeCodec.MAX_PAYLOAD_BYTES + 1) { 'a'.code.toByte() }
        assertTrue(runCatching { SecureMeshEnvelopeCodec.encode(tooLarge, sender, 10_000L, 60_000L) }.isFailure)
    }
}
