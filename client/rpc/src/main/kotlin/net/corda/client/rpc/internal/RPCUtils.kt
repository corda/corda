package net.corda.client.rpc.internal

import net.corda.nodeapi.RPCApi
import java.lang.reflect.Method

object RPCUtils {
    fun isShutdownMethodName(methodName: String) =
            methodName.equals("shutdown", true) ||
                    methodName.equals("gracefulShutdown", true) ||
                    methodName.equals("terminate", true)

    fun RPCApi.ClientToServer.RpcRequest.isShutdownCmd() = isShutdownMethodName(methodName)
    fun Method.isShutdown() = isShutdownMethodName(name)
    fun Method.isStartFlow() = name.startsWith("startFlow") || name.startsWith("startTrackedFlow")
    fun Method.isStartFlowWithClientId() = name == "startFlowWithClientId" || name == "startFlowDynamicWithClientId"
}