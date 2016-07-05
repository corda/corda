package com.r3corda.node.services.statemachine

import co.paralleluniverse.fibers.Fiber
import co.paralleluniverse.fibers.Suspendable
import com.r3corda.core.messaging.MessagingService
import com.r3corda.core.protocols.ProtocolLogic
import com.r3corda.node.services.MockServices
import com.r3corda.node.services.api.Checkpoint
import com.r3corda.node.services.api.CheckpointStorage
import com.r3corda.node.services.network.InMemoryMessagingNetwork
import com.r3corda.node.utilities.AffinityExecutor
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Test
import java.util.*

class StateMachineManagerTests {

    val checkpointStorage = RecordingCheckpointStorage()
    val network = InMemoryMessagingNetwork(false).InMemoryMessaging(true, InMemoryMessagingNetwork.Handle(1, "mock"))
    val smm = createManager()

    @After
    fun cleanUp() {
        network.stop()
    }

    @Test
    fun `newly added protocol is preserved on restart`() {
        smm.add("test", ProtocolWithoutCheckpoints())
        // Ensure we're restoring from the original add checkpoint
        assertThat(checkpointStorage.allCheckpoints).hasSize(1)
        val restoredProtocol = createManager().run {
            start()
            findStateMachines(ProtocolWithoutCheckpoints::class.java).single().first
        }
        assertThat(restoredProtocol.protocolStarted).isTrue()
    }

    @Test
    fun `protocol can lazily use the serviceHub in its constructor`() {
        val protocol = ProtocolWithLazyServiceHub()
        smm.add("test", protocol)
        assertThat(protocol.lazyTime).isNotNull()
    }

    private fun createManager() = StateMachineManager(object : MockServices() {
        override val networkService: MessagingService get() = network
    }, emptyList(), checkpointStorage, AffinityExecutor.SAME_THREAD)



    private class ProtocolWithoutCheckpoints : ProtocolLogic<Unit>() {

        @Transient var protocolStarted = false

        @Suspendable
        override fun call() {
            protocolStarted = true
            Fiber.park()
        }

        override val topic: String get() = throw UnsupportedOperationException()
    }


    private class ProtocolWithLazyServiceHub : ProtocolLogic<Unit>() {

        val lazyTime by lazy { serviceHub.clock.instant() }

        @Suspendable
        override fun call() {}

        override val topic: String get() = throw UnsupportedOperationException()
    }


    class RecordingCheckpointStorage : CheckpointStorage {

        private val _checkpoints = ArrayList<Checkpoint>()
        val allCheckpoints = ArrayList<Checkpoint>()

        override fun addCheckpoint(checkpoint: Checkpoint) {
            _checkpoints.add(checkpoint)
            allCheckpoints.add(checkpoint)
        }

        override fun removeCheckpoint(checkpoint: Checkpoint) {
            _checkpoints.remove(checkpoint)
        }

        override val checkpoints: Iterable<Checkpoint> get() = _checkpoints
    }

}
