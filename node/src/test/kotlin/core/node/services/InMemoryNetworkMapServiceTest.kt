package core.node.services

import co.paralleluniverse.fibers.Suspendable
import core.*
import core.crypto.SecureHash
import core.crypto.signWithECDSA
import core.node.NodeInfo
import core.protocols.ProtocolLogic
import core.serialization.serialize
import core.testing.MockNetwork
import core.utilities.AddOrRemove
import core.utilities.BriefLogFormatter
import org.junit.Before
import org.junit.Test
import java.security.PrivateKey
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InMemoryNetworkMapServiceTest {
    lateinit var network: MockNetwork

    init {
        BriefLogFormatter.init()
    }

    @Before
    fun setup() {
        network = MockNetwork()
    }

    /**
     * Perform basic tests of registering, de-registering and fetching the full network map.
     */
    @Test
    fun success() {
        val (mapServiceNode, registerNode) = network.createTwoNodes()
        val service = mapServiceNode.inNodeNetworkMapService!! as InMemoryNetworkMapService

        // Confirm the service contains only its own node
        assertEquals(1, service.nodes.count())
        assertNull(service.processQueryRequest(NetworkMapService.QueryIdentityRequest(registerNode.info.identity, mapServiceNode.info.address, Long.MIN_VALUE)).node)

        // Register the second node
        var seq = 1L
        val expires = Instant.now() + NetworkMapService.DEFAULT_EXPIRATION_PERIOD
        val nodeKey = registerNode.storage.myLegalIdentityKey
        val addChange = NodeRegistration(registerNode.info, seq++, AddOrRemove.ADD, expires)
        val addWireChange = addChange.toWire(nodeKey.private)
        service.processRegistrationChangeRequest(NetworkMapService.RegistrationRequest(addWireChange, mapServiceNode.info.address, Long.MIN_VALUE))
        assertEquals(2, service.nodes.count())
        assertEquals(mapServiceNode.info, service.processQueryRequest(NetworkMapService.QueryIdentityRequest(mapServiceNode.info.identity, mapServiceNode.info.address, Long.MIN_VALUE)).node)

        // Re-registering should be a no-op
        service.processRegistrationChangeRequest(NetworkMapService.RegistrationRequest(addWireChange, mapServiceNode.info.address, Long.MIN_VALUE))
        assertEquals(2, service.nodes.count())

        // Confirm that de-registering the node succeeds and drops it from the node lists
        var removeChange = NodeRegistration(registerNode.info, seq, AddOrRemove.REMOVE, expires)
        val removeWireChange = removeChange.toWire(nodeKey.private)
        assert(service.processRegistrationChangeRequest(NetworkMapService.RegistrationRequest(removeWireChange, mapServiceNode.info.address, Long.MIN_VALUE)).success)
        assertNull(service.processQueryRequest(NetworkMapService.QueryIdentityRequest(registerNode.info.identity, mapServiceNode.info.address, Long.MIN_VALUE)).node)

        // Trying to de-register a node that doesn't exist should fail
        assert(!service.processRegistrationChangeRequest(NetworkMapService.RegistrationRequest(removeWireChange, mapServiceNode.info.address, Long.MIN_VALUE)).success)
    }

    class TestAcknowledgePSM(val server: NodeInfo, val hash: SecureHash)
    : ProtocolLogic<Unit>() {
        @Suspendable
        override fun call() {
            val req = NetworkMapService.UpdateAcknowledge(hash, serviceHub.networkService.myAddress)
            send(NetworkMapService.PUSH_ACK_PROTOCOL_TOPIC, server.address, 0, req)
        }
    }

    class TestFetchPSM(val server: NodeInfo, val subscribe: Boolean, val ifChangedSinceVersion: Int? = null)
    : ProtocolLogic<Collection<NodeRegistration>?>() {
        @Suspendable
        override fun call(): Collection<NodeRegistration>? {
            val sessionID = random63BitValue()
            val req = NetworkMapService.FetchMapRequest(subscribe, ifChangedSinceVersion, serviceHub.networkService.myAddress, sessionID)
            return sendAndReceive<NetworkMapService.FetchMapResponse>(
                    NetworkMapService.FETCH_PROTOCOL_TOPIC, server.address, 0, sessionID, req)
                    .validate { it.nodes }
        }
    }

    class TestRegisterPSM(val server: NodeInfo, val reg: NodeRegistration, val privateKey: PrivateKey)
    : ProtocolLogic<NetworkMapService.RegistrationResponse>() {
        @Suspendable
        override fun call(): NetworkMapService.RegistrationResponse {
            val sessionID = random63BitValue()
            val req = NetworkMapService.RegistrationRequest(reg.toWire(privateKey), serviceHub.networkService.myAddress, sessionID)

            return sendAndReceive<NetworkMapService.RegistrationResponse>(
                    NetworkMapService.REGISTER_PROTOCOL_TOPIC, server.address, 0, sessionID, req)
                    .validate { it }
        }
    }

    class TestSubscribePSM(val server: NodeInfo, val subscribe: Boolean)
    : ProtocolLogic<NetworkMapService.SubscribeResponse>() {
        @Suspendable
        override fun call(): NetworkMapService.SubscribeResponse {
            val sessionID = random63BitValue()
            val req = NetworkMapService.SubscribeRequest(subscribe, serviceHub.networkService.myAddress, sessionID)

            return sendAndReceive<NetworkMapService.SubscribeResponse>(
                    NetworkMapService.SUBSCRIPTION_PROTOCOL_TOPIC, server.address, 0, sessionID, req)
                    .validate { it }
        }
    }

    @Test
    fun successWithNetwork() {
        val (mapServiceNode, registerNode) = network.createTwoNodes()

        // Confirm there's a network map service on node 0
        assertNotNull(mapServiceNode.inNodeNetworkMapService)

        // Confirm all nodes have registered themselves
        var fetchPsm = registerNode.smm.add(NetworkMapService.FETCH_PROTOCOL_TOPIC, TestFetchPSM(mapServiceNode.info, false))
        network.runNetwork()
        assertEquals(2, fetchPsm.get()?.count())

        // Forcibly deregister the second node
        val nodeKey = registerNode.storage.myLegalIdentityKey
        val expires = Instant.now() + NetworkMapService.DEFAULT_EXPIRATION_PERIOD
        val seq = 2L
        val reg = NodeRegistration(registerNode.info, seq, AddOrRemove.REMOVE, expires)
        val registerPsm = registerNode.smm.add(NetworkMapService.REGISTER_PROTOCOL_TOPIC, TestRegisterPSM(mapServiceNode.info, reg, nodeKey.private))
        network.runNetwork()
        assertTrue(registerPsm.get().success)

        // Now only map service node should be registered
        fetchPsm = registerNode.smm.add(NetworkMapService.FETCH_PROTOCOL_TOPIC, TestFetchPSM(mapServiceNode.info, false))
        network.runNetwork()
        assertEquals(mapServiceNode.info, fetchPsm.get()?.filter { it.type == AddOrRemove.ADD }?.map { it.node }?.single())
    }

    @Test
    fun subscribeWithNetwork() {
        val (mapServiceNode, registerNode) = network.createTwoNodes()
        val service = (mapServiceNode.inNodeNetworkMapService as InMemoryNetworkMapService)

        // Test subscribing to updates
        val subscribePsm = registerNode.smm.add(NetworkMapService.SUBSCRIPTION_PROTOCOL_TOPIC,
                TestSubscribePSM(mapServiceNode.info, true))
        network.runNetwork()
        subscribePsm.get()

        // Check the unacknowledged count is zero
        assertEquals(0, service.getUnacknowledgedCount(registerNode.info.address))

        // Fire off an update
        val nodeKey = registerNode.storage.myLegalIdentityKey
        var seq = 1L
        val expires = Instant.now() + NetworkMapService.DEFAULT_EXPIRATION_PERIOD
        var reg = NodeRegistration(registerNode.info, seq++, AddOrRemove.ADD, expires)
        var wireReg = reg.toWire(nodeKey.private)
        service.notifySubscribers(wireReg)

        // Check the unacknowledged count is one
        assertEquals(1, service.getUnacknowledgedCount(registerNode.info.address))

        // Send in an acknowledgment and verify the count goes down
        val hash = SecureHash.sha256(wireReg.raw.bits)
        val acknowledgePsm = registerNode.smm.add(NetworkMapService.PUSH_ACK_PROTOCOL_TOPIC,
                TestAcknowledgePSM(mapServiceNode.info, hash))
        network.runNetwork()
        acknowledgePsm.get()

        assertEquals(0, service.getUnacknowledgedCount(registerNode.info.address))

        // Intentionally fill the pending acknowledgements to verify it doesn't drop subscribers before the limit
        // is hit. On the last iteration overflow the pending list, and check the node is unsubscribed
        for (i in 0..service.maxUnacknowledgedUpdates) {
            reg = NodeRegistration(registerNode.info, seq++, AddOrRemove.ADD, expires)
            wireReg = reg.toWire(nodeKey.private)
            service.notifySubscribers(wireReg)
            if (i < service.maxUnacknowledgedUpdates) {
                assertEquals(i + 1, service.getUnacknowledgedCount(registerNode.info.address))
            } else {
                assertNull(service.getUnacknowledgedCount(registerNode.info.address))
            }
        }
    }
}
