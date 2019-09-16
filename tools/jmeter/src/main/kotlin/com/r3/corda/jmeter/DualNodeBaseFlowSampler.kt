package com.r3.corda.jmeter

import net.corda.client.rpc.CordaRPCClient
import net.corda.client.rpc.CordaRPCConnection
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.LazyPool
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.contextLogger
import org.apache.jmeter.config.Argument
import org.apache.jmeter.config.Arguments
import org.apache.jmeter.protocol.java.sampler.AbstractJavaSamplerClient
import org.apache.jmeter.protocol.java.sampler.JavaSamplerContext
import org.apache.jmeter.samplers.SampleResult
import org.slf4j.Logger
import java.util.*


inline fun Logger.trace(msg: () -> String) {
    if (isTraceEnabled) trace(msg())
}

inline fun Logger.debug(msg: () -> String) {
    if (isDebugEnabled) debug(msg())
}

/**
 * Handle connections to 2 nodes so that we can start flows on either.
 */
abstract class DualNodeBaseFlowSampler : AbstractJavaSamplerClient() {
    companion object {
        protected data class RPCParams(val address: NetworkHostAndPort, val user: String, val password: String)
        protected data class RPCClient(val rpcClient: CordaRPCClient, val rpcConnection: CordaRPCConnection, val ops: CordaRPCOps)

        val hostA = Argument("host_A", "localhost", "<meta>", "The remote network address (hostname or IP address) to connect to for RPC.")
        val portA = Argument("port_A", "10000", "<meta>", "The remote port to connect to for RPC.")
        val hostB = Argument("host_B", "localhost", "<meta>", "The remote network address (hostname or IP address) to connect to for RPC.")
        val portB = Argument("port_B", "10001", "<meta>", "The remote port to connect to for RPC.")
        val usernameA = Argument("username_A", "corda", "<meta>", "The RPC user to connect to connect as (Node A).")
        val passwordA = Argument("password_A", "corda_is_awesome", "<meta>", "The password for the RPC user (Node A).")
        val usernameB = Argument("username_B", "corda", "<meta>", "The RPC user to connect to connect as (Node B).")
        val passwordB = Argument("password_B", "corda_is_awesome", "<meta>", "The password for the RPC user (Node B).")
        val notary = Argument("notaryName", "", "<meta>", "The X500 name of the notary.")
        val allArgs = setOf(hostA, portA, usernameA, passwordA, hostB, portB, usernameB, passwordB)
        val log = contextLogger()
        protected val rpcClientPools = Collections.synchronizedMap(mutableMapOf<RPCParams, LazyPool<RPCClient>>())
    }

    lateinit var notaryIdentity: Party

    private var rpcParamsA: RPCParams? = null
    private var rpcParamsB: RPCParams? = null

    private var rpcPoolA: LazyPool<RPCClient>? = null
    private var rpcPoolB: LazyPool<RPCClient>? = null

    protected open var flowResult: Any? = null

    protected fun getIdentity( rpc: CordaRPCOps, testContext: JavaSamplerContext, arg: Argument): Party {
        if (!testContext.containsParameter(arg.name)) {
            throw IllegalStateException("You must specify the '${arg.name}' property.")
        }
        val argName = CordaX500Name.parse(testContext.getParameter(arg.name))
        return rpc.wellKnownPartyFromX500Name(argName) ?: throw IllegalStateException("Don't know: '$argName'")
    }

    protected fun getNotaryIdentity(rpc: CordaRPCOps, testContext: JavaSamplerContext) {
        notaryIdentity = getIdentity(rpc,testContext, notary)
    }

    override fun getDefaultParameters(): Arguments {
        // Add copies of all args, since they seem to be mutable.
        return Arguments().apply {
            for (arg in allArgs) {
                addArgument(arg.clone() as Argument)
            }
            for (arg in additionalArgs) {
                addArgument(arg.clone() as Argument)
            }
        }
    }

