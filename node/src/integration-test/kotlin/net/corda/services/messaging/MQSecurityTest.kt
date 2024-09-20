package net.corda.services.messaging

import co.paralleluniverse.fibers.Suspendable
import net.corda.client.rpc.CordaRPCClient
import net.corda.client.rpc.CordaRPCConnection
import net.corda.core.crypto.random63BitValue
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.unwrap
import net.corda.coretesting.internal.configureTestSSL
import net.corda.node.internal.NodeWithInfo
import net.corda.nodeapi.RPCApi
import net.corda.nodeapi.internal.ArtemisMessagingComponent.Companion.INTERNAL_PREFIX
import net.corda.nodeapi.internal.ArtemisMessagingComponent.Companion.NOTIFICATIONS_ADDRESS
import net.corda.nodeapi.internal.config.MutualSslConfiguration
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.User
import net.corda.testing.node.internal.NodeBasedTest
import net.corda.testing.node.internal.startFlow
import org.apache.activemq.artemis.api.core.ActiveMQNonExistentQueueException
import org.apache.activemq.artemis.api.core.ActiveMQSecurityException
import org.apache.activemq.artemis.api.core.QueueConfiguration
import org.apache.activemq.artemis.api.core.RoutingType
import org.apache.activemq.artemis.api.core.SimpleString
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals

/**
 * Runs a series of MQ-related attacks against a node. Subclasses need to call [startAttacker] to connect
 * the attacker to [alice].
 */
abstract class MQSecurityTest : NodeBasedTest() {
    private val rpcUser = User("user1", "pass", permissions = emptySet())
    lateinit var alice: NodeWithInfo
    lateinit var attacker: SimpleMQClient
    private val runOnStop = ArrayList<() -> Any?>()

    @Before
    override fun setUp() {
        super.setUp()
        alice = startNode(ALICE_NAME, rpcUsers = extraRPCUsers + rpcUser)
        attacker = createAttacker()
        startAttacker(attacker)
    }

    open val extraRPCUsers: List<User> get() = emptyList()

    abstract fun createAttacker(): SimpleMQClient

    abstract fun startAttacker(attacker: SimpleMQClient)

    @After
    fun tearDown() {
        runOnStop.forEach { it() }
    }

    @Test(timeout=300_000)
	fun `create queue for valid RPC user`() {
        val user1Queue = "${RPCApi.RPC_CLIENT_QUEUE_NAME_PREFIX}.${rpcUser.username}.${random63BitValue()}"
        assertTempQueueCreationAttackFails(user1Queue)
    }

    @Test(timeout=300_000)
	fun `create queue for invalid RPC user`() {
        val invalidRPCQueue = "${RPCApi.RPC_CLIENT_QUEUE_NAME_PREFIX}.${random63BitValue()}.${random63BitValue()}"
        assertTempQueueCreationAttackFails(invalidRPCQueue)
    }

    @Test(timeout=300_000)
	open fun `send message to notifications address`() {
        assertSendAttackFails(NOTIFICATIONS_ADDRESS)
    }

    @Test(timeout=300_000)
	fun `create random internal queue`() {
        val randomQueue = "$INTERNAL_PREFIX${random63BitValue()}"
        assertAllQueueCreationAttacksFail(randomQueue)
    }

    @Test(timeout=300_000)
	fun `create random queue`() {
        val randomQueue = random63BitValue().toString()
        assertAllQueueCreationAttacksFail(randomQueue)
    }

    fun clientTo(target: NetworkHostAndPort, sslConfiguration: MutualSslConfiguration? = configureTestSSL(CordaX500Name("MegaCorp", "London", "GB"))): SimpleMQClient {
        val client = SimpleMQClient(target, sslConfiguration)
        runOnStop += client::stop
        return client
    }

    fun amqpClientTo(target: NetworkHostAndPort,
                     sslConfiguration: MutualSslConfiguration = configureTestSSL(CordaX500Name("MegaCorp", "London", "GB"))
    ): SimpleAMQPClient {
        val client = SimpleAMQPClient(target, sslConfiguration)
        runOnStop += client::stop
        return client
    }

    private val rpcConnections = mutableListOf<CordaRPCConnection>()
    private fun loginToRPC(target: NetworkHostAndPort, rpcUser: User): CordaRPCOps {
        return CordaRPCClient(target).start(rpcUser.username, rpcUser.password).also { runOnStop += it::forceClose }.proxy
    }

