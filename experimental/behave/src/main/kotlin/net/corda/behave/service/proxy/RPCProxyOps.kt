package net.corda.behave.service.proxy

import com.google.common.net.HostAndPort
import net.corda.client.rpc.internal.KryoClientSerializationScheme
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.Party
import net.corda.core.internal.checkOkResponse
import net.corda.core.internal.openHttpConnection
import net.corda.core.internal.responseAs
import net.corda.core.node.NodeInfo
import net.corda.core.serialization.internal.effectiveSerializationEnv
import net.corda.core.serialization.serialize
import net.corda.core.utilities.NetworkHostAndPort

class RPCProxyOps(private val targetHostAndPort: NetworkHostAndPort) {

    init {
        try {
            effectiveSerializationEnv
        } catch (e: IllegalStateException) {
            try {
                KryoClientSerializationScheme.initialiseSerialization()
            } catch (e: IllegalStateException) {
                // Race e.g. two of these constructed in parallel, ignore.
            }
        }
    }

    fun <T> startFlowDynamic (logicType: Class<out FlowLogic<T>>, vararg args: Any?): Any? {
        return doPost(targetHostAndPort, "start-flow", logicType.name.serialize().bytes)
    }

    fun nodeInfo(): NodeInfo {
        return doGet(targetHostAndPort, "node-info")
    }

    fun registeredFlows(): List<String> {
        return doGet(targetHostAndPort, "registered-flows")
    }

    fun notaryIdentities(): List<Party> {
        return doGet(targetHostAndPort, "notary-identities")
    }

    private fun doPost(hostAndPort: NetworkHostAndPort, path: String, payload: ByteArray) {
        val url = java.net.URL("http://$hostAndPort/rpc/$path")
        url.openHttpConnection().apply {
            doOutput = true
            requestMethod = "POST"
            setRequestProperty("Content-Type", javax.ws.rs.core.MediaType.APPLICATION_OCTET_STREAM)
            outputStream.write(payload)
            checkOkResponse()
        }
    }

    private inline fun <reified T : Any> doGet(hostAndPort: NetworkHostAndPort, path: String): T {
        return java.net.URL("http://$hostAndPort/rpc/$path").openHttpConnection().responseAs()
    }
}

// Note that the passed in constructor function is only used for unification of other type parameters and reification of
// the Class instance of the flow. This could be changed to use the constructor function directly.

inline fun <T, reified R : FlowLogic<T>> RPCProxyOps.startFlow(
        @Suppress("UNUSED_PARAMETER")
        flowConstructor: () -> R
): Any? = startFlowDynamic(R::class.java)

inline fun <T, A, reified R : FlowLogic<T>> RPCProxyOps.startFlow(
        @Suppress("UNUSED_PARAMETER")
        flowConstructor: (A) -> R,
        arg0: A
): Any? = startFlowDynamic(R::class.java, arg0)

/**
 * Extension function for type safe invocation of flows from Kotlin, for example:
 *
 * val rpc: CordaRPCOps = (..)
 * rpc.startFlow(::ResolveTransactionsFlow, setOf<SecureHash>(), aliceIdentity)
 */
inline fun <T, A, B, reified R : FlowLogic<T>> RPCProxyOps.startFlow(
        @Suppress("UNUSED_PARAMETER")
        flowConstructor: (A, B) -> R,
        arg0: A,
        arg1: B
): Any? = startFlowDynamic(R::class.java, arg0, arg1)

inline fun <T, A, B, C, reified R : FlowLogic<T>> RPCProxyOps.startFlow(
        @Suppress("UNUSED_PARAMETER")
        flowConstructor: (A, B, C) -> R,
        arg0: A,
        arg1: B,
        arg2: C
): Any? = startFlowDynamic(R::class.java, arg0, arg1, arg2)

inline fun <T, A, B, C, D, reified R : FlowLogic<T>> RPCProxyOps.startFlow(
        @Suppress("UNUSED_PARAMETER")
        flowConstructor: (A, B, C, D) -> R,
        arg0: A,
        arg1: B,
        arg2: C,
        arg3: D
): Any? = startFlowDynamic(R::class.java, arg0, arg1, arg2, arg3)

inline fun <T, A, B, C, D, E, reified R : FlowLogic<T>> RPCProxyOps.startFlow(
        @Suppress("UNUSED_PARAMETER")
        flowConstructor: (A, B, C, D, E) -> R,
        arg0: A,
        arg1: B,
        arg2: C,
        arg3: D,
        arg4: E
): Any? = startFlowDynamic(R::class.java, arg0, arg1, arg2, arg3, arg4)

inline fun <T, A, B, C, D, E, F, reified R : FlowLogic<T>> RPCProxyOps.startFlow(
        @Suppress("UNUSED_PARAMETER")
        flowConstructor: (A, B, C, D, E, F) -> R,
        arg0: A,
        arg1: B,
        arg2: C,
        arg3: D,
        arg4: E,
        arg5: F
): Any? = startFlowDynamic(R::class.java, arg0, arg1, arg2, arg3, arg4, arg5)

