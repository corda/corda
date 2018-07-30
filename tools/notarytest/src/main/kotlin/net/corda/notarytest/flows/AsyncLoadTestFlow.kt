/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.notarytest.flows

import co.paralleluniverse.fibers.Suspendable
import com.google.common.base.Stopwatch
import net.corda.client.mock.Generator
import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.StateRef
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.entropyToKeyPair
import net.corda.core.crypto.generateKeyPair
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.NotarisationRequest
import net.corda.core.flows.NotarisationRequestSignature
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.concurrent.transpose
import net.corda.core.internal.notary.AsyncCFTNotaryService
import net.corda.core.internal.notary.AsyncUniquenessProvider
import net.corda.core.internal.notary.generateSignature
import java.math.BigInteger
import java.util.*
import java.util.concurrent.TimeUnit

@StartableByRPC
open class AsyncLoadTestFlow<T : AsyncCFTNotaryService>(
        private val serviceType: Class<T>,
        private val transactionCount: Int,
        private val batchSize: Int = 100,
        /**
         * Number of input states per transaction.
         * If *null*, variable sized transactions will be created with median size 4.
         */
        private val inputStateCount: Int? = null
) : FlowLogic<Long>() {
    private val keyPairGenerator = Generator.long().map { entropyToKeyPair(BigInteger.valueOf(it)) }
    private val publicKeyGeneratorSingle = Generator.pure(generateKeyPair().public)
    private val partyGenerator: Generator<Party> = Generator.int().combine(publicKeyGeneratorSingle) { n, key ->
        Party(CordaX500Name(organisation = "Party$n", locality = "London", country = "GB"), key)
    }
    private val txIdGenerator = Generator.bytes(32).map { SecureHash.sha256(it) }
    private val stateRefGenerator = txIdGenerator.combine(Generator.intRange(0, 10)) { id, pos -> StateRef(id, pos) }

    @Suspendable
    override fun call(): Long {
        var current = 0
        var totalDuration = 0L
        while (current < transactionCount) {
            val batch = Math.min(batchSize, transactionCount - current)
            totalDuration += runBatch(batch)
            current += batch
        }
        return totalDuration
    }

    private val random = SplittableRandom()

    private fun runBatch(transactionCount: Int): Long {
        val stopwatch = Stopwatch.createStarted()
        val futures = mutableListOf<CordaFuture<AsyncUniquenessProvider.Result>>()

        val service = serviceHub.cordaService(serviceType)

        for (i in 1..batchSize) {
            val txId: SecureHash = txIdGenerator.generateOrFail(random)
            val callerParty = partyGenerator.generateOrFail(random)
            val inputGenerator = if (inputStateCount == null) {
                Generator.replicatePoisson(4.0, stateRefGenerator, true)
            } else {
                Generator.replicate(inputStateCount, stateRefGenerator)
            }
            val inputs = inputGenerator.generateOrFail(random)
            val requestSignature = NotarisationRequest(inputs, txId).generateSignature(serviceHub)

            futures += AsyncCFTNotaryService.CommitOperation(service, inputs, txId, callerParty, requestSignature,
                    null, emptyList()).execute()
        }

        futures.transpose().get()

        stopwatch.stop()
        val duration = stopwatch.elapsed(TimeUnit.MILLISECONDS)
        logger.info("Committed $transactionCount transactions in $duration ms, avg ${duration.toDouble() / transactionCount} ms")

        return duration
    }
}