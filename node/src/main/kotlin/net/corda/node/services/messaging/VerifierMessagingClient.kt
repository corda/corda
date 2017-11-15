package net.corda.node.services.messaging

import com.codahale.metrics.MetricRegistry
import net.corda.core.crypto.random63BitValue
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.serialization.internal.nodeSerializationEnv
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.loggerFor
import net.corda.node.services.transactions.OutOfProcessTransactionVerifierService
import net.corda.node.utilities.*
import net.corda.nodeapi.ArtemisMessagingComponent.Companion.NODE_USER
import net.corda.nodeapi.ArtemisTcpTransport
import net.corda.nodeapi.ConnectionDirection
import net.corda.nodeapi.VerifierApi
import net.corda.nodeapi.VerifierApi.VERIFICATION_REQUESTS_QUEUE_NAME
import net.corda.nodeapi.VerifierApi.VERIFICATION_RESPONSES_QUEUE_NAME_PREFIX
import net.corda.nodeapi.config.SSLConfiguration
import org.apache.activemq.artemis.api.core.RoutingType
import org.apache.activemq.artemis.api.core.SimpleString
import org.apache.activemq.artemis.api.core.client.*
import org.apache.activemq.artemis.api.core.client.ActiveMQClient.DEFAULT_ACK_BATCH_SIZE
import java.util.concurrent.*

class VerifierMessagingClient(private val config: SSLConfiguration,
                              private val serverAddress: NetworkHostAndPort,
                              metrics: MetricRegistry
) : SingletonSerializeAsToken() {
    companion object {
        private val log = loggerFor<VerifierMessagingClient>()
        private val verifierResponseAddress = "$VERIFICATION_RESPONSES_QUEUE_NAME_PREFIX.${random63BitValue()}"
    }

    private class Started(val sessionFactory: ClientSessionFactory,
                          val session: ClientSession,
                          val producer: ClientProducer,
                          val verificationResponseConsumer: ClientConsumer) {
        fun dispose() {
            producer.close()
            // Ensure any trailing messages are committed to the journal
            session.commit()
            // Closing the factory closes all the sessions it produced as well.
            sessionFactory.close()
        }
    }

    /** An executor for sending messages */
    private val messagingExecutor = AffinityExecutor.ServiceAffinityExecutor("Messaging", 1)
    private var started: Started? = null
    fun start() = synchronized(this) {
        check(started == null) { "start can't be called twice" }
        log.info("Connecting to message broker: $serverAddress")
        // TODO Add broker CN to config for host verification in case the embedded broker isn't used
        val tcpTransport = ArtemisTcpTransport.tcpTransport(ConnectionDirection.Outbound(), serverAddress, config)
        val locator = ActiveMQClient.createServerLocatorWithoutHA(tcpTransport).apply {
            // Never time out on our loopback Artemis connections. If we switch back to using the InVM transport this
            // would be the default and the two lines below can be deleted.
            connectionTTL = -1
            clientFailureCheckPeriod = -1
            minLargeMessageSize = ArtemisMessagingServer.MAX_FILE_SIZE
            isUseGlobalPools = nodeSerializationEnv != null
        }
        val sessionFactory = locator.createSessionFactory()
        // Login using the node username. The broker will authentiate us as its node (as opposed to another peer)
        // using our TLS certificate.
        // Note that the acknowledgement of messages is not flushed to the Artermis journal until the default buffer
        // size of 1MB is acknowledged.
        val session = sessionFactory!!.createSession(NODE_USER, NODE_USER, false, true, true, locator.isPreAcknowledge, DEFAULT_ACK_BATCH_SIZE)
        session.start()
        // Create a general purpose producer.
        val producer = session.createProducer()
        fun checkVerifierCount() {
            if (session.queueQuery(SimpleString(VERIFICATION_REQUESTS_QUEUE_NAME)).consumerCount == 0) {
                log.warn("No connected verifier listening on $VERIFICATION_REQUESTS_QUEUE_NAME!")
            }
        }

        // Attempts to create a durable queue on the broker which is bound to an address of the same name.
        fun createQueueIfAbsent(queueName: String) {
            val queueQuery = session.queueQuery(SimpleString(queueName))
            if (!queueQuery.isExists) {
                log.info("Create fresh queue $queueName bound on same address")
                session.createQueue(queueName, RoutingType.MULTICAST, queueName, true)
            }
        }
        createQueueIfAbsent(VERIFICATION_REQUESTS_QUEUE_NAME)
        createQueueIfAbsent(verifierResponseAddress)
        val verificationResponseConsumer = session.createConsumer(verifierResponseAddress)
        messagingExecutor.scheduleAtFixedRate(::checkVerifierCount, 0, 10, TimeUnit.SECONDS)
        started = Started(sessionFactory, session, producer, verificationResponseConsumer)
    }

    fun start2() = synchronized(this) {
        verifierService.start(started!!.verificationResponseConsumer)
    }

    fun stop() = synchronized(this) {
        started!!.dispose()
        started = null
    }

    internal val verifierService = OutOfProcessTransactionVerifierService(metrics) { nonce, transaction ->
        messagingExecutor.fetchFrom {
            sendRequest(nonce, transaction)
        }
    }

    private fun sendRequest(nonce: Long, transaction: LedgerTransaction) = synchronized(this) {
        val started = started!!
        val message = started.session.createMessage(false)
        val request = VerifierApi.VerificationRequest(nonce, transaction, SimpleString(verifierResponseAddress))
        request.writeToClientMessage(message)
        started.producer.send(VERIFICATION_REQUESTS_QUEUE_NAME, message)
    }
}