    override fun setupTest(context: JavaSamplerContext) {
        super.setupTest(context)

        rpcParamsA = RPCParams(NetworkHostAndPort(context.getParameter(hostA.name), context.getIntParameter(portA.name)), context.getParameter(usernameA.name), context.getParameter(passwordA.name))
        rpcParamsB = RPCParams(NetworkHostAndPort(context.getParameter(hostB.name), context.getIntParameter(portB.name)), context.getParameter(usernameB.name), context.getParameter(passwordB.name))

        rpcPoolA = rpcClientPools.computeIfAbsent(rpcParamsA) {
            val rpcClient = CordaRPCClient(it.address)
            LazyPool {
                val rpcConnection = rpcClient.start(it.user, it.password)
                val rpcProxy = rpcConnection.proxy
                RPCClient(rpcClient, rpcConnection, rpcProxy)
            }
        }

        rpcPoolB = rpcClientPools.computeIfAbsent(rpcParamsB) {
            val rpcClient = CordaRPCClient(it.address)
            LazyPool {
                val rpcConnection = rpcClient.start(it.user, it.password)
                val rpcProxy = rpcConnection.proxy
                RPCClient(rpcClient, rpcConnection, rpcProxy)
            }
        }

        log.info("Set up test with rpcParamsA = $rpcParamsA, rpcPoolA = $rpcPoolA")
        rpcPoolA?.run {
            setupTest(it.ops, context)
        }

        log.info("Set up test with rpcParamsB = $rpcParamsB, rpcPoolB = $rpcPoolB")
        rpcPoolB?.run {
            setupTest(it.ops, context)
        }
    }

    protected open fun additionalFlowResponseProcessing(context: JavaSamplerContext, sample: SampleResult, response: Any?) {
        // Override this if you want to contribute things from the flow result to the sample.
    }

    abstract fun determineNextOpTarget(): TransferNode
    abstract fun getTestLabel(): String

    override fun runTest(context: JavaSamplerContext): SampleResult {
        val node = determineNextOpTarget()
        val testLabel = getTestLabel()
        val rpcPool = if (node == TransferNode.NODE_A) { rpcPoolA } else { rpcPoolB }
        return rpcPool!!.run {
            val flowInvoke = createFlowInvoke(it.ops, context)
            val result = SampleResult()
            result.sampleStart()
            val handle = it.ops.startFlowDynamic(flowInvoke.flowLogicClass, *(flowInvoke.args))
            result.sampleLabel = testLabel

            result.latencyEnd()
            try {
                flowResult = handle.returnValue.get()
                result.sampleEnd()
                result.apply {
                    isSuccessful = true
                    additionalFlowResponseProcessing(context, this, flowResult)
                }
            } catch (e: Exception) {
                flowResult = null
                result.sampleEnd()
                e.printStackTrace()
                result.apply {
                    isSuccessful = false
                    additionalFlowResponseProcessing(context, this, e)
                }
            }
        }
    }

    override fun teardownTest(context: JavaSamplerContext) {
        log.info("Tear down test with rpcParamsA = $rpcParamsA, rpcPoolA = $rpcPoolA")
        for(rpcClient in rpcPoolA?.close() ?: emptyList()) {
            teardownTest(rpcClient.ops, context)
            rpcClient.rpcConnection.close()
        }
        rpcClientPools.remove(rpcParamsA)
        rpcPoolA = null
        rpcParamsA = null

        log.info("Tear down test with rpcParamsB = $rpcParamsB, rpcPoolB = $rpcPoolB")
        for(rpcClient in rpcPoolB?.close() ?: emptyList()) {
            rpcClient.rpcConnection.close()
        }
        rpcClientPools.remove(rpcParamsB)
        rpcPoolB = null
        rpcParamsB = null
        super.teardownTest(context)
    }

    abstract val additionalArgs: Set<Argument>
    abstract fun setupTest(rpcProxy: CordaRPCOps, testContext: JavaSamplerContext)
    abstract fun createFlowInvoke(rpcProxy: CordaRPCOps, testContext: JavaSamplerContext): BaseFlowSampler.FlowInvoke<*>
    abstract fun teardownTest(rpcProxy: CordaRPCOps, testContext: JavaSamplerContext)
}