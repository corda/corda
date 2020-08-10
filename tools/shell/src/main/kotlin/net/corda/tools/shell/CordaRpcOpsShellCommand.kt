package net.corda.tools.shell

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.type.TypeFactory
import net.corda.core.internal.messaging.InternalCordaRPCOps

internal abstract class CordaRpcOpsShellCommand : InteractiveShellCommand<InternalCordaRPCOps>() {
    override val rpcOpsClass: Class<out InternalCordaRPCOps> = InternalCordaRPCOps::class.java

    fun objectMapper(classLoader: ClassLoader?): ObjectMapper {
        val om = createYamlInputMapper()
        if (classLoader != null) {
            om.typeFactory = TypeFactory.defaultInstance().withClassLoader(classLoader)
        }
        return om
    }

    private fun createYamlInputMapper(): ObjectMapper {
        val rpcOps = ops()
        return InteractiveShell.createYamlInputMapper(rpcOps)
    }
}