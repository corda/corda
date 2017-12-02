package net.corda.node.services.messaging

import com.codahale.metrics.MetricRegistry
import net.corda.core.crypto.random63BitValue
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.loggerFor
import net.corda.node.services.transactions.OutOfProcessTransactionVerifierService
import net.corda.node.utilities.*
import net.corda.nodeapi.VerifierApi
import net.corda.nodeapi.VerifierApi.VERIFICATION_REQUESTS_QUEUE_NAME
import net.corda.nodeapi.VerifierApi.VERIFICATION_RESPONSES_QUEUE_NAME_PREFIX
import net.corda.nodeapi.internal.config.SSLConfiguration
import org.apache.activemq.artemis.api.core.RoutingType
import org.apache.activemq.artemis.api.core.SimpleString
import org.apache.activemq.artemis.api.core.client.*
import java.util.concurrent.*

class VerifierMessagingClient(config: SSLConfiguration, serverAddress: NetworkHostAndPort, metrics: MetricRegistry) : SingletonSerializeAsToken() {
    companion object {
        private val log = loggerFor<VerifierMessagingClient>()
        private val verifierResponseAddress = "$VERIFICATION_RESPONSES_QUEUE_NAME_PREFIX.${random63BitValue()}"
    }

    private val artemis = ArtemisMessagingClient(config, serverAddress)
    /** An executor for sending messages */
    private val messagingExecutor = AffinityExecutor.ServiceAffinityExecutor("Messaging", 1)
    private var verificationResponseConsumer: ClientConsumer? = null
    fun start(): Unit = synchronized(this) {
        val session = artemis.start().session
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
        verificationResponseConsumer = session.createConsumer(verifierResponseAddress)
        messagingExecutor.scheduleAtFixedRate(::checkVerifierCount, 0, 10, TimeUnit.SECONDS)
    }

    fun start2() = synchronized(this) {
        verifierService.start(verificationResponseConsumer!!)
    }

    fun stop() = synchronized(this) {
        artemis.stop()
    }

    internal val verifierService = OutOfProcessTransactionVerifierService(metrics) { nonce, transaction ->
        messagingExecutor.fetchFrom {
            sendRequest(nonce, transaction)
        }
    }

    private fun sendRequest(nonce: Long, transaction: LedgerTransaction) = synchronized(this) {
        val started = artemis.started!!
        val message = started.session.createMessage(false)
        val request = VerifierApi.VerificationRequest(nonce, transaction, SimpleString(verifierResponseAddress))
        request.writeToClientMessage(message)
        started.producer.send(VERIFICATION_REQUESTS_QUEUE_NAME, message)
    }
}
