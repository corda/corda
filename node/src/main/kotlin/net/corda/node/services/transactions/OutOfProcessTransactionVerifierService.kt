/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node.services.transactions

import com.codahale.metrics.Gauge
import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.Timer
import net.corda.core.concurrent.CordaFuture
import net.corda.core.crypto.SecureHash
import net.corda.core.node.services.TransactionVerifierService
import net.corda.core.crypto.random63BitValue
import net.corda.core.internal.concurrent.OpenFuture
import net.corda.core.internal.concurrent.openFuture
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.utilities.contextLogger
import net.corda.nodeapi.VerifierApi
import org.apache.activemq.artemis.api.core.client.ClientConsumer
import java.util.concurrent.ConcurrentHashMap

class OutOfProcessTransactionVerifierService(
        private val metrics: MetricRegistry,
        private val sendRequest: (Long, LedgerTransaction) -> Unit
) : SingletonSerializeAsToken(), TransactionVerifierService {
    companion object {
        private val log = contextLogger()
    }

    private data class VerificationHandle(
            val transactionId: SecureHash,
            val resultFuture: OpenFuture<Unit>,
            val durationTimerContext: Timer.Context
    )

    private val verificationHandles = ConcurrentHashMap<Long, VerificationHandle>()

    // Metrics
    private fun metric(name: String) = "OutOfProcessTransactionVerifierService.$name"

    private val durationTimer = metrics.timer(metric("Verification.Duration"))
    private val successMeter = metrics.meter(metric("Verification.Success"))
    private val failureMeter = metrics.meter(metric("Verification.Failure"))

    class VerificationResultForUnknownTransaction(nonce: Long) :
            Exception("Verification result arrived for unknown transaction nonce $nonce")

    fun start(responseConsumer: ClientConsumer) {
        log.info("Starting out of process verification service")
        metrics.register(metric("VerificationsInFlight"), Gauge { verificationHandles.size })
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

    override fun verify(transaction: LedgerTransaction): CordaFuture<*> {
        log.info("Verifying ${transaction.id}")
        val future = openFuture<Unit>()
        val nonce = random63BitValue()
        verificationHandles[nonce] = VerificationHandle(transaction.id, future, durationTimer.time())
        sendRequest(nonce, transaction)
        return future
    }
}