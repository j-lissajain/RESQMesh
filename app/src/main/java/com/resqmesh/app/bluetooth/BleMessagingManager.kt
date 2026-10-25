package com.resqmesh.app.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.nio.charset.StandardCharsets
import java.util.ArrayDeque
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import java.util.UUID

private val CLIENT_CONFIGURATION_DESCRIPTOR_UUID: UUID =
    UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

private const val DEFAULT_MTU = BleMessageProtocol.DEFAULT_MTU
private const val REQUESTED_MTU = 247
private const val ACK_TIMEOUT_MILLIS = 8_000L
private const val HANDSHAKE_TIMEOUT_MILLIS = 8_000L
private const val CLIENT_CONNECTION_TIMEOUT_MILLIS = 12_000L
private const val MAX_ATTEMPTS = 2
private const val SERVER_NOTIFICATION_DELAY_MILLIS = 20L
private const val MAX_STORED_MESSAGES = 100
private const val MAX_RECEIVED_MESSAGE_IDS = 128

enum class BleConnectionStatus {
    DISCONNECTED,
    WAITING_FOR_PEER,
    CONNECTING,
    CONNECTED
}

enum class MessageDeliveryState {
    SENDING,
    DELIVERED,
    RECEIVED,
    FAILED,
    DISCONNECTED
}

data class ChatMessage(
    val id: String,
    val text: String,
    val isIncoming: Boolean,
    val state: MessageDeliveryState
)

data class BleMessagingState(
    val serverReady: Boolean = false,
    val connectionStatus: BleConnectionStatus = BleConnectionStatus.DISCONNECTED,
    val selectedPeerId: String? = null,
    val connectedPeerId: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val error: String? = null
)

/**
 * Foreground-only GATT transport for the RESQMesh messaging characteristic.
 * Every phone opens a GATT server and may also open one GATT client connection.
 */
class BleMessagingManager(context: Context) : AutoCloseable {
    private val appContext = context.applicationContext
    private val bluetoothManager = appContext.getSystemService(BluetoothManager::class.java)
    private val readinessChecker = BluetoothReadinessChecker(appContext)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val listeners = mutableSetOf<(BleMessagingState) -> Unit>()
    private val serverConnections = LinkedHashMap<String, ServerConnection>()
    private val pendingMessages = LinkedHashMap<Long, PendingOutgoingMessage>()
    private val receivedMessageIds = LinkedHashSet<Long>()
    private val messages = ArrayDeque<ChatMessage>()

    private var gattServer: BluetoothGattServer? = null
    private var messageCharacteristic: BluetoothGattCharacteristic? = null
    private var clientGatt: BluetoothGatt? = null
    private var clientCharacteristic: BluetoothGattCharacteristic? = null
    private var clientPeerId: String? = null
    private var clientDeviceAddress: String? = null
    private var clientMtu = DEFAULT_MTU
    private var clientReady = false
    private var clientDiscoveryStarted = false
    private var clientConnectionTimeout: Runnable? = null
    private var clientWriteInFlight: QueuedClientWrite? = null
    private val clientWriteQueue = ArrayDeque<QueuedClientWrite>()
    private val clientReassembler = BleFrameReassembler()
    private var localAnonymousId: String? = null
    private var selectedPeerId: String? = null
    private var closed = false
    private var _state = BleMessagingState()

    val state: BleMessagingState
        get() = _state

    fun addListener(listener: (BleMessagingState) -> Unit): () -> Unit {
        if (closed) {
            listener(_state)
            return {}
        }
        listeners += listener
        listener(_state)
        return { listeners -= listener }
    }

