package net.corda.node.shell

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.core.messaging.CordaRPCOps
import net.corda.node.services.api.ServiceHubInternal
import org.crsh.command.BaseCommand
import org.crsh.shell.impl.command.CRaSHSession

/**
 * Simply extends CRaSH BaseCommand to add easy access to the RPC ops class.
 */
open class InteractiveShellCommand : BaseCommand() {
    fun ops() = ((context.session as CRaSHSession).authInfo as CordaSSHAuthInfo).rpcOps
    fun ansiProgressRenderer() = ((context.session as CRaSHSession).authInfo as CordaSSHAuthInfo).ansiProgressRenderer
    fun services() = context.attributes["services"] as ServiceHubInternal
    fun objectMapper() = context.attributes["mapper"] as ObjectMapper
}