    fun loginToRPCAndGetClientQueue(): String {
        loginToRPC(alice.node.configuration.rpcOptions.address, rpcUser)
        val clientQueueQuery = SimpleString("${RPCApi.RPC_CLIENT_QUEUE_NAME_PREFIX}.${rpcUser.username}.*")
        val client = clientTo(alice.node.configuration.rpcOptions.address)
        client.start(rpcUser.username, rpcUser.password, false)
        return client.session.addressQuery(clientQueueQuery).queueNames.single().toString()
    }

    fun assertAllQueueCreationAttacksFail(queue: String) {
        assertNonTempQueueCreationAttackFails(queue, durable = true)
        assertNonTempQueueCreationAttackFails(queue, durable = false)
        assertTempQueueCreationAttackFails(queue)
    }

    fun assertTempQueueCreationAttackFails(queue: String) {
        assertAttackFails(queue, "CREATE_NON_DURABLE_QUEUE") {
            attacker.session.createQueue(QueueConfiguration(queue)
                    .setRoutingType(RoutingType.MULTICAST)
                    .setAddress(queue)
                    .setTemporary(true)
                    .setDurable(false))
        }
        // Double-check
        assertThatExceptionOfType(ActiveMQNonExistentQueueException::class.java).isThrownBy {
            attacker.session.createConsumer(queue)
        }
    }

    fun assertAttackFailsNonexistent(queue: String, attack: () -> Unit) {
        assertThatExceptionOfType(ActiveMQNonExistentQueueException::class.java)
                .isThrownBy(attack)
                .withMessageContaining(queue)
    }

    fun assertNonTempQueueCreationAttackFails(queue: String, durable: Boolean) {
        val permission = if (durable) "CREATE_DURABLE_QUEUE" else "CREATE_NON_DURABLE_QUEUE"
        assertAttackFails(queue, permission) {
            attacker.session.createQueue(
                    QueueConfiguration(queue).setAddress(queue).setRoutingType(RoutingType.MULTICAST).setDurable(durable))
        }
        // Double-check
        assertThatExceptionOfType(ActiveMQNonExistentQueueException::class.java).isThrownBy {
            attacker.session.createConsumer(queue)
        }
    }

    open fun assertSendAttackFails(address: String) {
        val message = attacker.createMessage()
        assertEquals(true, attacker.producer.isBlockOnNonDurableSend)
        assertAttackFails(address, "SEND") {
            attacker.producer.send(address, message)
        }
        assertEquals(0, message.deliveryCount)
        assertEquals(0, message.bodySize)
    }

    fun assertConsumeAttackFails(queue: String) {
        assertAttackFails(queue, "CONSUME") {
            attacker.session.createConsumer(queue)
        }
        assertAttackFails(queue, "BROWSE") {
            attacker.session.createConsumer(queue, true)
        }
    }

    fun assertConsumeAttackFailsNonexistent(queue: String) {
        assertAttackFailsNonexistent(queue) {
            attacker.session.createConsumer(queue)
        }
        assertAttackFailsNonexistent(queue) {
            attacker.session.createConsumer(queue, true)
        }
    }

    fun assertAttackFails(queue: String, permission: String, attack: () -> Unit) {
        assertThatExceptionOfType(ActiveMQSecurityException::class.java)
                .isThrownBy(attack)
                .withMessageContaining(queue)
                .withMessageContaining(permission)
    }

    protected fun startBobAndCommunicateWithAlice(): Party {
        val bob = startNode(BOB_NAME)
        val bobParty = bob.info.singleIdentity()
        // Perform a protocol exchange to force the peer queue to be created
        alice.services.startFlow(SendFlow(bobParty, 0)).resultFuture.getOrThrow()
        return bobParty
    }

    @InitiatingFlow
    private class SendFlow(val otherParty: Party, val payload: Any) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() = initiateFlow(otherParty).send(payload)
    }

    @InitiatedBy(SendFlow::class)
    private class ReceiveFlow(val otherPartySession: FlowSession) : FlowLogic<Any>() {
        @Suspendable
        override fun call() = otherPartySession.receive<Any>().unwrap { it }
    }
}