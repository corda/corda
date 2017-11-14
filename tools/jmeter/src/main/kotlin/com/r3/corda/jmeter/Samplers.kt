package com.r3.corda.jmeter

import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.utilities.OpaqueBytes
import net.corda.finance.DOLLARS
import net.corda.finance.flows.CashIssueFlow
import org.apache.jmeter.config.Argument
import org.apache.jmeter.protocol.java.sampler.JavaSamplerContext


abstract class AbstractSampler : FlowSampler() {
    lateinit var notaryIdentity: Party

    companion object JMeterProperties {
        val notary = Argument("notaryName", "", "<meta>", "The X500 name of the notary.")
    }

    protected fun getIdentities(rpc: CordaRPCOps, testContext: JavaSamplerContext) {
        if (!testContext.containsParameter(notary.name)) {
            throw IllegalStateException("You must specify the '${notary.name}' property.")
        }
        val notaryName = CordaX500Name.parse(testContext.getParameter(notary.name))
        notaryIdentity = rpc.wellKnownPartyFromX500Name(notaryName) ?: throw IllegalStateException("Don't know $notaryName")
    }
}

class CashIssueSampler : AbstractSampler() {
    override val additionalArgs: Set<Argument> = setOf(notary)

    override fun setupTest(rpcProxy: CordaRPCOps, testContext: JavaSamplerContext) {
        getIdentities(rpcProxy, testContext)
    }

    override fun teardownTest(rpcProxy: CordaRPCOps, testContext: JavaSamplerContext) {
    }

    override fun createFlowInvoke(rpcProxy: CordaRPCOps, testContext: JavaSamplerContext): FlowInvoke<CashIssueFlow> {
        val amount = 1_100_000_000_000.DOLLARS
        return FlowInvoke<CashIssueFlow>(CashIssueFlow::class.java, arrayOf(amount, OpaqueBytes.of(1), notaryIdentity))
    }

}