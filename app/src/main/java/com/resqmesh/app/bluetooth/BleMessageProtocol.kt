package com.resqmesh.app.bluetooth

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.LinkedHashMap
import java.util.LinkedHashSet

enum class BleFrameType(val wireValue: Int) {
    HELLO(1),
    MESSAGE(2),
    ACK(3);

    companion object {
        fun fromWireValue(value: Int): BleFrameType? = entries.firstOrNull { it.wireValue == value }
    }
}

data class BleFrame(
    val type: BleFrameType,
    val messageId: Long,
    val totalLength: Int,
    val offset: Int,
    val payload: ByteArray
)

object BleMessageProtocol {
    const val MAX_MESSAGE_BYTES = 1024
    const val DEFAULT_MTU = 23
    private const val HEADER_SIZE = 16
    private const val MAGIC_0 = 0x52
    private const val MAGIC_1 = 0x51
    private const val VERSION = 1
    private val secureRandom = SecureRandom()

    fun newMessageId(): Long {
        var id = secureRandom.nextLong()
        while (id == 0L) id = secureRandom.nextLong()
        return id
    }

    fun messageIdLabel(messageId: Long): String = "%016X".format(messageId)

    fun encode(
        type: BleFrameType,
        messageId: Long,
        payload: ByteArray,
        mtu: Int
    ): List<ByteArray> {
        require(payload.size <= MAX_MESSAGE_BYTES) { "Payload is too large" }
        val packetLimit = mtu - 3
        require(packetLimit >= HEADER_SIZE) { "MTU is too small for the RESQMesh frame header" }

        val chunkSize = packetLimit - HEADER_SIZE
        if (payload.isEmpty()) {
            return listOf(encodePacket(type, messageId, 0, 0, ByteArray(0)))
        }

        val packets = mutableListOf<ByteArray>()
        var offset = 0
        while (offset < payload.size) {
            val end = minOf(offset + chunkSize, payload.size)
            packets += encodePacket(
                type = type,
                messageId = messageId,
                totalLength = payload.size,
                offset = offset,
                payload = payload.copyOfRange(offset, end)
            )
            offset = end
        }
        return packets
    }

    fun decode(packet: ByteArray): BleFrame? {
        if (packet.size < HEADER_SIZE) return null

        val buffer = ByteBuffer.wrap(packet).order(ByteOrder.BIG_ENDIAN)
        if (buffer.get().toInt() and 0xFF != MAGIC_0) return null
        if (buffer.get().toInt() and 0xFF != MAGIC_1) return null
        if (buffer.get().toInt() and 0xFF != VERSION) return null
        val type = BleFrameType.fromWireValue(buffer.get().toInt() and 0xFF) ?: return null
        val messageId = buffer.long
        val totalLength = buffer.short.toInt() and 0xFFFF
        val offset = buffer.short.toInt() and 0xFFFF
        val payload = ByteArray(buffer.remaining()).also(buffer::get)

        if (totalLength > MAX_MESSAGE_BYTES) return null
        if (totalLength == 0) {
            return if (offset == 0 && payload.isEmpty()) {
                BleFrame(type, messageId, totalLength, offset, payload)
            } else {
                null
            }
        }
        if (offset >= totalLength || payload.isEmpty()) return null
        if (offset + payload.size > totalLength) return null

        return BleFrame(type, messageId, totalLength, offset, payload)
    }

    fun decodeUtf8(payload: ByteArray): String? = try {
        StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(payload))
            .toString()
    } catch (_: CharacterCodingException) {
        null
    }

    private fun encodePacket(
        type: BleFrameType,
        messageId: Long,
        totalLength: Int,
        offset: Int,
        payload: ByteArray
    ): ByteArray {
        return ByteBuffer.allocate(HEADER_SIZE + payload.size)
            .order(ByteOrder.BIG_ENDIAN)
            .put(MAGIC_0.toByte())
            .put(MAGIC_1.toByte())
            .put(VERSION.toByte())
            .put(type.wireValue.toByte())
            .putLong(messageId)
            .putShort(totalLength.toShort())
            .putShort(offset.toShort())
            .put(payload)
            .array()
    }
}

class BleFrameReassembler {
    private data class Key(val type: BleFrameType, val messageId: Long)

    private class PartialFrame(val totalLength: Int, val createdAtMillis: Long) {
        val payload = ByteArray(totalLength)
        val received = BooleanArray(totalLength)
        var receivedBytes = 0
    }

    private val partialFrames = LinkedHashMap<Key, PartialFrame>()
    private val completedKeys = LinkedHashSet<Key>()

    fun accept(frame: BleFrame, nowMillis: Long = System.currentTimeMillis()): ByteArray? {
        removeExpired(nowMillis)
        val key = Key(frame.type, frame.messageId)
        if (completedKeys.contains(key)) return null
        if (frame.totalLength == 0) {
            completedKeys.add(key)
            trimCompletedKeys()
            return ByteArray(0)
        }

        val partial = partialFrames.getOrPut(key) {
            PartialFrame(frame.totalLength, nowMillis)
        }
        if (partial.totalLength != frame.totalLength) {
            partialFrames.remove(key)
            return null
        }

        frame.payload.forEachIndexed { index, byte ->
            val targetIndex = frame.offset + index
            if (targetIndex >= partial.totalLength) {
                partialFrames.remove(key)
                return null
            }
            if (!partial.received[targetIndex]) {
                partial.received[targetIndex] = true
                partial.payload[targetIndex] = byte
                partial.receivedBytes++
            }
        }

        return if (partial.receivedBytes == partial.totalLength) {
            partialFrames.remove(key)
            completedKeys.add(key)
            trimCompletedKeys()
            partial.payload.copyOf()
        } else {
            null
        }
    }

    fun removeExpired(nowMillis: Long, timeoutMillis: Long = 15_000L) {
        val iterator = partialFrames.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (nowMillis - entry.value.createdAtMillis > timeoutMillis) {
                iterator.remove()
            }
        }
    }

    fun clear() {
        partialFrames.clear()
        completedKeys.clear()
    }

    private fun trimCompletedKeys() {
        while (completedKeys.size > 128) {
            completedKeys.iterator().let { iterator ->
                iterator.next()
                iterator.remove()
            }
        }
    }
}
