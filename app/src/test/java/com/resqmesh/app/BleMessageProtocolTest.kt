package com.resqmesh.app

import com.resqmesh.app.bluetooth.BleFrameReassembler
import com.resqmesh.app.bluetooth.BleFrameType
import com.resqmesh.app.bluetooth.BleMessageProtocol
import com.resqmesh.app.data.EmergencyLocation
import com.resqmesh.app.data.EmergencyLocationSource
import com.resqmesh.app.data.EmergencyReport
import com.resqmesh.app.data.EmergencySeverity
import com.resqmesh.app.data.EmergencyType
import com.resqmesh.app.data.EmergencyWireCodec
import com.resqmesh.app.security.SecureMeshEnvelopeCodec
import com.resqmesh.app.security.SoftwareDeviceIdentity
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

class BleMessageProtocolTest {
    @Test
    fun unicodeMessage_roundTripsAcrossFragments() {
        val message = "RESQMesh test 🚑 — café — नमस्ते"
        val payload = message.toByteArray(StandardCharsets.UTF_8)
        val packets = BleMessageProtocol.encode(
            type = BleFrameType.MESSAGE,
            messageId = 42L,
            payload = payload,
            mtu = 23
        )
        val reassembler = BleFrameReassembler()
        var complete: ByteArray? = null

        packets.shuffled().forEach { packet ->
            val frame = BleMessageProtocol.decode(packet)
            checkNotNull(frame)
            complete = reassembler.accept(frame)
        }

        assertEquals(message, BleMessageProtocol.decodeUtf8(checkNotNull(complete)))
    }

    @Test
    fun duplicateFragment_doesNotDuplicateOrCorruptPayload() {
        val payload = "dupe".toByteArray(StandardCharsets.UTF_8)
        val packet = BleMessageProtocol.encode(
            type = BleFrameType.MESSAGE,
            messageId = 99L,
            payload = payload,
            mtu = 23
        ).single()
        val frame = checkNotNull(BleMessageProtocol.decode(packet))
        val reassembler = BleFrameReassembler()

        assertArrayEquals(payload, reassembler.accept(frame))
        assertNull(reassembler.accept(frame))
        assertNull(reassembler.accept(frame))
    }

    @Test
    fun malformedAndOversizedFramesAreRejected() {
        assertNull(BleMessageProtocol.decode(byteArrayOf(0x00, 0x00)))

        val oversized = ByteArray(BleMessageProtocol.MAX_MESSAGE_BYTES + 1)
        val packets = runCatching {
            BleMessageProtocol.encode(
                type = BleFrameType.MESSAGE,
                messageId = 7L,
                payload = oversized,
                mtu = 23
            )
        }
        assertTrue(packets.isFailure)
    }

    @Test
    fun acknowledgmentFrameUsesTheSameMessageId() {
        val packet = BleMessageProtocol.encode(
            type = BleFrameType.ACK,
            messageId = 1234L,
            payload = ByteArray(0),
            mtu = 23
        ).single()

        val frame = checkNotNull(BleMessageProtocol.decode(packet))
        assertEquals(BleFrameType.ACK, frame.type)
        assertEquals(1234L, frame.messageId)
        assertEquals(0, frame.totalLength)
    }

    @Test
    fun signedMaximumEmergencyEnvelopeRoundTripsAcross23ByteMtuFragments() {
        val identity = SoftwareDeviceIdentity.generate()
        val report = EmergencyReport(
            id = "frag-emergency-01",
            type = EmergencyType.FLOOD_RESCUE,
            severity = EmergencySeverity.CRITICAL,
            description = "Urgent rescue needed near bridge ".repeat(7).take(240),
            createdAtMillis = 1_700_000_000_000L,
            location = EmergencyLocation(
                latitude = 21.1458,
                longitude = 79.0882,
                accuracyMeters = 5.0f,
                timestampMillis = 1_700_000_000_100L,
                source = EmergencyLocationSource.GNSS
            ),
            locationDescription = "Landmark description ".repeat(8).take(180),
            originId = "node-origin-1"
        )
        val wire = EmergencyWireCodec.encode(report)
        val signedEnvelope = SecureMeshEnvelopeCodec.encode(wire, identity, 1_000L, 60_000L)
        assertTrue(signedEnvelope.size > 1_024)

        val packets = BleMessageProtocol.encode(
            type = BleFrameType.MESSAGE,
            messageId = 555L,
            payload = signedEnvelope,
            mtu = 23
        )
        assertTrue(packets.size > 50)

        val reassembler = BleFrameReassembler()
        var reassembled: ByteArray? = null
        packets.shuffled().forEach { packet ->
            val frame = checkNotNull(BleMessageProtocol.decode(packet))
            val result = reassembler.accept(frame, nowMillis = 2_000L)
            if (result != null) reassembled = result
        }

        assertNotNull(reassembled)
        val verified = checkNotNull(SecureMeshEnvelopeCodec.decode(checkNotNull(reassembled), identity, 2_000L))
        val decodedReport = checkNotNull(EmergencyWireCodec.decode(verified.payload))
        assertEquals(report.id, decodedReport.emergencyId)
        assertEquals(report.description, decodedReport.description)
        assertEquals(report.locationDescription, decodedReport.locationDescription)
    }

    @Test
    fun timedOutPartialFramesAreEvictedCleanly() {
        val payload = "A message split across multiple BLE packets for timeout test".toByteArray(StandardCharsets.UTF_8)
        val packets = BleMessageProtocol.encode(
            type = BleFrameType.MESSAGE,
            messageId = 888L,
            payload = payload,
            mtu = 23
        )
        val reassembler = BleFrameReassembler()

        // Deliver only the first fragment at t=1000
        val firstFrame = checkNotNull(BleMessageProtocol.decode(packets.first()))
        assertNull(reassembler.accept(firstFrame, nowMillis = 1_000L))

        // After timeout (t=20000 > 1000 + 15000), delivering remaining fragments only will not complete the message
        packets.drop(1).forEach { packet ->
            val frame = checkNotNull(BleMessageProtocol.decode(packet))
            assertNull(reassembler.accept(frame, nowMillis = 20_000L))
        }

        // Delivering the first fragment again within the new window now completes reassembly
        assertArrayEquals(payload, reassembler.accept(firstFrame, nowMillis = 20_500L))
    }
}