    @SuppressLint("MissingPermission")
    fun startServer() {
        if (closed || gattServer != null) return

        val readiness = readinessChecker.check()
        when {
            !readiness.bluetoothSupported -> {
                updateError("This phone does not support Bluetooth.")
                return
            }

            !readiness.bleSupported -> {
                updateError("This phone does not support Bluetooth Low Energy.")
                return
            }

            !readiness.requiredPermissionsGranted -> {
                updateError("Allow ${BluetoothPermissions.permissionLabel()} access before starting messaging.")
                return
            }

            readiness.bluetoothEnabled != true -> {
                updateError("Turn on Bluetooth before starting messaging.")
                return
            }
        }

        val server = try {
            bluetoothManager?.openGattServer(appContext, serverCallback)
        } catch (_: SecurityException) {
            null
        } catch (_: RuntimeException) {
            null
        }

        if (server == null) {
            updateError("The BLE GATT server could not be opened on this phone.")
            return
        }

        val service = BluetoothGattService(
            ResqMeshBleProtocol.SERVICE_UUID,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )
        val characteristic = BluetoothGattCharacteristic(
            ResqMeshBleProtocol.MESSAGE_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or
                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE or
                BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        val descriptor = BluetoothGattDescriptor(
            CLIENT_CONFIGURATION_DESCRIPTOR_UUID,
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
        )
        characteristic.addDescriptor(descriptor)
        service.addCharacteristic(characteristic)

        if (!server.addService(service)) {
            server.close()
            updateError("The BLE messaging service could not be registered.")
            return
        }

        gattServer = server
        messageCharacteristic = characteristic
        publish(
            _state.copy(
                serverReady = true,
                error = null
            )
        )
    }

    fun startSession(localSessionId: String?) {
        localAnonymousId = localSessionId
        startServer()
    }

    fun setLocalSessionId(localSessionId: String?) {
        localAnonymousId = localSessionId
    }

    fun selectPeer(peerId: String) {
        selectedPeerId = peerId
        publish(_state.copy(selectedPeerId = peerId))
    }

    fun stopSession() {
        pendingMessages.values.forEach { pending ->
            cancelTimeout(pending)
            updateMessageState(pending.messageId, MessageDeliveryState.DISCONNECTED)
        }
        pendingMessages.clear()
        closeClientConnection(markPending = false)
        closeServerConnections(markPending = false)
        gattServer?.close()
        gattServer = null
        messageCharacteristic = null
        clientWriteQueue.clear()
        clientWriteInFlight = null
        clientReassembler.clear()
        selectedPeerId = null
        localAnonymousId = null
        publish(
            BleMessagingState(
                messages = messages.toList()
            )
        )
    }

    fun connectToPeer(peer: BlePeer, localSessionId: String?) {
        if (closed) return
        localAnonymousId = localSessionId ?: localAnonymousId
        selectedPeerId = peer.anonymousId

        if (isConnectedTo(peer.anonymousId)) return

        val localId = localAnonymousId
        if (localId != null && localId > peer.anonymousId) {
            publish(
                _state.copy(
                    selectedPeerId = peer.anonymousId,
                    connectionStatus = BleConnectionStatus.WAITING_FOR_PEER,
                    connectedPeerId = null,
                    error = "Waiting for the peer with the lower temporary ID to initiate the connection."
                )
            )
            return
        }

        startServer()
        if (gattServer == null) return
        if (clientGatt != null) return

        val device = try {
            bluetoothManager?.adapter?.getRemoteDevice(peer.deviceAddress)
        } catch (_: SecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
        if (device == null) {
            updateError("The selected BLE peer is no longer available.")
            return
        }

        clientPeerId = peer.anonymousId
        clientDeviceAddress = peer.deviceAddress
        clientMtu = DEFAULT_MTU
        clientReady = false
        clientDiscoveryStarted = false
        publish(
            _state.copy(
                selectedPeerId = peer.anonymousId,
                connectionStatus = BleConnectionStatus.CONNECTING,
                connectedPeerId = null,
                error = null
            )
        )

        try {
            clientGatt = device.connectGatt(
                appContext,
                false,
                clientCallback,
                BluetoothDevice.TRANSPORT_LE
            )
            val timeout = Runnable {
                if (clientGatt != null && !clientReady) {
                    failClientConnection("BLE connection timed out before messaging was ready.")
                }
            }
            clientConnectionTimeout = timeout
            mainHandler.postDelayed(timeout, CLIENT_CONNECTION_TIMEOUT_MILLIS)
        } catch (_: SecurityException) {
            clientGatt = null
            updateError("BLE connection permission was revoked.")
            publish(_state.copy(connectionStatus = BleConnectionStatus.DISCONNECTED))
        } catch (exception: RuntimeException) {
            clientGatt = null
            updateError("BLE connection could not start: ${exception.message ?: "unknown error"}")
            publish(_state.copy(connectionStatus = BleConnectionStatus.DISCONNECTED))
        }
    }

    fun sendMessage(text: String) {
        if (closed) return
        val payload = text.toByteArray(StandardCharsets.UTF_8)
        if (text.isBlank()) {
            updateError("Enter a message before sending.")
            return
        }
        if (payload.size > BleMessageProtocol.MAX_MESSAGE_BYTES) {
            updateError("Message is too large. Keep messages under 1024 UTF-8 bytes.")
            return
        }

        val route = findActiveRoute()
        if (route == null) {
            updateError("Connect to a nearby peer before sending a message.")
            return
        }

        val messageId = BleMessageProtocol.newMessageId()
        appendMessage(
            ChatMessage(
                id = BleMessageProtocol.messageIdLabel(messageId),
                text = text,
                isIncoming = false,
                state = MessageDeliveryState.SENDING
            )
        )
        val pending = PendingOutgoingMessage(
            messageId = messageId,
            route = route,
            attempts = 1
        )
        pendingMessages[messageId] = pending
        if (!sendFrame(route, BleFrameType.MESSAGE, messageId, payload)) {
            pendingMessages.remove(messageId)
            updateMessageState(messageId, MessageDeliveryState.FAILED)
            updateError("The message could not be queued for BLE delivery.")
        } else {
            scheduleAckTimeout(pending)
        }
    }

    override fun close() {
        if (closed) return
        stopSession()
        closed = true
        listeners.clear()
        mainHandler.removeCallbacksAndMessages(null)
    }

    private data class PendingOutgoingMessage(
        val messageId: Long,
        val route: RouteRef,
        var attempts: Int,
        var timeout: Runnable? = null
    )

    private data class RouteRef(
        val side: TransportSide,
        val deviceAddress: String,
        val peerId: String?
    )

    private enum class TransportSide {
        CLIENT,
        SERVER
    }

    private data class QueuedClientWrite(
        val bytes: ByteArray,
        val afterComplete: (() -> Unit)? = null,
        val afterFailure: (() -> Unit)? = null
    )

    private data class QueuedServerNotification(
        val bytes: ByteArray,
        val afterComplete: (() -> Unit)? = null,
        val afterFailure: (() -> Unit)? = null
    )

    private class ServerConnection(val device: BluetoothDevice) {
        var mtu: Int = DEFAULT_MTU
        var notificationsEnabled: Boolean = false
        var remotePeerId: String? = null
        val reassembler = BleFrameReassembler()
        val notificationQueue = ArrayDeque<QueuedServerNotification>()
        var notificationInFlight: QueuedServerNotification? = null
        var handshakeTimeout: Runnable? = null
    }

    private val serverCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            mainHandler.post {
                if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                    handleServerConnected(device)
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    handleServerDisconnected(device.address)
                }
            }
        }

