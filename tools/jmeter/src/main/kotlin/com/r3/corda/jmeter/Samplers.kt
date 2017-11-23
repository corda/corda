package com.r3.corda.jmeter

import com.r3.corda.enterprise.perftestcordapp.DOLLARS
import com.r3.corda.enterprise.perftestcordapp.POUNDS
import com.r3.corda.enterprise.perftestcordapp.flows.CashIssueAndPaymentFlow
import com.r3.corda.enterprise.perftestcordapp.flows.CashIssueFlow
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.utilities.OpaqueBytes
import org.apache.jmeter.config.Argument
import org.apache.jmeter.protocol.java.sampler.JavaSamplerContext

/**
 * A base sampler that looks up identities via RPC ready for starting flows, to be extended and specialised as required.
 */
abstract class AbstractSampler : BaseFlowSampler() {
    lateinit var notaryIdentity: Party

    companion object JMeterProperties {
        val notary = Argument("notaryName", "", "<meta>", "The X500 name of the notary.")
    }

    protected fun getIdentity( rpc: CordaRPCOps, testContext: JavaSamplerContext, arg: Argument):Party{
        if (!testContext.containsParameter(arg.name)) {
            throw IllegalStateException("You must specify the '${arg.name}' property.")
        }
        val argName = CordaX500Name.parse(testContext.getParameter(arg.name))
        return rpc.wellKnownPartyFromX500Name(argName) ?: throw IllegalStateException("Don't know $argName")
    }

    protected fun getNotaryIdentity(rpc: CordaRPCOps, testContext: JavaSamplerContext) {
        notaryIdentity = getIdentity(rpc,testContext, notary)
    }
}

/**
 * A sampler for calling CashIssueFlow.
 *
 * TODO: add more configurable parameters (reference, amount etc) if there is a requirement.
 */
class CashIssueSampler : AbstractSampler() {
    override val additionalArgs: Set<Argument> = setOf(notary)

    override fun setupTest(rpcProxy: CordaRPCOps, testContext: JavaSamplerContext) {
        getNotaryIdentity(rpcProxy, testContext)
    }

    override fun teardownTest(rpcProxy: CordaRPCOps, testContext: JavaSamplerContext) {
    }

    override fun createFlowInvoke(rpcProxy: CordaRPCOps, testContext: JavaSamplerContext): FlowInvoke<CashIssueFlow> {
        val amount = 1_100_000_000_000.DOLLARS
        return FlowInvoke<CashIssueFlow>(CashIssueFlow::class.java, arrayOf(amount, OpaqueBytes.of(1), notaryIdentity))
    }
}

/**
 * A sampler that issues cash and pays it to a specified party, thus invoking the notary and the payee
 * via P2P
 */
class CashIssueAndPaySampler : AbstractSampler() {
    companion object JMeterProperties{
        val otherParty = Argument("otherPartyName", "", "<meta>", "The X500 name of the payee.")
    }

    lateinit var counterParty: Party

    override fun setupTest(rpcProxy: CordaRPCOps, testContext: JavaSamplerContext) {
        getNotaryIdentity(rpcProxy,testContext)
        counterParty = getIdentity(rpcProxy, testContext, otherParty)
    }


    override fun createFlowInvoke(rpcProxy: CordaRPCOps, testContext: JavaSamplerContext): FlowInvoke<*> {
        val amount = 2_000_000.POUNDS
        return FlowInvoke<CashIssueAndPaymentFlow>(CashIssueAndPaymentFlow::class.java, arrayOf(amount, OpaqueBytes.of(1), notaryIdentity, counterParty))
    }

    override fun teardownTest(rpcProxy: CordaRPCOps, testContext: JavaSamplerContext) {
    }

    override val additionalArgs: Set<Argument>
        get() = setOf(notary, otherParty)
}