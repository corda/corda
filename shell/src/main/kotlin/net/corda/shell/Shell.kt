package net.corda.shell

import net.corda.core.messaging.CordaRPCOps
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.getOrThrow
import net.corda.nodeapi.internal.config.SSLConfiguration
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

data class SSHDConfiguration(val port: Int)

data class ShellConfiguration(
        val baseDirectory: Path,
        val hostAndPort: NetworkHostAndPort,
        val ssl: SSLConfiguration?,
        val sshd: SSHDConfiguration?,
        val noLocalShell: Boolean)


fun makeRPCOps(getCordaRPCOps: (username: String?, credential: String?) -> CordaRPCOps, username: String?, credential: String?) : CordaRPCOps {
    val cordaRPCOps: CordaRPCOps by lazy {
        val x = getCordaRPCOps(username, credential)
        x
    }

    return Proxy.newProxyInstance(CordaRPCOps::class.java.classLoader, arrayOf(CordaRPCOps::class.java), { _, method, args ->
        XRPCContextRunner {
            try {
                method.invoke(cordaRPCOps, *(args ?: arrayOf()))
            } catch (e: InvocationTargetException) {
                // Unpack exception.
                throw e.targetException
            }
        }.get().getOrThrow()
    }) as CordaRPCOps
}


private class XRPCContextRunner<T>(val block:() -> T): Thread() {

    private var result: CompletableFuture<T> = CompletableFuture()

    override fun run() {
        //CURRENT_RPC_CONTEXT.set(RpcAuthContext(invocationContext, authorizingSubject))
        try {
            result.complete(block())
        } catch (e: Throwable) {
            result.completeExceptionally(e)
        } finally {
            //CURRENT_RPC_CONTEXT.remove()
        }
    }

    fun get(): Future<T> {
        start()
        join()
        return result
    }
}