        override fun onMtuChanged(device: BluetoothDevice, mtu: Int) {
            mainHandler.post {
                serverConnections[device.address]?.mtu = mtu.coerceAtLeast(DEFAULT_MTU)
            }
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            val isClientConfiguration = descriptor.uuid == CLIENT_CONFIGURATION_DESCRIPTOR_UUID
            val isValid = isClientConfiguration && !preparedWrite && offset == 0
            if (responseNeeded) {
                sendServerResponse(
                    device = device,
                    requestId = requestId,
                    status = if (isValid) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_INVALID_OFFSET
                )
            }
            if (isValid) {
                val notificationsEnabled = value?.contentEquals(
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                ) == true || value?.contentEquals(
                    BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                ) == true
                mainHandler.post {
                    serverConnections[device.address]?.let { connection ->
                        connection.notificationsEnabled = notificationsEnabled
                        refreshConnectionState()
                    }
                }
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            val isMessageCharacteristic = characteristic.uuid == ResqMeshBleProtocol.MESSAGE_CHARACTERISTIC_UUID
            val isValid = isMessageCharacteristic && !preparedWrite && offset == 0 && value != null
            if (responseNeeded) {
                sendServerResponse(
                    device = device,
                    requestId = requestId,
                    status = if (isValid) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_INVALID_OFFSET
                )
            }
            if (isValid) {
                val packet = value?.copyOf() ?: return
                mainHandler.post {
                    serverConnections[device.address]?.let { connection ->
                        handleIncomingPacket(
                            packet = packet,
                            reassembler = connection.reassembler,
                            route = RouteRef(
                                side = TransportSide.SERVER,
                                deviceAddress = device.address,
                                peerId = connection.remotePeerId
                            ),
                            serverConnection = connection
                        )
                    }
                }
            }
        }

        override fun onNotificationSent(device: BluetoothDevice, status: Int) {
            mainHandler.post {
                serverConnections[device.address]?.let { connection ->
                    finishServerNotification(connection, status == BluetoothGatt.GATT_SUCCESS)
                }
            }
        }
    }

