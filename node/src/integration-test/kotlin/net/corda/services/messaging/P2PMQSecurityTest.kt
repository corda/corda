package net.corda.services.messaging

import net.corda.core.crypto.generateKeyPair
import net.corda.core.crypto.toStringShort
import net.corda.nodeapi.RPCApi
import net.corda.nodeapi.internal.ArtemisMessagingComponent
import net.corda.nodeapi.internal.ArtemisMessagingComponent.Companion.P2P_PREFIX
import net.corda.nodeapi.internal.ArtemisMessagingComponent.Companion.PEERS_PREFIX
import net.corda.services.messaging.SimpleAMQPClient.Companion.sendAndVerify
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.singleIdentity
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.Test
import javax.jms.JMSException

/**
 * Runs a series of MQ-related attacks against a node. Subclasses need to call [startAttacker] to connect
 * the attacker to [alice].
 */
abstract class P2PMQSecurityTest : MQSecurityTest() {
    override fun assertSendAttackFails(address: String) {
        val attacker = amqpClientTo(alice.node.configuration.p2pAddress)
        val session = attacker.start(ArtemisMessagingComponent.PEER_USER, ArtemisMessagingComponent.PEER_USER)
        val message = session.createMessage()
        message.setStringProperty("_AMQ_VALIDATED_USER", "O=MegaCorp, L=London, C=GB")
        val queue = session.createQueue(address)
        assertThatExceptionOfType(JMSException::class.java).isThrownBy {
            session.createProducer(queue).sendAndVerify(message)
        }.withMessageContaining(address).withMessageContaining("SEND")
    }

    fun assertProducerQueueCreationAttackFails(address: String) {
        val attacker = amqpClientTo(alice.node.configuration.p2pAddress)
        val session = attacker.start(ArtemisMessagingComponent.PEER_USER, ArtemisMessagingComponent.PEER_USER)
        val message = session.createMessage()
        message.setStringProperty("_AMQ_VALIDATED_USER", "O=MegaCorp, L=London, C=GB")
        val queue = session.createQueue(address)
        assertThatExceptionOfType(JMSException::class.java).isThrownBy {
            session.createProducer(queue)
        }.withMessageContaining(address).withMessageContaining("CREATE_DURABLE_QUEUE")
    }

    @Test(timeout=300_000)
	fun `consume message from P2P queue`() {
        assertConsumeAttackFails("$P2P_PREFIX${alice.info.singleIdentity().owningKey.toStringShort()}")
    }

    @Test(timeout=300_000)
	fun `consume message from peer queue`() {
        val bobParty = startBobAndCommunicateWithAlice()
        assertConsumeAttackFails("$PEERS_PREFIX${bobParty.owningKey.toStringShort()}")
    }

    @Test(timeout=300_000)
	fun `send message to address of peer which has been communicated with`() {
        val bobParty = startBobAndCommunicateWithAlice()
        assertSendAttackFails("$PEERS_PREFIX${bobParty.owningKey.toStringShort()}")
    }

    @Test(timeout=300_000)
	fun `create queue for peer which has not been communicated with`() {
        val bob = startNode(BOB_NAME)
        assertAllQueueCreationAttacksFail("$PEERS_PREFIX${bob.info.singleIdentity().owningKey.toStringShort()}")
    }

    @Test(timeout=300_000)
	fun `create queue for unknown peer`() {
        val invalidPeerQueue = "$PEERS_PREFIX${generateKeyPair().public.toStringShort()}"
        assertAllQueueCreationAttacksFail(invalidPeerQueue)
    }

    @Test(timeout=300_000)
	fun `consume message from RPC requests queue`() {
        assertConsumeAttackFailsNonexistent(RPCApi.RPC_SERVER_QUEUE_NAME)
    }

    @Test(timeout=300_000)
	fun `consume message from logged in user's RPC queue`() {
        val user1Queue = loginToRPCAndGetClientQueue()
        assertConsumeAttackFailsNonexistent(user1Queue)
    }
}