/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

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
import net.corda.node.internal.StartedNodeWithInternals
import net.corda.nodeapi.RPCApi
import net.corda.nodeapi.internal.ArtemisMessagingComponent.Companion.INTERNAL_PREFIX
import net.corda.nodeapi.internal.ArtemisMessagingComponent.Companion.NOTIFICATIONS_ADDRESS
import net.corda.nodeapi.internal.config.SSLConfiguration
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.internal.IntegrationTestSchemas
import net.corda.testing.node.User
import net.corda.testing.core.singleIdentity
import net.corda.testing.internal.configureTestSSL
import net.corda.testing.internal.toDatabaseSchemaName
import net.corda.testing.node.internal.NodeBasedTest
import net.corda.testing.node.internal.TestStartedNode
import net.corda.testing.node.internal.startFlow
import org.apache.activemq.artemis.api.core.ActiveMQNonExistentQueueException
import org.apache.activemq.artemis.api.core.ActiveMQSecurityException
import org.apache.activemq.artemis.api.core.RoutingType
import org.apache.activemq.artemis.api.core.SimpleString
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.ClassRule
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals

/**
 * Runs a series of MQ-related attacks against a node. Subclasses need to call [startAttacker] to connect
 * the attacker to [alice].
 */
abstract class MQSecurityTest : NodeBasedTest() {
    companion object {
        @ClassRule
        @JvmField
        val databaseSchemas = IntegrationTestSchemas(ALICE_NAME.toDatabaseSchemaName(), BOB_NAME.toDatabaseSchemaName())
    }

    val rpcUser = User("user1", "pass", permissions = emptySet())
    lateinit var alice: StartedNodeWithInternals
    lateinit var attacker: SimpleMQClient
    private val clients = ArrayList<SimpleMQClient>()

    override fun setUp() {
        super.init()
        super.setUp()
        alice = startNode(ALICE_NAME, rpcUsers = extraRPCUsers + rpcUser)
        attacker = createAttacker()
        startAttacker(attacker)
    }

    open val extraRPCUsers: List<User> get() = emptyList()

    abstract fun createAttacker(): SimpleMQClient

    abstract fun startAttacker(attacker: SimpleMQClient)

    override fun tearDown() {
        rpcConnections.forEach { it.forceClose() }
        clients.forEach { it.stop() }
        super.tearDown()
    }

    @Test
    fun `create queue for valid RPC user`() {
        val user1Queue = "${RPCApi.RPC_CLIENT_QUEUE_NAME_PREFIX}.${rpcUser.username}.${random63BitValue()}"
        assertTempQueueCreationAttackFails(user1Queue)
    }

    @Test
    fun `create queue for invalid RPC user`() {
        val invalidRPCQueue = "${RPCApi.RPC_CLIENT_QUEUE_NAME_PREFIX}.${random63BitValue()}.${random63BitValue()}"
        assertTempQueueCreationAttackFails(invalidRPCQueue)
    }

    @Test
    fun `send message to notifications address`() {
        assertSendAttackFails(NOTIFICATIONS_ADDRESS)
    }

    @Test
    fun `create random internal queue`() {
        val randomQueue = "$INTERNAL_PREFIX${random63BitValue()}"
        assertAllQueueCreationAttacksFail(randomQueue)
    }

    @Test
    fun `create random queue`() {
        val randomQueue = random63BitValue().toString()
        assertAllQueueCreationAttacksFail(randomQueue)
    }

    fun clientTo(target: NetworkHostAndPort, sslConfiguration: SSLConfiguration? = configureTestSSL(CordaX500Name("MegaCorp", "London", "GB"))): SimpleMQClient {
        val client = SimpleMQClient(target, sslConfiguration)
        clients += client
        return client
    }

    private val rpcConnections = mutableListOf<CordaRPCConnection>()
    private fun loginToRPC(target: NetworkHostAndPort, rpcUser: User): CordaRPCOps {
        return CordaRPCClient(target).start(rpcUser.username, rpcUser.password).also { rpcConnections.add(it) }.proxy
    }

    fun loginToRPCAndGetClientQueue(): String {
        loginToRPC(alice.internals.configuration.rpcOptions.address, rpcUser)
        val clientQueueQuery = SimpleString("${RPCApi.RPC_CLIENT_QUEUE_NAME_PREFIX}.${rpcUser.username}.*")
        val client = clientTo(alice.internals.configuration.rpcOptions.address)
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
            attacker.session.createTemporaryQueue(queue, RoutingType.MULTICAST, queue)
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
            attacker.session.createQueue(queue, RoutingType.MULTICAST, queue, durable)
        }
        // Double-check
        assertThatExceptionOfType(ActiveMQNonExistentQueueException::class.java).isThrownBy {
            attacker.session.createConsumer(queue)
        }
    }

    fun assertSendAttackFails(address: String) {
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
        bob.registerInitiatedFlow(ReceiveFlow::class.java)
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