package com.resqmesh.app

import com.resqmesh.app.data.EmergencyLocation
import com.resqmesh.app.data.EmergencyLocationSource
import com.resqmesh.app.data.EmergencyReport
import com.resqmesh.app.data.EmergencyReportStorageCodec
import com.resqmesh.app.data.EmergencyReportStatus
import com.resqmesh.app.data.EmergencySeverity
import com.resqmesh.app.data.EmergencyType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class EmergencyReportJsonCodecTest {
    @Test
    fun mapLocationAndDescription_surviveEncodeDecode() {
        val report = EmergencyReport(
            id = "report-1",
            type = EmergencyType.TRAPPED_PERSON,
            severity = EmergencySeverity.HIGH,
            description = "Person needs assistance",
            createdAtMillis = 100L,
            status = EmergencyReportStatus.LOCAL_ONLY,
            locationDescription = "Near the main entrance",
            location = EmergencyLocation(
                latitude = 21.1458,
                longitude = 79.0882,
                accuracyMeters = null,
                timestampMillis = 90L,
                source = EmergencyLocationSource.MAP,
                isVerified = false
            )
        )

        val restored = EmergencyReportStorageCodec.decode(
            EmergencyReportStorageCodec.encode(report)
        )

        assertNotNull(restored)
        assertEquals("Near the main entrance", restored?.locationDescription)
        assertEquals(EmergencyLocationSource.MAP, restored?.location?.source)
        assertFalse(restored?.location?.isVerified == true)
        assertEquals("Saved locally — not transmitted", restored?.status?.label)
    }
}
