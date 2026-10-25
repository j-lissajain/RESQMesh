package com.resqmesh.app

import com.resqmesh.app.data.EmergencyLocationSource
import com.resqmesh.app.data.EmergencyReportValidation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmergencyReportValidationTest {
    @Test
    fun manualCoordinates_acceptValidRangeAndRemainUnverified() {
        val location = EmergencyReportValidation.parseManualLocation(
            latitudeText = "21.1458",
            longitudeText = "79.0882",
            timestampMillis = 123L
        )

        requireNotNull(location)
        assertEquals(EmergencyLocationSource.MANUAL, location.source)
        assertFalse(location.isVerified)
        assertEquals(21.1458, location.latitude, 0.000001)
        assertEquals(79.0882, location.longitude, 0.000001)
    }

    @Test
    fun manualCoordinates_rejectOutOfRangeAndMalformedValues() {
        assertNull(EmergencyReportValidation.parseManualLocation("91", "0", 1L))
        assertNull(EmergencyReportValidation.parseManualLocation("0", "181", 1L))
        assertNull(EmergencyReportValidation.parseManualLocation("not-a-number", "0", 1L))
    }

    @Test
    fun descriptionValidation_rejectsBlankOnlyText() {
        assertFalse(EmergencyReportValidation.isDescriptionValid("   "))
        assertTrue(EmergencyReportValidation.isDescriptionValid("Need water"))
    }
}
