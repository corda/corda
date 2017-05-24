package net.corda.node.services.network

import com.google.common.util.concurrent.ListenableFuture
import net.corda.core.getOrThrow
import net.corda.core.messaging.SingleMessageRecipient
import net.corda.core.node.NodeInfo
import net.corda.core.node.services.DEFAULT_SESSION_ID
import net.corda.core.node.services.ServiceInfo
import net.corda.core.serialization.deserialize
import net.corda.core.utilities.ALICE
import net.corda.core.utilities.BOB
import net.corda.core.utilities.CHARLIE
import net.corda.core.utilities.DUMMY_MAP
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.messaging.send
import net.corda.node.services.messaging.sendRequest
import net.corda.node.services.network.AbstractNetworkMapServiceTest.Changed.Added
import net.corda.node.services.network.AbstractNetworkMapServiceTest.Changed.Removed
import net.corda.node.services.network.NetworkMapService.*
import net.corda.node.services.network.NetworkMapService.Companion.FETCH_TOPIC
import net.corda.node.services.network.NetworkMapService.Companion.PUSH_ACK_TOPIC
import net.corda.node.services.network.NetworkMapService.Companion.PUSH_TOPIC
import net.corda.node.services.network.NetworkMapService.Companion.QUERY_TOPIC
import net.corda.node.services.network.NetworkMapService.Companion.REGISTER_TOPIC
import net.corda.node.services.network.NetworkMapService.Companion.SUBSCRIPTION_TOPIC
import net.corda.node.utilities.AddOrRemove
import net.corda.node.utilities.AddOrRemove.ADD
import net.corda.node.utilities.AddOrRemove.REMOVE
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetwork.MockNode
import org.assertj.core.api.Assertions.assertThat
import org.bouncycastle.asn1.x500.X500Name
import org.eclipse.jetty.util.BlockingArrayQueue
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.math.BigInteger
import java.security.KeyPair
import java.time.Instant

abstract class AbstractNetworkMapServiceTest<out S : AbstractNetworkMapService> {
    lateinit var network: MockNetwork
    lateinit var mapServiceNode: MockNode
    lateinit var alice: MockNode

    companion object {
        val subscriberLegalName = X500Name("CN=Subscriber,OU=Corda QA Department,O=R3 CEV,L=New York,C=US")
    }

    @Before
    fun setup() {
        network = MockNetwork(defaultFactory = nodeFactory)
        network.createTwoNodes(firstNodeName = DUMMY_MAP.name, secondNodeName = ALICE.name).apply {
            mapServiceNode = first
            alice = second
        }
        network.runNetwork()
        lastSerial = System.currentTimeMillis()
    }

    @After
    fun tearDown() {
        network.stopNodes()
    }

    protected abstract val nodeFactory: MockNetwork.Factory

    protected abstract val networkMapService: S

    // For persistent service, switch out the implementation for a newly instantiated one so we can check the state is preserved.
    protected abstract fun swizzle()

    @Test
    fun `all nodes register themselves`() {
        // setup has run the network and so we immediately expect the network map service to be correctly populated
        assertThat(alice.fetchMap()).containsOnly(Added(mapServiceNode), Added(alice))
        assertThat(alice.identityQuery()).isEqualTo(alice.info)
        assertThat(mapServiceNode.identityQuery()).isEqualTo(mapServiceNode.info)
    }

    @Test
    fun `re-register the same node`() {
        val response = alice.registration(ADD)
        swizzle()
        assertThat(response.getOrThrow().error).isNull()
        assertThat(alice.fetchMap()).containsOnly(Added(mapServiceNode), Added(alice))  // Confirm it's a no-op
    }

    @Test
    fun `re-register with smaller serial value`() {
        val response = alice.registration(ADD, serial = 1)
        swizzle()
        assertThat(response.getOrThrow().error).isNotNull()  // Make sure send error message is sent back
        assertThat(alice.fetchMap()).containsOnly(Added(mapServiceNode), Added(alice))  // Confirm it's a no-op
    }

