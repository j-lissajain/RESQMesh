package com.resqmesh.app

import com.resqmesh.app.bluetooth.EmergencyRelayController
import com.resqmesh.app.bluetooth.EmergencyRelayTransport
import com.resqmesh.app.bluetooth.EmergencyTransportResult
import com.resqmesh.app.bluetooth.RelayScheduler
import com.resqmesh.app.data.EmergencyContentTrust
import com.resqmesh.app.data.EmergencyRelayQueueItem
import com.resqmesh.app.data.EmergencyRelayQueueRepository
import com.resqmesh.app.data.EmergencyReport
import com.resqmesh.app.data.EmergencyReportMerge
import com.resqmesh.app.data.EmergencyReportRepository
import com.resqmesh.app.data.EmergencyReportStatus
import com.resqmesh.app.data.EmergencySeenRepository
import com.resqmesh.app.data.EmergencySeverity
import com.resqmesh.app.data.EmergencyType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmergencyRelayControllerTest {
    @Test
    fun aToBToCForwardsOnceWithUpdatedEnvelopeAndPersistsAcrossRestart() {
        val queue = FakeQueueRepository()
        val seenB = FakeSeenRepository()
        val repoB = FakeReportRepository()
        val transportB = FakeTransport(mutableSetOf("node-a", "node-c"))
        val schedulerB = TestScheduler()
        val controllerB = EmergencyRelayController(
            repository = repoB,
            seenStore = seenB,
            queueStore = queue,
            transport = transportB,
            localNodeId = "node-b",
            nowMillis = { 100L },
            scheduler = schedulerB
        )
        val receivedAtB = receivedReport(revision = 1, hopCount = 0, ttl = 3)

        controllerB.onIncoming(receivedAtB, "node-a")

        assertEquals(1, transportB.sent.size)
        val forwardedToC = transportB.sent.single().report
        assertEquals(1, forwardedToC.hopCount)
        assertEquals(2, forwardedToC.remainingTtl)
        assertEquals("node-b", forwardedToC.lastHopId)
        assertEquals("node-a", queue.list(100L).single().excludedPeerIds.single())

        transportB.acknowledgeLast()
        schedulerB.runImmediate()
        assertEquals(true, queue.list(100L).single().deliveries["node-c"]?.acknowledged)

        controllerB.enqueue(
            receivedAtB.copy(
                contentRevision = 2,
                status = EmergencyReportStatus.LOCAL_ONLY,
                receivedFromPeerId = null,
                contentTrust = EmergencyContentTrust.LOCAL_ORIGINATED,
                originId = "node-b"
            )
        )

        val repoC = FakeReportRepository()
        val controllerC = EmergencyRelayController(
            repository = repoC,
            seenStore = FakeSeenRepository(),
            queueStore = FakeQueueRepository(),
            transport = FakeTransport(mutableSetOf("node-b")),
            localNodeId = "node-c",
            nowMillis = { 100L },
            scheduler = TestScheduler()
        )
        controllerC.onIncoming(forwardedToC, "node-b")
        assertEquals(1, repoC.list().single().hopCount)
        assertEquals(2, repoC.list().single().remainingTtl)
        assertEquals(1, repoC.list().single().contentRevision)

        controllerB.onIncoming(receivedAtB, "node-a")
        assertEquals(2, transportB.sent.size)

        controllerB.close()
        val restartedTransport = FakeTransport(mutableSetOf("node-c"))
        val restarted = EmergencyRelayController(
            repository = repoB,
            seenStore = seenB,
            queueStore = queue,
            transport = restartedTransport,
            localNodeId = "node-b",
            nowMillis = { 101L },
            scheduler = TestScheduler()
        )
        restarted.refresh()
        assertEquals(1, restartedTransport.sent.size)
        restarted.close()
        controllerC.close()
    }

    @Test
    fun staleRevisionIsStoredAsSeenButDoesNotReplaceNewerRemoteContent() {
        val repo = FakeReportRepository()
        val controller = EmergencyRelayController(
            repository = repo,
            seenStore = FakeSeenRepository(),
            queueStore = FakeQueueRepository(),
            transport = FakeTransport(mutableSetOf()),
            localNodeId = "node-b",
            nowMillis = { 100L },
            scheduler = TestScheduler()
        )
        controller.onIncoming(receivedReport(2, 0, 3, "newer"), "node-a")
        controller.onIncoming(receivedReport(1, 0, 3, "stale"), "node-a")

        assertEquals("newer", repo.list().single().description)
        assertEquals(2, repo.list().single().contentRevision)
        controller.close()
    }

    @Test
    fun invalidPayloadAndTtlExhaustionAreNotForwarded() {
        val transport = FakeTransport(mutableSetOf("node-c"))
        val repo = FakeReportRepository()
        val controller = EmergencyRelayController(
            repository = repo,
            seenStore = FakeSeenRepository(),
            queueStore = FakeQueueRepository(),
            transport = transport,
            localNodeId = "node-b",
            nowMillis = { 100L },
            scheduler = TestScheduler()
        )

        controller.onIncoming(receivedReport(1, 3, 0, "expired"), "node-a")
        controller.onIncoming(receivedReport(1, 2, 2, "invalid"), "node-a")

        assertTrue(transport.sent.isEmpty())
        assertEquals(1, repo.list().size)
        controller.close()
    }

    private fun receivedReport(
        revision: Int,
        hopCount: Int,
        ttl: Int,
        description: String = "Need help"
    ): EmergencyReport = EmergencyReport(
        id = "mesh-emergency",
        type = EmergencyType.MEDICAL,
        severity = EmergencySeverity.HIGH,
        description = description,
        createdAtMillis = 1L,
        status = EmergencyReportStatus.RECEIVED_FROM_PEER,
        originId = "node-a",
        receivedFromPeerId = "node-a",
        hopCount = hopCount,
        remainingTtl = ttl,
        lastHopId = if (hopCount == 0) null else "node-a",
        contentRevision = revision,
        contentTrust = EmergencyContentTrust.REMOTE_UNVERIFIED
    )

    private class TestScheduler : RelayScheduler {
        private val immediate = ArrayDeque<() -> Unit>()

        override fun post(delayMillis: Long, action: () -> Unit) {
            if (delayMillis == 0L) immediate += action
        }

        fun runImmediate() {
            while (immediate.isNotEmpty()) immediate.removeFirst().invoke()
        }
    }

    private class FakeTransport(
        private val active: MutableSet<String>
    ) : EmergencyRelayTransport {
        data class Sent(val report: EmergencyReport, val peerId: String, val callback: (EmergencyTransportResult, String?) -> Unit)

        val sent = mutableListOf<Sent>()
        private val listeners = mutableListOf<() -> Unit>()

        override fun connectedPeerIds(): Set<String> = active

        override fun sendEmergencyToPeer(
            report: EmergencyReport,
            peerId: String,
            callback: (EmergencyTransportResult, String?) -> Unit
        ): Boolean {
            if (peerId !in active) return false
            sent += Sent(report, peerId, callback)
            return true
        }

        override fun addConnectionListener(listener: () -> Unit): () -> Unit {
            listeners += listener
            return { listeners -= listener }
        }

        fun acknowledgeLast() {
            sent.last().callback(EmergencyTransportResult.ACKNOWLEDGED, null)
        }
    }

    private class FakeSeenRepository : EmergencySeenRepository {
        private val seen = mutableSetOf<Pair<String, Int>>()

        override fun recordIfNew(
            emergencyId: String,
            contentRevision: Int,
            atMillis: Long,
            lastHopId: String?
        ): Boolean = seen.add(emergencyId to contentRevision)
    }

    private class FakeReportRepository : EmergencyReportRepository {
        private var reports = emptyList<EmergencyReport>()

        override fun list(): List<EmergencyReport> = reports

        override fun save(report: EmergencyReport): EmergencyReport? {
            reports = EmergencyReportMerge.upsert(reports, report)
            return reports.firstOrNull { it.id == report.id }
        }

        override fun updateStatus(reportId: String, status: EmergencyReportStatus): EmergencyReport? {
            val current = reports.firstOrNull { it.id == reportId } ?: return null
            return save(current.copy(status = status))
        }
    }

    private class FakeQueueRepository : EmergencyRelayQueueRepository {
        private var items = emptyList<EmergencyRelayQueueItem>()

        override fun list(nowMillis: Long): List<EmergencyRelayQueueItem> =
            items.filter { it.expiresAtMillis > nowMillis }

        override fun upsert(item: EmergencyRelayQueueItem): EmergencyRelayQueueItem? {
            val current = list(item.queuedAtMillis).toMutableList()
            val index = current.indexOfFirst { it.report.id == item.report.id }
            if (index < 0) current += item
            else if (item.report.contentRevision > current[index].report.contentRevision) current[index] = item
            else if (item.report.contentRevision == current[index].report.contentRevision) {
                current[index] = current[index].copy(
                    excludedPeerIds = current[index].excludedPeerIds + item.excludedPeerIds
                )
            }
            items = current
            return items.firstOrNull { it.report.id == item.report.id }
        }

        override fun update(
            key: String,
            nowMillis: Long,
            transform: (EmergencyRelayQueueItem) -> EmergencyRelayQueueItem
        ): EmergencyRelayQueueItem? {
            val current = list(nowMillis).toMutableList()
            val index = current.indexOfFirst { it.key == key }
            if (index < 0) return null
            current[index] = transform(current[index])
            items = current
            return current[index]
        }

        override fun removeExpired(nowMillis: Long) {
            items = list(nowMillis)
        }
    }
}
