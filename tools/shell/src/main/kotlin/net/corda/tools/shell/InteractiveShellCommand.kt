package net.corda.tools.shell

import org.crsh.command.BaseCommand
import org.crsh.shell.impl.command.CRaSHSession

/**
 * Simply extends CRaSH BaseCommand to add easy access to the RPC ops class.
 */
open class InteractiveShellCommand : BaseCommand() {
    fun ops() = ((context.session as CRaSHSession).authInfo as CordaSSHAuthInfo).rpcOps
    fun ansiProgressRenderer() = ((context.session as CRaSHSession).authInfo as CordaSSHAuthInfo).ansiProgressRenderer
    fun objectMapper() = ((context.session as CRaSHSession).authInfo as CordaSSHAuthInfo).yamlInputMapper
    fun isSsh() = ((context.session as CRaSHSession).authInfo as CordaSSHAuthInfo).isSsh
}
