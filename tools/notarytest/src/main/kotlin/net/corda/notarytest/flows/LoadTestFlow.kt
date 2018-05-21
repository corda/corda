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
import net.corda.core.contracts.StateRef
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.entropyToKeyPair
import net.corda.core.crypto.generateKeyPair
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.NotarisationRequest
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.notary.TrustedAuthorityNotaryService
import net.corda.core.internal.notary.generateSignature
import java.math.BigInteger
import java.util.*
import java.util.concurrent.TimeUnit

@StartableByRPC
open class LoadTestFlow<T : TrustedAuthorityNotaryService>(
        private val serviceType: Class<T>,
        private val transactionCount: Int,
        /**
         * Number of input states per transaction.
         * If *null*, variable sized transactions will be created with median size 4.
         */
        private val inputStateCount: Int?
) : FlowLogic<Long>() {
    private val keyPairGenerator = Generator.long().map { entropyToKeyPair(BigInteger.valueOf(it)) }
    private val publicKeyGenerator = keyPairGenerator.map { it.public }

    private val publicKeyGenerator2 = Generator.pure(generateKeyPair().public)
    private val partyGenerator: Generator<Party> = Generator.int().combine(publicKeyGenerator2) { n, key ->
        Party(CordaX500Name(organisation = "Party$n", locality = "London", country = "GB"), key)
    }
    private val txIdGenerator = Generator.bytes(32).map { SecureHash.sha256(it) }
    private val stateRefGenerator = Generator.intRange(0, 10).map { StateRef(SecureHash.randomSHA256(), it) }

    @Suspendable
    override fun call(): Long {
        val stopwatch = Stopwatch.createStarted()
        val random = SplittableRandom()

        for (i in 1..transactionCount) {
            val txId: SecureHash = txIdGenerator.generateOrFail(random)
            val callerParty = partyGenerator.generateOrFail(random)
            val inputGenerator = if (inputStateCount == null) {
                Generator.replicatePoisson(4.0, stateRefGenerator, true)
            } else {
                Generator.replicate(inputStateCount, stateRefGenerator)
            }
            val inputs = inputGenerator.generateOrFail(random)
            val localStopwatch = Stopwatch.createStarted()
            val sig = NotarisationRequest(inputs, txId).generateSignature(serviceHub)
            serviceHub.cordaService(serviceType).commitInputStates(inputs, txId, callerParty, sig, null)
            logger.info("Committed a transaction ${txId.toString().take(10)} with ${inputs.size} inputs in ${localStopwatch.stop().elapsed(TimeUnit.MILLISECONDS)} ms")
        }

        stopwatch.stop()
        val duration = stopwatch.elapsed(TimeUnit.MILLISECONDS)
        logger.info("Committed $transactionCount transactions in $duration, avg ${duration.toDouble() / transactionCount} ms")

        return duration
    }
}