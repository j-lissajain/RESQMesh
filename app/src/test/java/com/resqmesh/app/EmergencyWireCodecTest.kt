package com.resqmesh.app

import com.resqmesh.app.data.EmergencyLocation
import com.resqmesh.app.data.EmergencyLocationSource
import com.resqmesh.app.data.EmergencyReport
import com.resqmesh.app.data.EmergencyReportStatus
import com.resqmesh.app.data.EmergencySeverity
import com.resqmesh.app.data.EmergencyType
import com.resqmesh.app.data.EmergencyWireCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

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
    }

    @Test
    fun malformedAndUnsupportedPayloadsAreRejected() {
        assertNull(EmergencyWireCodec.decode("ordinary chat".toByteArray()))
        val encoded = EmergencyWireCodec.encode(report).toString(Charsets.UTF_8)
        val unsupported = encoded.replace("|1|", "|999|").toByteArray()
        assertNull(EmergencyWireCodec.decode(unsupported))
        assertNull(EmergencyWireCodec.decode((encoded + "|extra").toByteArray()))
    }

    @Test
    fun receivedReportPreservesIdTimestampAndMarksPeerOrigin() {
        val decoded = checkNotNull(EmergencyWireCodec.decode(EmergencyWireCodec.encode(report)))
        val received = EmergencyWireCodec.toReceivedReport(decoded, "peer-b")

        assertEquals(report.id, received.id)
        assertEquals(report.createdAtMillis, received.createdAtMillis)
        assertEquals(EmergencyReportStatus.RECEIVED_FROM_PEER, received.status)
        assertEquals("peer-b", received.receivedFromPeerId)
        assertFalse(received.location?.isVerified == true)
    }

    @Test
    fun validationRejectsIncompleteOrOutOfRangeLocation() {
        val invalid = EmergencyWireCodec.fromReport(report).copy(latitude = 91.0)
        assertTrue(EmergencyWireCodec.validate(invalid) != null)
        val incomplete = EmergencyWireCodec.fromReport(report).copy(longitude = null)
        assertTrue(EmergencyWireCodec.validate(incomplete) != null)
    }
}
