package net.corda.node.services.transactions

import com.codahale.metrics.Gauge
import com.codahale.metrics.Timer
import net.corda.core.concurrent.CordaFuture
import net.corda.core.crypto.SecureHash
import net.corda.core.node.services.TransactionVerifierService
import net.corda.core.crypto.random63BitValue
import net.corda.core.internal.concurrent.OpenFuture
import net.corda.core.internal.concurrent.openFuture
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.transactions.FullTransaction
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.utilities.loggerFor
import net.corda.node.services.api.MonitoringService
import net.corda.nodeapi.VerifierApi
import org.apache.activemq.artemis.api.core.client.ClientConsumer
import java.util.concurrent.ConcurrentHashMap

abstract class OutOfProcessTransactionVerifierService(
        val monitoringService: MonitoringService
) : SingletonSerializeAsToken(), TransactionVerifierService {
    companion object {
        val log = loggerFor<OutOfProcessTransactionVerifierService>()
    }

    private data class VerificationHandle(
            val transactionId: SecureHash,
            val resultFuture: OpenFuture<Unit>,
            val durationTimerContext: Timer.Context
    )

    private val verificationHandles = ConcurrentHashMap<Long, VerificationHandle>()

    // Metrics
    private fun metric(name: String) = "OutOfProcessTransactionVerifierService.$name"

    private val durationTimer = monitoringService.metrics.timer(metric("Verification.Duration"))
    private val successMeter = monitoringService.metrics.meter(metric("Verification.Success"))
    private val failureMeter = monitoringService.metrics.meter(metric("Verification.Failure"))

    class VerificationResultForUnknownTransaction(nonce: Long) :
            Exception("Verification result arrived for unknown transaction nonce $nonce")

    fun start(responseConsumer: ClientConsumer) {
        log.info("Starting out of process verification service")
        monitoringService.metrics.register(metric("VerificationsInFlight"), Gauge { verificationHandles.size })
        responseConsumer.setMessageHandler { message ->
            val response = VerifierApi.VerificationResponse.fromClientMessage(message)
            val handle = verificationHandles.remove(response.verificationId) ?:
                    throw VerificationResultForUnknownTransaction(response.verificationId)
            handle.durationTimerContext.stop()
            val exception = response.exception
            if (exception == null) {
                successMeter.mark()
                handle.resultFuture.set(Unit)
            } else {
                failureMeter.mark()
                handle.resultFuture.setException(exception)
            }
        }
    }

    abstract fun sendRequest(nonce: Long, transaction: LedgerTransaction)

    override fun verify(transaction: LedgerTransaction): CordaFuture<*> {
        log.info("Verifying ${transaction.id}")
        val future = openFuture<Unit>()
        val nonce = random63BitValue()
        verificationHandles[nonce] = VerificationHandle(transaction.id, future, durationTimer.time())
        sendRequest(nonce, transaction)
        return future
    }
}