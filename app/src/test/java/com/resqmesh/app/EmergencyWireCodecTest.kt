package com.resqmesh.app

import com.resqmesh.app.data.EmergencyEnvelopeRules
import com.resqmesh.app.data.EmergencyLocation
import com.resqmesh.app.data.EmergencyLocationSource
import com.resqmesh.app.data.EmergencyReport
import com.resqmesh.app.data.EmergencyReportStatus
import com.resqmesh.app.data.EmergencySeverity
import com.resqmesh.app.data.EmergencyType
import com.resqmesh.app.data.EmergencyWireCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class EmergencyWireCodecTest {
    private val report = EmergencyReport(
        id = "emergency-123",
        type = EmergencyType.FLOOD_RESCUE,
        severity = EmergencySeverity.CRITICAL,
        description = "Water rising near the school entrance",
        createdAtMillis = 1_700_000_000_000L,
        status = EmergencyReportStatus.LOCAL_ONLY,
        location = EmergencyLocation(
            latitude = 21.1458,
            longitude = 79.0882,
            accuracyMeters = 12.5f,
            timestampMillis = 1_700_000_000_100L,
            source = EmergencyLocationSource.GNSS
        ),
        locationDescription = "Behind the main gate",
        originId = "origin-a"
    )

    @Test
    fun structuredEmergency_roundTripsAllImportantFields() {
        val decoded = EmergencyWireCodec.decode(EmergencyWireCodec.encode(report))

        assertEquals(report.id, decoded?.emergencyId)
        assertEquals(EmergencyWireCodec.SCHEMA_VERSION, decoded?.schemaVersion)
        assertEquals(report.type, decoded?.type)
        assertEquals(report.severity, decoded?.severity)
        assertEquals(report.description, decoded?.description)
        assertEquals(report.createdAtMillis, decoded?.createdAtMillis)
        assertEquals(report.location?.latitude, decoded?.latitude)
        assertEquals(report.location?.longitude, decoded?.longitude)
        assertEquals(report.location?.source, decoded?.locationSource)
        assertEquals(report.location?.accuracyMeters, decoded?.accuracyMeters)
        assertEquals(report.locationDescription, decoded?.locationDescription)
        assertEquals(report.originId, decoded?.originId)
        assertEquals(report.contentRevision, decoded?.contentRevision)
        assertEquals(0, decoded?.hopCount)
        assertEquals(EmergencyEnvelopeRules.MAX_HOP_LIMIT, decoded?.remainingTtl)
        assertNull(decoded?.lastHopId)
    }

    @Test
    fun encodedPayloadDoesNotIncludeLocalUiStatus() {
        val encoded = EmergencyWireCodec.encode(report).toString(Charsets.UTF_8)
        assertTrue(encoded.startsWith(EmergencyWireCodec.DISCRIMINATOR))
        assertFalse(encoded.contains(EmergencyReportStatus.LOCAL_ONLY.name))
        assertFalse(encoded.contains(EmergencyReportStatus.TRANSPORT_ACKNOWLEDGED.name))
    }

    @Test
    fun relayEnvelope_roundTripsHopMetadata() {
        val relayed = report.copy(
            hopCount = 1,
            remainingTtl = EmergencyEnvelopeRules.MAX_HOP_LIMIT - 1,
            lastHopId = "peer-a"
        )
        val decoded = EmergencyWireCodec.decode(EmergencyWireCodec.encode(relayed))

        assertEquals(1, decoded?.hopCount)
        assertEquals(2, decoded?.remainingTtl)
        assertEquals("peer-a", decoded?.lastHopId)
    }

    @Test
    fun schemaFourCarriesLifecycleAndRevisionSchemaDefaultsToActive() {
        val revised = report.copy(contentRevision = 7)
        val encoded = EmergencyWireCodec.encode(revised)
        val decoded = checkNotNull(EmergencyWireCodec.decode(encoded))
        assertEquals(EmergencyWireCodec.SCHEMA_VERSION, decoded.schemaVersion)
        assertEquals(7, decoded.contentRevision)

        val preliminaryFields = encoded.toString(Charsets.UTF_8).split('|').toMutableList()
        preliminaryFields[1] = EmergencyWireCodec.REVISION_SCHEMA_VERSION.toString()
        preliminaryFields.removeAt(preliminaryFields.lastIndex)
        preliminaryFields.removeAt(preliminaryFields.lastIndex)
        val preliminary = checkNotNull(
            EmergencyWireCodec.decode(preliminaryFields.joinToString("|").toByteArray())
        )
        assertEquals(EmergencyWireCodec.REVISION_SCHEMA_VERSION, preliminary.schemaVersion)
        assertEquals(7, preliminary.contentRevision)
    }

    @Test
    fun malformedAndUnsupportedPayloadsAreRejected() {
        assertNull(EmergencyWireCodec.decode("ordinary chat".toByteArray()))
        val encoded = EmergencyWireCodec.encode(report).toString(Charsets.UTF_8)
        val unsupported = encoded.replace(
            "|${EmergencyWireCodec.SCHEMA_VERSION}|",
            "|999|"
        ).toByteArray()
        assertNull(EmergencyWireCodec.decode(unsupported))
        assertNull(EmergencyWireCodec.decode((encoded + "|extra").toByteArray()))
    }

    @Test
    fun phase4LegacyPayloadsRemainDecodable() {
        val legacy = listOf(
            EmergencyWireCodec.DISCRIMINATOR,
            "1",
            encodeText(report.id),
            report.type.name,
            report.severity.name,
            encodeText(report.description),
            "21.1458",
            "79.0882",
            "GNSS",
            "12.5",
            "1700000000100",
            encodeText("Behind the main gate"),
            report.createdAtMillis.toString(),
            encodeText("origin-a"),
            EmergencyReportStatus.LOCAL_ONLY.name
        ).joinToString("|")

        val decoded = EmergencyWireCodec.decode(legacy.toByteArray())
        assertNotNull(decoded)
        assertEquals(EmergencyWireCodec.LEGACY_SCHEMA_VERSION, decoded?.schemaVersion)
        assertEquals(report.id, decoded?.emergencyId)
        assertEquals(0, decoded?.hopCount)
        assertEquals(EmergencyEnvelopeRules.MAX_HOP_LIMIT, decoded?.remainingTtl)
        val received = EmergencyWireCodec.toReceivedReport(checkNotNull(decoded), "peer-b")
        assertEquals(EmergencyReportStatus.RECEIVED_FROM_PEER, received.status)
        assertEquals(null, received.lastHopId)
        assertEquals(1, received.contentRevision)
    }

    @Test
    fun receivedReportPreservesIdTimestampAndMarksPeerOrigin() {
        val decoded = checkNotNull(EmergencyWireCodec.decode(EmergencyWireCodec.encode(report)))
        val received = EmergencyWireCodec.toReceivedReport(decoded, "peer-b")

        assertEquals(report.id, received.id)
        assertEquals(report.createdAtMillis, received.createdAtMillis)
        assertEquals(EmergencyReportStatus.RECEIVED_FROM_PEER, received.status)
        assertEquals("peer-b", received.receivedFromPeerId)
        assertEquals(null, received.lastHopId)
        assertFalse(received.location?.isVerified == true)
    }

    @Test
    fun validationRejectsIncompleteOrOutOfRangeLocation() {
        val invalid = EmergencyWireCodec.fromReport(report).copy(latitude = 91.0)
        assertTrue(EmergencyWireCodec.validate(invalid) != null)
        val incomplete = EmergencyWireCodec.fromReport(report).copy(longitude = null)
        assertTrue(EmergencyWireCodec.validate(incomplete) != null)
    }

    @Test
    fun validationRejectsInvalidHopMetadata() {
        assertTrue(
            EmergencyWireCodec.validate(
                EmergencyWireCodec.fromReport(report).copy(hopCount = 4)
            ) != null
        )
        assertTrue(
            EmergencyWireCodec.validate(
                EmergencyWireCodec.fromReport(report).copy(remainingTtl = -1)
            ) != null
        )
        assertTrue(
            EmergencyWireCodec.validate(
                EmergencyWireCodec.fromReport(report).copy(
                    hopCount = 2,
                    remainingTtl = 2,
                    lastHopId = "peer-a"
                )
            ) != null
        )
        assertTrue(
            EmergencyWireCodec.validate(
                EmergencyWireCodec.fromReport(report).copy(hopCount = 1, lastHopId = null)
            ) != null
        )
        assertTrue(
            EmergencyWireCodec.validate(
                EmergencyWireCodec.fromReport(report).copy(hopCount = 0, lastHopId = "peer-a")
            ) != null
        )
        assertNull(
            EmergencyWireCodec.validate(
                EmergencyWireCodec.fromReport(report).copy(
                    hopCount = 1,
                    remainingTtl = 2,
                    lastHopId = "peer-a"
                )
            )
        )
    }

    private fun encodeText(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))
}