    @Test
    fun `de-register node`() {
        val response = alice.registration(REMOVE)
        swizzle()
        assertThat(response.getOrThrow().error).isNull()
        assertThat(alice.fetchMap()).containsOnly(Added(mapServiceNode), Removed(alice))
        swizzle()
        assertThat(alice.identityQuery()).isNull()
        assertThat(mapServiceNode.identityQuery()).isEqualTo(mapServiceNode.info)
    }

    @Test
    fun `de-register same node again`() {
        alice.registration(REMOVE)
        val response = alice.registration(REMOVE)
        swizzle()
        assertThat(response.getOrThrow().error).isNotNull()  // Make sure send error message is sent back
        assertThat(alice.fetchMap()).containsOnly(Added(mapServiceNode), Removed(alice))
    }

    @Test
    fun `de-register unknown node`() {
        val bob = newNodeSeparateFromNetworkMap(BOB.name)
        val response = bob.registration(REMOVE)
        swizzle()
        assertThat(response.getOrThrow().error).isNotNull()  // Make sure send error message is sent back
        assertThat(alice.fetchMap()).containsOnly(Added(mapServiceNode), Added(alice))
    }

    @Test
    fun `subscribed while new node registers`() {
        val updates = alice.subscribe()
        swizzle()
        val bob = addNewNodeToNetworkMap(BOB.name)
        swizzle()
        val update = updates.single()
        assertThat(update.mapVersion).isEqualTo(networkMapService.mapVersion)
        assertThat(update.wireReg.verified().toChanged()).isEqualTo(Added(bob.info))
    }

    @Test
    fun `subscribed while node de-registers`() {
        val bob = addNewNodeToNetworkMap(BOB.name)
        val updates = alice.subscribe()
        bob.registration(REMOVE)
        swizzle()
        assertThat(updates.map { it.wireReg.verified().toChanged() }).containsOnly(Removed(bob.info))
    }

    @Test
    fun unsubscribe() {
        val updates = alice.subscribe()
        val bob = addNewNodeToNetworkMap(BOB.name)
        alice.unsubscribe()
        addNewNodeToNetworkMap(CHARLIE.name)
        swizzle()
        assertThat(updates.map { it.wireReg.verified().toChanged() }).containsOnly(Added(bob.info))
    }

    @Test
    fun `surpass unacknowledged update limit`() {
        val subscriber = newNodeSeparateFromNetworkMap(subscriberLegalName)
        val updates = subscriber.subscribe()
        val bob = addNewNodeToNetworkMap(BOB.name)
        var serial = updates.first().wireReg.verified().serial
        repeat(networkMapService.maxUnacknowledgedUpdates) {
            bob.registration(ADD, serial = ++serial)
            swizzle()
        }
        // We sent maxUnacknowledgedUpdates + 1 updates - the last one will be missed
        assertThat(updates).hasSize(networkMapService.maxUnacknowledgedUpdates)
    }

    @Test
    fun `delay sending update ack until just before unacknowledged update limit`() {
        val subscriber = newNodeSeparateFromNetworkMap(subscriberLegalName)
        val updates = subscriber.subscribe()
        val bob = addNewNodeToNetworkMap(BOB.name)
        var serial = updates.first().wireReg.verified().serial
        repeat(networkMapService.maxUnacknowledgedUpdates - 1) {
            bob.registration(ADD, serial = ++serial)
            swizzle()
        }
        // Subscriber will receive maxUnacknowledgedUpdates updates before sending ack
        subscriber.ackUpdate(updates.last().mapVersion)
        swizzle()
        bob.registration(ADD, serial = ++serial)
        assertThat(updates).hasSize(networkMapService.maxUnacknowledgedUpdates + 1)
        assertThat(updates.last().wireReg.verified().serial).isEqualTo(serial)
    }

    private fun MockNode.fetchMap(subscribe: Boolean = false, ifChangedSinceVersion: Int? = null): List<Changed> {
        val request = FetchMapRequest(subscribe, ifChangedSinceVersion, info.address)
        val response = services.networkService.sendRequest<FetchMapResponse>(FETCH_TOPIC, request, mapServiceNode.info.address)
        network.runNetwork()
        return response.getOrThrow().nodes?.map { it.toChanged() } ?: emptyList()
    }

