package net.corda.node

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.core.messaging.CordaRPCOps
import net.corda.node.services.api.ServiceHubInternal
import org.crsh.command.BaseCommand

/**
 * Simply extends CRaSH BaseCommand to add easy access to the RPC ops class.
 */
open class InteractiveShellCommand : BaseCommand() {
    fun ops() = context.attributes["ops"] as CordaRPCOps
    fun services() = context.attributes["services"] as ServiceHubInternal
    fun objectMapper() = context.attributes["mapper"] as ObjectMapper
}