    private val clientCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            mainHandler.post {
                if (gatt !== clientGatt) return@post
                if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                    clientConnectionTimeout?.let(mainHandler::removeCallbacks)
                    clientConnectionTimeout = null
                    requestMtuOrDiscover(gatt)
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    handleClientDisconnected(gatt, status)
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            mainHandler.post {
                if (gatt !== clientGatt) return@post
                clientMtu = if (status == BluetoothGatt.GATT_SUCCESS) mtu.coerceAtLeast(DEFAULT_MTU) else DEFAULT_MTU
                discoverClientServices(gatt)
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            mainHandler.post {
                if (gatt !== clientGatt) return@post
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    enableClientNotifications(gatt)
                } else {
                    failClientConnection("GATT service discovery failed.")
                }
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            mainHandler.post {
                if (gatt !== clientGatt) return@post
                if (descriptor.uuid == CLIENT_CONFIGURATION_DESCRIPTOR_UUID &&
                    status == BluetoothGatt.GATT_SUCCESS
                ) {
                    clientReady = true
                    publish(
                        _state.copy(
                            connectionStatus = BleConnectionStatus.CONNECTED,
                            selectedPeerId = selectedPeerId ?: clientPeerId,
                            connectedPeerId = clientPeerId,
                            error = null
                        )
                    )
                    sendClientHello()
                } else {
                    failClientConnection("The BLE notification subscription failed.")
                }
            }
        }

        @Deprecated("Use the value overload on Android 13 and newer")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            val packet = characteristic.value?.copyOf() ?: return
            handleClientNotification(gatt, characteristic, packet)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleClientNotification(gatt, characteristic, value.copyOf())
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            mainHandler.post {
                if (gatt !== clientGatt) return@post
                finishClientWrite(status == BluetoothGatt.GATT_SUCCESS)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleServerConnected(device: BluetoothDevice) {
        val address = device.address
        serverConnections.remove(address)?.let { oldConnection ->
            oldConnection.handshakeTimeout?.let(mainHandler::removeCallbacks)
            oldConnection.notificationQueue.clear()
        }
        val connection = ServerConnection(device)
        serverConnections[address] = connection
        val timeout = Runnable {
            val current = serverConnections[address]
            if (current === connection && current.remotePeerId == null) {
                closeServerConnection(address, markPending = false)
                updateError("A BLE peer connected but did not complete its handshake.")
                refreshConnectionState()
            }
        }
        connection.handshakeTimeout = timeout
        mainHandler.postDelayed(timeout, HANDSHAKE_TIMEOUT_MILLIS)
        refreshConnectionState()
    }

    private fun handleServerDisconnected(address: String) {
        closeServerConnection(address, markPending = true)
        refreshConnectionState()
    }

    private fun requestMtuOrDiscover(gatt: BluetoothGatt) {
        if (clientDiscoveryStarted) return
        val requested = try {
            gatt.requestMtu(REQUESTED_MTU)
        } catch (_: SecurityException) {
            false
        } catch (_: RuntimeException) {
            false
        }
        if (!requested) discoverClientServices(gatt)
    }

    private fun discoverClientServices(gatt: BluetoothGatt) {
        if (clientDiscoveryStarted) return
        clientDiscoveryStarted = true
        try {
            if (!gatt.discoverServices()) {
                failClientConnection("GATT service discovery could not start.")
            }
        } catch (_: SecurityException) {
            failClientConnection("BLE connection permission was revoked.")
        }
    }

    private fun enableClientNotifications(gatt: BluetoothGatt) {
        val service = gatt.getService(ResqMeshBleProtocol.SERVICE_UUID)
        val characteristic = service?.getCharacteristic(ResqMeshBleProtocol.MESSAGE_CHARACTERISTIC_UUID)
        if (characteristic == null) {
            failClientConnection("The selected peer does not expose the RESQMesh messaging service.")
            return
        }
        val notificationEnabled = try {
            gatt.setCharacteristicNotification(characteristic, true)
        } catch (_: SecurityException) {
            false
        }
        if (!notificationEnabled) {
            failClientConnection("BLE notifications could not be enabled.")
            return
        }
        val descriptor = characteristic.getDescriptor(CLIENT_CONFIGURATION_DESCRIPTOR_UUID)
        if (descriptor == null) {
            failClientConnection("The peer messaging characteristic has no notification descriptor.")
            return
        }
        clientCharacteristic = characteristic
        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        try {
            if (!gatt.writeDescriptor(descriptor)) {
                failClientConnection("The BLE notification subscription could not be written.")
            }
        } catch (_: SecurityException) {
            failClientConnection("BLE connection permission was revoked.")
        }
    }

    private fun sendClientHello() {
        val token = localAnonymousId?.hexToBytes()
        if (token == null) {
            failClientConnection("The local temporary BLE identity is unavailable.")
            return
        }
        sendClientFrame(
            type = BleFrameType.HELLO,
            messageId = 0L,
            payload = token
        )
    }

    private fun handleClientNotification(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        packet: ByteArray
    ) {
        if (characteristic.uuid != ResqMeshBleProtocol.MESSAGE_CHARACTERISTIC_UUID) return
        mainHandler.post {
            if (gatt !== clientGatt) return@post
            handleIncomingPacket(
                packet = packet,
                reassembler = clientReassembler,
                route = RouteRef(
                    side = TransportSide.CLIENT,
                    deviceAddress = clientDeviceAddress.orEmpty(),
                    peerId = clientPeerId
                ),
                serverConnection = null
            )
        }
    }

    private fun handleIncomingPacket(
        packet: ByteArray,
        reassembler: BleFrameReassembler,
        route: RouteRef,
        serverConnection: ServerConnection?
    ) {
        val frame = BleMessageProtocol.decode(packet) ?: return
        val payload = reassembler.accept(frame) ?: return
        when (frame.type) {
            BleFrameType.HELLO -> handleHello(payload, serverConnection)
            BleFrameType.MESSAGE -> {
                if (payload.isNotEmpty()) handleIncomingMessage(frame.messageId, payload, route)
            }
            BleFrameType.ACK -> handleAcknowledgement(frame.messageId)
        }
    }

    private fun handleHello(payload: ByteArray, serverConnection: ServerConnection?) {
        val peerId = ResqMeshBleProtocol.anonymousIdFor(payload) ?: return
        val connection = serverConnection ?: return
        connection.remotePeerId = peerId
        if (selectedPeerId == null || !hasActivePeer(selectedPeerId)) {
            selectedPeerId = peerId
        }
        connection.handshakeTimeout?.let(mainHandler::removeCallbacks)
        connection.handshakeTimeout = null
        resolveDuplicateConnection(connection)
        refreshConnectionState()
    }

    private fun handleIncomingMessage(messageId: Long, payload: ByteArray, route: RouteRef) {
        val text = BleMessageProtocol.decodeUtf8(payload) ?: return
        if (text.toByteArray(StandardCharsets.UTF_8).size > BleMessageProtocol.MAX_MESSAGE_BYTES) return

        if (!receivedMessageIds.add(messageId)) {
            sendAcknowledgement(route, messageId)
            return
        }
        while (receivedMessageIds.size > MAX_RECEIVED_MESSAGE_IDS) {
            receivedMessageIds.iterator().let { iterator ->
                iterator.next()
                iterator.remove()
            }
        }

        appendMessage(
            ChatMessage(
                id = BleMessageProtocol.messageIdLabel(messageId),
                text = text,
                isIncoming = true,
                state = MessageDeliveryState.RECEIVED
            )
        )
        sendAcknowledgement(route, messageId)
    }

    private fun handleAcknowledgement(messageId: Long) {
        val pending = pendingMessages.remove(messageId) ?: return
        cancelTimeout(pending)
        updateMessageState(messageId, MessageDeliveryState.DELIVERED)
    }

    private fun sendAcknowledgement(route: RouteRef, messageId: Long) {
        sendFrame(route, BleFrameType.ACK, messageId, ByteArray(0))
    }

    private fun sendFrame(
        route: RouteRef,
        type: BleFrameType,
        messageId: Long,
        payload: ByteArray,
        afterComplete: (() -> Unit)? = null,
        afterFailure: (() -> Unit)? = null
    ): Boolean {
        return try {
            when (route.side) {
                TransportSide.CLIENT -> sendClientFrame(
                    type = type,
                    messageId = messageId,
                    payload = payload,
                    afterComplete = afterComplete,
                    afterFailure = afterFailure
                )

                TransportSide.SERVER -> {
                    val connection = serverConnections[route.deviceAddress]
                    if (connection == null) {
                        false
                    } else {
                        enqueueServerFrame(
                            connection = connection,
                            type = type,
                            messageId = messageId,
                            payload = payload,
                            afterComplete = afterComplete,
                            afterFailure = afterFailure
                        )
                    }
                }
            }
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    private fun sendClientFrame(
        type: BleFrameType,
        messageId: Long,
        payload: ByteArray,
        afterComplete: (() -> Unit)? = null,
        afterFailure: (() -> Unit)? = null
    ): Boolean {
        if (!clientReady || clientCharacteristic == null || clientGatt == null) return false
        val packets = BleMessageProtocol.encode(type, messageId, payload, clientMtu)
        packets.forEachIndexed { index, packet ->
            clientWriteQueue.add(
                QueuedClientWrite(
                    bytes = packet,
                    afterComplete = if (index == packets.lastIndex) afterComplete else null,
                    afterFailure = if (index == packets.lastIndex) afterFailure else null
                )
            )
        }
        pumpClientWriteQueue()
        return true
    }

    private fun pumpClientWriteQueue() {
        if (clientWriteInFlight != null) return
        val gatt = clientGatt ?: return
        val characteristic = clientCharacteristic ?: return
        if (clientWriteQueue.isEmpty()) return
        val next = clientWriteQueue.removeFirst()
        clientWriteInFlight = next
        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        characteristic.value = next.bytes
        val started = try {
            gatt.writeCharacteristic(characteristic)
        } catch (_: SecurityException) {
            false
        } catch (_: RuntimeException) {
            false
        }
        if (!started) finishClientWrite(success = false)
    }

    private fun finishClientWrite(success: Boolean) {
        val current = clientWriteInFlight ?: return
        clientWriteInFlight = null
        if (success) {
            current.afterComplete?.invoke()
            pumpClientWriteQueue()
        } else {
            clientWriteQueue.clear()
            current.afterFailure?.invoke()
            failClientConnection("A BLE characteristic write failed.")
        }
    }

    private fun enqueueServerFrame(
        connection: ServerConnection,
        type: BleFrameType,
        messageId: Long,
        payload: ByteArray,
        afterComplete: (() -> Unit)? = null,
        afterFailure: (() -> Unit)? = null
    ): Boolean {
        if (!connection.notificationsEnabled || gattServer == null || messageCharacteristic == null) return false
        val packets = BleMessageProtocol.encode(type, messageId, payload, connection.mtu)
        packets.forEachIndexed { index, packet ->
            connection.notificationQueue.add(
                QueuedServerNotification(
                    bytes = packet,
                    afterComplete = if (index == packets.lastIndex) afterComplete else null,
                    afterFailure = if (index == packets.lastIndex) afterFailure else null
                )
            )
        }
        pumpServerNotifications(connection)
        return true
    }

    @SuppressLint("MissingPermission")
    private fun pumpServerNotifications(connection: ServerConnection) {
        if (connection.notificationInFlight != null) return
        if (connection.notificationQueue.isEmpty()) return
        val next = connection.notificationQueue.removeFirst()
        val server = gattServer ?: return
        val characteristic = messageCharacteristic ?: return
        connection.notificationInFlight = next
        val started = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                server.notifyCharacteristicChanged(
                    connection.device,
                    characteristic,
                    false,
                    next.bytes
                ) == BluetoothGatt.GATT_SUCCESS
            } else {
                characteristic.value = next.bytes
                server.notifyCharacteristicChanged(connection.device, characteristic, false)
            }
        } catch (_: SecurityException) {
            false
        } catch (_: RuntimeException) {
            false
        }
        if (!started) finishServerNotification(connection, success = false)
    }

    private fun finishServerNotification(connection: ServerConnection, success: Boolean) {
        val current = connection.notificationInFlight ?: return
        connection.notificationInFlight = null
        if (success) {
            current.afterComplete?.invoke()
            mainHandler.postDelayed(
                { pumpServerNotifications(connection) },
                SERVER_NOTIFICATION_DELAY_MILLIS
            )
        } else {
            connection.notificationQueue.clear()
            current.afterFailure?.invoke()
            val address = connection.device.address
            markPendingForRoute(
                RouteRef(TransportSide.SERVER, address, connection.remotePeerId),
                MessageDeliveryState.FAILED
            )
            refreshConnectionState()
        }
    }

    private fun findActiveRoute(): RouteRef? {
        val selected = selectedPeerId
        if (clientReady && clientDeviceAddress != null &&
            (selected == null || selected == clientPeerId)
        ) {
            return RouteRef(
                side = TransportSide.CLIENT,
                deviceAddress = clientDeviceAddress.orEmpty(),
                peerId = clientPeerId
            )
        }

        val serverConnection = serverConnections.values.firstOrNull { connection ->
            connection.notificationsEnabled &&
                connection.remotePeerId != null &&
                (selected == null || selected == connection.remotePeerId)
        }
        return serverConnection?.let { connection ->
            RouteRef(
                side = TransportSide.SERVER,
                deviceAddress = connection.device.address,
                peerId = connection.remotePeerId
            )
        }
    }

    private fun isConnectedTo(peerId: String): Boolean =
        (clientReady && clientPeerId == peerId) ||
            serverConnections.values.any { it.notificationsEnabled && it.remotePeerId == peerId }

    private fun scheduleAckTimeout(pending: PendingOutgoingMessage) {
        val timeout = Runnable {
            val current = pendingMessages[pending.messageId] ?: return@Runnable
            if (current.attempts < MAX_ATTEMPTS && routeIsAvailable(current.route)) {
                current.attempts++
                if (resendPending(current)) {
                    scheduleAckTimeout(current)
                } else {
                    pendingMessages.remove(current.messageId)
                    updateMessageState(current.messageId, MessageDeliveryState.FAILED)
                }
            } else {
                pendingMessages.remove(current.messageId)
                updateMessageState(
                    current.messageId,
                    if (routeIsAvailable(current.route)) {
                        MessageDeliveryState.FAILED
                    } else {
                        MessageDeliveryState.DISCONNECTED
                    }
                )
            }
        }
        pending.timeout = timeout
        mainHandler.postDelayed(timeout, ACK_TIMEOUT_MILLIS)
    }

    private fun resendPending(pending: PendingOutgoingMessage): Boolean {
        val message = messages.firstOrNull {
            it.id == BleMessageProtocol.messageIdLabel(pending.messageId)
        } ?: return false
        return sendFrame(
            route = pending.route,
            type = BleFrameType.MESSAGE,
            messageId = pending.messageId,
            payload = message.text.toByteArray(StandardCharsets.UTF_8)
        )
    }

    private fun routeIsAvailable(route: RouteRef): Boolean = when (route.side) {
        TransportSide.CLIENT -> clientReady && clientDeviceAddress == route.deviceAddress
        TransportSide.SERVER -> serverConnections[route.deviceAddress]?.let {
            it.notificationsEnabled
        } == true
    }

    private fun cancelTimeout(pending: PendingOutgoingMessage) {
        pending.timeout?.let(mainHandler::removeCallbacks)
        pending.timeout = null
    }

    private fun markPendingForRoute(route: RouteRef, finalState: MessageDeliveryState) {
        val iterator = pendingMessages.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val pending = entry.value
            if (pending.route.side == route.side &&
                pending.route.deviceAddress == route.deviceAddress
            ) {
                cancelTimeout(pending)
                updateMessageState(pending.messageId, finalState)
                iterator.remove()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleClientDisconnected(gatt: BluetoothGatt, status: Int) {
        clientConnectionTimeout?.let(mainHandler::removeCallbacks)
        clientConnectionTimeout = null
        val address = clientDeviceAddress.orEmpty()
        val disconnectedPeerId = clientPeerId
        markPendingForRoute(
            RouteRef(TransportSide.CLIENT, address, disconnectedPeerId),
            MessageDeliveryState.DISCONNECTED
        )
        gatt.close()
        clientGatt = null
        clientCharacteristic = null
        clientPeerId = null
        clientDeviceAddress = null
        clientReady = false
        clientDiscoveryStarted = false
        clientWriteQueue.clear()
        clientWriteInFlight = null
        clientReassembler.clear()
        if (selectedPeerId == disconnectedPeerId) {
            selectedPeerId = firstActivePeerId()
        }
        publish(
            _state.copy(
                connectionStatus = BleConnectionStatus.DISCONNECTED,
                selectedPeerId = selectedPeerId,
                connectedPeerId = null,
                error = if (status == BluetoothGatt.GATT_SUCCESS) {
                    null
                } else {
                    "BLE connection closed (status $status)."
                }
            )
        )
        refreshConnectionState()
    }

    @SuppressLint("MissingPermission")
    private fun failClientConnection(message: String) {
        clientConnectionTimeout?.let(mainHandler::removeCallbacks)
        clientConnectionTimeout = null
        val gatt = clientGatt
        val address = clientDeviceAddress.orEmpty()
        val failedPeerId = clientPeerId
        markPendingForRoute(
            RouteRef(TransportSide.CLIENT, address, failedPeerId),
            MessageDeliveryState.FAILED
        )
        clientGatt = null
        clientCharacteristic = null
        clientPeerId = null
        clientDeviceAddress = null
        clientReady = false
        clientDiscoveryStarted = false
        clientWriteQueue.clear()
        clientWriteInFlight = null
        clientReassembler.clear()
        if (selectedPeerId == failedPeerId) {
            selectedPeerId = firstActivePeerId()
        }
        try {
            gatt?.disconnect()
            gatt?.close()
        } catch (_: SecurityException) {
            // Permission may have been revoked while the connection was active.
        }
        publish(
            _state.copy(
                connectionStatus = BleConnectionStatus.DISCONNECTED,
                selectedPeerId = selectedPeerId,
                connectedPeerId = null,
                error = message
            )
        )
        refreshConnectionState()
    }

    @SuppressLint("MissingPermission")
    private fun closeClientConnection(markPending: Boolean) {
        clientConnectionTimeout?.let(mainHandler::removeCallbacks)
        clientConnectionTimeout = null
        val address = clientDeviceAddress.orEmpty()
        val closedPeerId = clientPeerId
        if (markPending) {
            markPendingForRoute(
                RouteRef(TransportSide.CLIENT, address, closedPeerId),
                MessageDeliveryState.DISCONNECTED
            )
        }
        val gatt = clientGatt
        clientGatt = null
        clientCharacteristic = null
        clientPeerId = null
        clientDeviceAddress = null
        clientReady = false
        clientDiscoveryStarted = false
        clientWriteQueue.clear()
        clientWriteInFlight = null
        clientReassembler.clear()
        if (selectedPeerId == closedPeerId) {
            selectedPeerId = firstActivePeerId()
        }
        try {
            gatt?.disconnect()
            gatt?.close()
        } catch (_: SecurityException) {
            // Permission may have been revoked while closing.
        }
    }

    @SuppressLint("MissingPermission")
    private fun closeServerConnection(address: String, markPending: Boolean) {
        val connection = serverConnections.remove(address) ?: return
        connection.handshakeTimeout?.let(mainHandler::removeCallbacks)
        connection.handshakeTimeout = null
        if (markPending) {
            markPendingForRoute(
                RouteRef(TransportSide.SERVER, address, connection.remotePeerId),
                MessageDeliveryState.DISCONNECTED
            )
        }
        connection.notificationQueue.clear()
        connection.notificationInFlight = null
        try {
            gattServer?.cancelConnection(connection.device)
        } catch (_: SecurityException) {
            // Permission may have been revoked while closing.
        }
    }

    private fun closeServerConnections(markPending: Boolean) {
        serverConnections.keys.toList().forEach { address ->
            closeServerConnection(address, markPending)
        }
    }

    private fun resolveDuplicateConnection(serverConnection: ServerConnection) {
        val remoteId = serverConnection.remotePeerId ?: return
        val localId = localAnonymousId ?: return
        if (clientPeerId != remoteId || clientGatt == null) return

        if (localId < remoteId) {
            // Lower temporary ID is the deterministic initiator; keep the client route.
            closeServerConnection(serverConnection.device.address, markPending = false)
        } else {
            // If the other side is the initiator, keep the server route.
            closeClientConnection(markPending = true)
        }
    }

    private fun refreshConnectionState() {
        val clientConnected = clientReady && clientPeerId != null
        val serverConnection = serverConnections.values.firstOrNull {
            it.notificationsEnabled && it.remotePeerId != null
        }
        val connectedId = if (clientConnected) clientPeerId else serverConnection?.remotePeerId
        selectedPeerId = BlePeerSelection.retainOrChoose(
            currentPeerId = selectedPeerId,
            activePeerIds = buildList {
                connectedId?.let(::add)
                serverConnection?.remotePeerId?.let { if (it != connectedId) add(it) }
            }
        )
        val status = when {
            connectedId != null -> BleConnectionStatus.CONNECTED
            clientGatt != null || serverConnections.isNotEmpty() -> BleConnectionStatus.CONNECTING
            _state.connectionStatus == BleConnectionStatus.WAITING_FOR_PEER -> {
                BleConnectionStatus.WAITING_FOR_PEER
            }
            else -> BleConnectionStatus.DISCONNECTED
        }
        publish(
            _state.copy(
                serverReady = gattServer != null,
                connectionStatus = status,
                selectedPeerId = selectedPeerId,
                connectedPeerId = connectedId
            )
        )
    }

    private fun handleServerResponseFailure(message: String) {
        updateError(message)
    }

    @SuppressLint("MissingPermission")
    private fun sendServerResponse(
        device: BluetoothDevice,
        requestId: Int,
        status: Int
    ) {
        try {
            gattServer?.sendResponse(device, requestId, status, 0, null)
        } catch (_: SecurityException) {
            handleServerResponseFailure("BLE server permission was revoked.")
        }
    }

    private fun appendMessage(message: ChatMessage) {
        if (messages.any { it.id == message.id && it.isIncoming == message.isIncoming }) return
        messages.addLast(message)
        while (messages.size > MAX_STORED_MESSAGES) messages.removeFirst()
        publish(_state.copy(messages = messages.toList(), error = null))
    }

    private fun updateMessageState(messageId: Long, state: MessageDeliveryState) {
        val label = BleMessageProtocol.messageIdLabel(messageId)
        var changed = false
        val updated = messages.map { message ->
            if (message.id == label && !message.isIncoming && message.state != state) {
                changed = true
                message.copy(state = state)
            } else {
                message
            }
        }
        if (changed) {
            messages.clear()
            messages.addAll(updated)
            publish(_state.copy(messages = messages.toList()))
        }
    }

    private fun updateError(message: String) {
        publish(_state.copy(error = message))
    }

    private fun hasActivePeer(peerId: String?): Boolean {
        if (peerId == null) return false
        return (clientReady && clientPeerId == peerId) ||
            serverConnections.values.any {
                it.notificationsEnabled && it.remotePeerId == peerId
            }
    }

    private fun firstActivePeerId(): String? = when {
        clientReady -> clientPeerId
        else -> serverConnections.values.firstOrNull {
            it.notificationsEnabled && it.remotePeerId != null
        }?.remotePeerId
    }

    private fun publish(newState: BleMessagingState) {
        _state = newState
        val snapshot = newState
        if (Looper.myLooper() == Looper.getMainLooper()) {
            listeners.toList().forEach { listener -> listener(snapshot) }
        } else {
            mainHandler.post {
                listeners.toList().forEach { listener -> listener(snapshot) }
            }
        }
    }

    private fun String.hexToBytes(): ByteArray? {
        if (length == 0 || length % 2 != 0) return null
        return runCatching {
            ByteArray(length / 2) { index ->
                substring(index * 2, index * 2 + 2).toInt(16).toByte()
            }
        }.getOrNull()
    }
}