    private fun NodeRegistration.toChanged(): Changed = when (type) {
        ADD -> Added(node)
        REMOVE -> Removed(node)
    }

    private fun MockNode.identityQuery(): NodeInfo? {
        val request = QueryIdentityRequest(info.legalIdentity, info.address)
        val response = services.networkService.sendRequest<QueryIdentityResponse>(QUERY_TOPIC, request, mapServiceNode.info.address)
        network.runNetwork()
        return response.getOrThrow().node
    }

    private var lastSerial = Long.MIN_VALUE

    private fun MockNode.registration(addOrRemove: AddOrRemove,
                                      serial: Long? = null): ListenableFuture<RegistrationResponse> {
        val distinctSerial = if (serial == null) {
            ++lastSerial
        } else {
            lastSerial = serial
            serial
        }
        val expires = Instant.now() + NetworkMapService.DEFAULT_EXPIRATION_PERIOD
        val nodeRegistration = NodeRegistration(info, distinctSerial, addOrRemove, expires)
        val request = RegistrationRequest(nodeRegistration.toWire(services.keyManagementService, services.legalIdentityKey), info.address)
        val response = services.networkService.sendRequest<RegistrationResponse>(REGISTER_TOPIC, request, mapServiceNode.info.address)
        network.runNetwork()
        return response
    }

    private fun MockNode.subscribe(): List<Update> {
        val request = SubscribeRequest(true, info.address)
        val updates = BlockingArrayQueue<Update>()
        services.networkService.addMessageHandler(PUSH_TOPIC, DEFAULT_SESSION_ID) { message, _ ->
            updates += message.data.deserialize<Update>()
        }
        val response = services.networkService.sendRequest<SubscribeResponse>(SUBSCRIPTION_TOPIC, request, mapServiceNode.info.address)
        network.runNetwork()
        assertThat(response.getOrThrow().confirmed).isTrue()
        return updates
    }

    private fun MockNode.unsubscribe() {
        val request = SubscribeRequest(false, info.address)
        val response = services.networkService.sendRequest<SubscribeResponse>(SUBSCRIPTION_TOPIC, request, mapServiceNode.info.address)
        network.runNetwork()
        assertThat(response.getOrThrow().confirmed).isTrue()
    }

    private fun MockNode.ackUpdate(mapVersion: Int) {
        val request = UpdateAcknowledge(mapVersion, services.networkService.myAddress)
        services.networkService.send(PUSH_ACK_TOPIC, DEFAULT_SESSION_ID, request, mapServiceNode.info.address)
        network.runNetwork()
    }

    private fun addNewNodeToNetworkMap(legalName: X500Name): MockNode {
        val node = network.createNode(networkMapAddress = mapServiceNode.info.address, legalName = legalName)
        network.runNetwork()
        lastSerial = System.currentTimeMillis()
        return node
    }

    private fun newNodeSeparateFromNetworkMap(legalName: X500Name): MockNode {
        return network.createNode(legalName = legalName, nodeFactory = NoNMSNodeFactory)
    }

    sealed class Changed {
        data class Added(val node: NodeInfo) : Changed() {
            constructor(node: MockNode) : this(node.info)
        }

        data class Removed(val node: NodeInfo) : Changed() {
            constructor(node: MockNode) : this(node.info)
        }
    }

    private object NoNMSNodeFactory : MockNetwork.Factory {
        override fun create(config: NodeConfiguration,
                            network: MockNetwork,
                            networkMapAddr: SingleMessageRecipient?,
                            advertisedServices: Set<ServiceInfo>,
                            id: Int,
                            overrideServices: Map<ServiceInfo, KeyPair>?,
                            entropyRoot: BigInteger): MockNode {
            return object : MockNode(config, network, networkMapAddr, advertisedServices, id, overrideServices, entropyRoot) {
                override fun makeNetworkMapService() {}
            }
        }
    }
}
