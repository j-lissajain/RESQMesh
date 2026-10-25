package com.resqmesh.app

import com.resqmesh.app.bluetooth.BleFrameReassembler
import com.resqmesh.app.bluetooth.BleFrameType
import com.resqmesh.app.bluetooth.BleMessageProtocol
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
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
}
