/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package com.r3.corda.jmeter

import com.r3.corda.enterprise.perftestcordapp.POUNDS
import com.r3.corda.enterprise.perftestcordapp.flows.CashIssueAndDoublePayment
import com.r3.corda.enterprise.perftestcordapp.flows.CashIssueAndDuplicatePayment
import net.corda.core.identity.Party
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.utilities.OpaqueBytes
import org.apache.jmeter.config.Argument
import org.apache.jmeter.protocol.java.sampler.JavaSamplerContext


/**
 * A sampler to just issue cash and pay another party, but the flow attempts to spend the cash twice (double spend) and
 * only succeeds if that is correctly rejected for the second spend.
 *
 * Use with 1 iteration and 1 thread in JMeter as a functional test.
 */
class NotariseDoubleSpendSampler : AbstractSampler() {
    companion object JMeterProperties {
        val otherParty = Argument("otherPartyName", "", "<meta>", "The X500 name of the payee.")
    }

    lateinit var counterParty: Party

    override fun setupTest(rpcProxy: CordaRPCOps, testContext: JavaSamplerContext) {
        getNotaryIdentity(rpcProxy, testContext)
        counterParty = getIdentity(rpcProxy, testContext, otherParty)
    }

    override fun createFlowInvoke(rpcProxy: CordaRPCOps, testContext: JavaSamplerContext): FlowInvoke<*> {
        val amount = 2_000_000.POUNDS
        return FlowInvoke<CashIssueAndDoublePayment>(CashIssueAndDoublePayment::class.java, arrayOf(amount, OpaqueBytes.of(1), counterParty, false, notaryIdentity))
    }

    override fun teardownTest(rpcProxy: CordaRPCOps, testContext: JavaSamplerContext) {
    }

    override val additionalArgs: Set<Argument>
        get() = setOf(notary, otherParty)
}


/**
 * A sampler to just issue cash and pay another party.  The flow actually submits the same transaction twice to the notary
 * which should succeed.
 *
 * Use with 1 iteration and 1 thread in JMeter as a functional test.
 */
class NotariseDuplicateTransactionSampler : AbstractSampler() {
    companion object JMeterProperties {
        val otherParty = Argument("otherPartyName", "", "<meta>", "The X500 name of the payee.")
    }

    lateinit var counterParty: Party

    override fun setupTest(rpcProxy: CordaRPCOps, testContext: JavaSamplerContext) {
        getNotaryIdentity(rpcProxy, testContext)
        counterParty = getIdentity(rpcProxy, testContext, otherParty)
    }

    override fun createFlowInvoke(rpcProxy: CordaRPCOps, testContext: JavaSamplerContext): FlowInvoke<*> {
        val amount = 2_000_000.POUNDS
        return FlowInvoke<CashIssueAndDuplicatePayment>(CashIssueAndDuplicatePayment::class.java, arrayOf(amount, OpaqueBytes.of(1), counterParty, false, notaryIdentity))
    }

    override fun teardownTest(rpcProxy: CordaRPCOps, testContext: JavaSamplerContext) {
    }

    override val additionalArgs: Set<Argument>
        get() = setOf(notary, otherParty)
}