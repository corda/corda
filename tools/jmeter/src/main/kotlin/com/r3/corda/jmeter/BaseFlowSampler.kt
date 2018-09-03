package com.r3.corda.jmeter

import net.corda.client.rpc.CordaRPCClient
import net.corda.client.rpc.CordaRPCConnection
import net.corda.core.flows.FlowLogic
import net.corda.core.internal.LazyPool
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.contextLogger
import org.apache.jmeter.config.Argument
import org.apache.jmeter.config.Arguments
import org.apache.jmeter.protocol.java.sampler.AbstractJavaSamplerClient
import org.apache.jmeter.protocol.java.sampler.JavaSamplerContext
import org.apache.jmeter.samplers.SampleResult
import java.util.*

/**
 * Do most of the work for firing flow start requests via RPC at a Corda node.
 */
abstract class BaseFlowSampler : AbstractJavaSamplerClient() {
    companion object {
        private data class RPCParams(val address: NetworkHostAndPort, val user: String, val password: String)
        private data class RPCClient(val rpcClient: CordaRPCClient, val rpcConnection: CordaRPCConnection, val ops: CordaRPCOps)

        val label = Argument("label", "\${__samplerName}", "<meta>", "The value in the label column in the resulting CSV file to dissambiguate this test run from others.")
        val host = Argument("host", "localhost", "<meta>", "The remote network address (hostname or IP address) to connect to for RPC.")
        val port = Argument("port", "10000", "<meta>", "The remote port to connect to for RPC.")
        val username = Argument("username", "corda", "<meta>", "The RPC user to connect to connect as.")
        val password = Argument("password", "corda_is_awesome", "<meta>", "The password for the RPC user.")

        val allArgs = setOf(label, host, port, username, password)

        val log = contextLogger()

        private val rpcClientPools = Collections.synchronizedMap(mutableMapOf<RPCParams, LazyPool<RPCClient>>())
    }

    private var rpcParams: RPCParams? = null
    private var rpcPool: LazyPool<RPCClient>? = null

    private var labelValue: String? = null

    protected open var flowResult: Any? = null

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
        rpcParams = RPCParams(NetworkHostAndPort(context.getParameter(host.name), context.getIntParameter(port.name)), context.getParameter(username.name), context.getParameter(password.name))
        labelValue = context.getParameter(label.name)
        if (labelValue.isNullOrBlank()) {
            labelValue = null
        }
        rpcPool = rpcClientPools.computeIfAbsent(rpcParams) {
            LazyPool {
                val rpcClient = CordaRPCClient(it.address)
                val rpcConnection = rpcClient.start(it.user, it.password)
                val rpcProxy = rpcConnection.proxy
                RPCClient(rpcClient, rpcConnection, rpcProxy)
            }
        }
        log.info("Set up test with rpcParams = $rpcParams, rpcPool = $rpcPool")
        rpcPool?.run {
            setupTest(it.ops, context)
        }
    }

    protected open fun additionalFlowResponseProcessing(context: JavaSamplerContext, sample: SampleResult, response: Any?) {
        // Override this if you want to contribute things from the flow result to the sample.
    }

    override fun runTest(context: JavaSamplerContext): SampleResult {
        return rpcPool!!.run {
            val flowInvoke = createFlowInvoke(it.ops, context)
            val result = SampleResult()
            result.sampleStart()
            val handle = it.ops.startFlowDynamic(flowInvoke.flowLogicClass, *(flowInvoke.args))
            result.sampleLabel = labelValue ?: flowInvoke.flowLogicClass.simpleName
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
        log.info("Tear down test with rpcParams = $rpcParams, rpcPool = $rpcPool")
        for(rpcClient in rpcPool?.close() ?: emptyList()) {
            teardownTest(rpcClient.ops, context)
            rpcClient.rpcConnection.close()
        }
        rpcClientPools.remove(rpcParams)
        rpcPool = null
        rpcParams = null
        labelValue = null
        super.teardownTest(context)
    }

    abstract val additionalArgs: Set<Argument>
    abstract fun setupTest(rpcProxy: CordaRPCOps, testContext: JavaSamplerContext)
    abstract fun createFlowInvoke(rpcProxy: CordaRPCOps, testContext: JavaSamplerContext): FlowInvoke<*>
    abstract fun teardownTest(rpcProxy: CordaRPCOps, testContext: JavaSamplerContext)

    class FlowInvoke<T : FlowLogic<*>>(val flowLogicClass: Class<out T>, val args: Array<Any?>)
}