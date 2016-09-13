package com.r3corda.node.services

import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.r3corda.core.map
import com.r3corda.core.messaging.TopicSession
import com.r3corda.core.messaging.runOnNextMessage
import com.r3corda.core.messaging.send
import com.r3corda.core.random63BitValue
import com.r3corda.core.serialization.deserialize
import com.r3corda.node.services.network.InMemoryNetworkMapService
import com.r3corda.node.services.network.NetworkMapService
import com.r3corda.node.services.network.NetworkMapService.*
import com.r3corda.node.services.network.NetworkMapService.Companion.FETCH_PROTOCOL_TOPIC
import com.r3corda.node.services.network.NetworkMapService.Companion.PUSH_ACK_PROTOCOL_TOPIC
import com.r3corda.node.services.network.NetworkMapService.Companion.REGISTER_PROTOCOL_TOPIC
import com.r3corda.node.services.network.NetworkMapService.Companion.SUBSCRIPTION_PROTOCOL_TOPIC
import com.r3corda.node.services.network.NodeRegistration
import com.r3corda.node.utilities.AddOrRemove
import com.r3corda.protocols.ServiceRequestMessage
import com.r3corda.testing.node.MockNetwork
import com.r3corda.testing.node.MockNetwork.MockNode
import org.junit.Before
import org.junit.Test
import java.security.PrivateKey
import java.time.Instant
import java.util.concurrent.Future
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InMemoryNetworkMapServiceTest {
    lateinit var network: MockNetwork

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
        assertNull(service.processQueryRequest(QueryIdentityRequest(registerNode.info.identity, mapServiceNode.info.address, Long.MIN_VALUE)).node)

        // Register the second node
        var seq = 1L
        val expires = Instant.now() + NetworkMapService.DEFAULT_EXPIRATION_PERIOD
        val nodeKey = registerNode.storage.myLegalIdentityKey
        val addChange = NodeRegistration(registerNode.info, seq++, AddOrRemove.ADD, expires)
        val addWireChange = addChange.toWire(nodeKey.private)
        service.processRegistrationChangeRequest(RegistrationRequest(addWireChange, mapServiceNode.info.address, Long.MIN_VALUE))
        assertEquals(2, service.nodes.count())
        assertEquals(mapServiceNode.info, service.processQueryRequest(QueryIdentityRequest(mapServiceNode.info.identity, mapServiceNode.info.address, Long.MIN_VALUE)).node)

        // Re-registering should be a no-op
        service.processRegistrationChangeRequest(RegistrationRequest(addWireChange, mapServiceNode.info.address, Long.MIN_VALUE))
        assertEquals(2, service.nodes.count())

        // Confirm that de-registering the node succeeds and drops it from the node lists
        val removeChange = NodeRegistration(registerNode.info, seq, AddOrRemove.REMOVE, expires)
        val removeWireChange = removeChange.toWire(nodeKey.private)
        assert(service.processRegistrationChangeRequest(RegistrationRequest(removeWireChange, mapServiceNode.info.address, Long.MIN_VALUE)).success)
        assertNull(service.processQueryRequest(QueryIdentityRequest(registerNode.info.identity, mapServiceNode.info.address, Long.MIN_VALUE)).node)

        // Trying to de-register a node that doesn't exist should fail
        assert(!service.processRegistrationChangeRequest(RegistrationRequest(removeWireChange, mapServiceNode.info.address, Long.MIN_VALUE)).success)
    }

    @Test
    fun `success with network`() {
        val (mapServiceNode, registerNode) = network.createTwoNodes()

        // Confirm there's a network map service on node 0
        assertNotNull(mapServiceNode.inNodeNetworkMapService)

        // Confirm all nodes have registered themselves
        network.runNetwork()
        var fetchPsm = fetchMap(registerNode, mapServiceNode, false)
        network.runNetwork()
        assertEquals(2, fetchPsm.get()?.count())

        // Forcibly deregister the second node
        val nodeKey = registerNode.storage.myLegalIdentityKey
        val expires = Instant.now() + NetworkMapService.DEFAULT_EXPIRATION_PERIOD
        val seq = 2L
        val reg = NodeRegistration(registerNode.info, seq, AddOrRemove.REMOVE, expires)
        val registerPsm = registration(registerNode, mapServiceNode, reg, nodeKey.private)
        network.runNetwork()
        assertTrue(registerPsm.get().success)

        // Now only map service node should be registered
        fetchPsm = fetchMap(registerNode, mapServiceNode, false)
        network.runNetwork()
        assertEquals(mapServiceNode.info, fetchPsm.get()?.filter { it.type == AddOrRemove.ADD }?.map { it.node }?.single())
    }

    @Test
    fun `subscribe with network`() {
        val (mapServiceNode, registerNode) = network.createTwoNodes()
        val service = (mapServiceNode.inNodeNetworkMapService as InMemoryNetworkMapService)

        // Test subscribing to updates
        network.runNetwork()
        val subscribePsm = subscribe(registerNode, mapServiceNode, true)
        network.runNetwork()
        subscribePsm.get()

        val startingMapVersion = service.mapVersion

        // Check the unacknowledged count is zero
        assertEquals(0, service.getUnacknowledgedCount(registerNode.info.address, startingMapVersion))

        // Fire off an update
        val nodeKey = registerNode.storage.myLegalIdentityKey
        var seq = 0L
        val expires = Instant.now() + NetworkMapService.DEFAULT_EXPIRATION_PERIOD
        var reg = NodeRegistration(registerNode.info, seq++, AddOrRemove.ADD, expires)
        var wireReg = reg.toWire(nodeKey.private)
        service.notifySubscribers(wireReg, startingMapVersion + 1)

        // Check the unacknowledged count is one
        assertEquals(1, service.getUnacknowledgedCount(registerNode.info.address, startingMapVersion + 1))

        // Send in an acknowledgment and verify the count goes down
        updateAcknowlege(registerNode, mapServiceNode, startingMapVersion + 1)
        network.runNetwork()

        assertEquals(0, service.getUnacknowledgedCount(registerNode.info.address, startingMapVersion + 1))

        // Intentionally fill the pending acknowledgements to verify it doesn't drop subscribers before the limit
        // is hit. On the last iteration overflow the pending list, and check the node is unsubscribed
        for (i in 0..service.maxUnacknowledgedUpdates) {
            reg = NodeRegistration(registerNode.info, seq++, AddOrRemove.ADD, expires)
            wireReg = reg.toWire(nodeKey.private)
            service.notifySubscribers(wireReg, i + startingMapVersion + 2)
            if (i < service.maxUnacknowledgedUpdates) {
                assertEquals(i + 1, service.getUnacknowledgedCount(registerNode.info.address, i + startingMapVersion + 2))
            } else {
                assertNull(service.getUnacknowledgedCount(registerNode.info.address, i + startingMapVersion + 2))
            }
        }
    }

    private fun registration(registerNode: MockNode, mapServiceNode: MockNode, reg: NodeRegistration, privateKey: PrivateKey): ListenableFuture<RegistrationResponse> {
        val req = RegistrationRequest(reg.toWire(privateKey), registerNode.services.networkService.myAddress, random63BitValue())
        return registerNode.sendAndReceive<RegistrationResponse>(REGISTER_PROTOCOL_TOPIC, mapServiceNode, req)
    }

    private fun subscribe(registerNode: MockNode, mapServiceNode: MockNode, subscribe: Boolean): ListenableFuture<SubscribeResponse> {
        val req = SubscribeRequest(subscribe, registerNode.services.networkService.myAddress, random63BitValue())
        return registerNode.sendAndReceive<SubscribeResponse>(SUBSCRIPTION_PROTOCOL_TOPIC, mapServiceNode, req)
    }

    private fun updateAcknowlege(registerNode: MockNode, mapServiceNode: MockNode, mapVersion: Int) {
        val req = UpdateAcknowledge(mapVersion, registerNode.services.networkService.myAddress)
        registerNode.send(PUSH_ACK_PROTOCOL_TOPIC, mapServiceNode, req)
    }

    private fun fetchMap(registerNode: MockNode, mapServiceNode: MockNode, subscribe: Boolean, ifChangedSinceVersion: Int? = null): Future<Collection<NodeRegistration>?> {
        val req = FetchMapRequest(subscribe, ifChangedSinceVersion, registerNode.services.networkService.myAddress, random63BitValue())
        return registerNode.sendAndReceive<FetchMapResponse>(FETCH_PROTOCOL_TOPIC, mapServiceNode, req).map { it.nodes }
    }

}