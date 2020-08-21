package net.corda.tools.shell

import net.corda.core.messaging.RPCOps
import org.crsh.command.BaseCommand
import org.crsh.shell.impl.command.CRaSHSession

/**
 * Simply extends CRaSH BaseCommand to add easy access to the RPC ops class.
 */
internal abstract class InteractiveShellCommand<T : RPCOps> : BaseCommand() {

    abstract val rpcOpsClass: Class<out T>

    @Suppress("UNCHECKED_CAST")
    fun ops(): T {
        val cRaSHSession = context.session as CRaSHSession
        val authInfo = cRaSHSession.authInfo as SshAuthInfo
        return authInfo.getOrCreateRpcOps(rpcOpsClass)
    }

    fun ansiProgressRenderer() = ((context.session as CRaSHSession).authInfo as CordaSSHAuthInfo).ansiProgressRenderer

    fun isSsh() = ((context.session as CRaSHSession).authInfo as CordaSSHAuthInfo).isSsh
}
