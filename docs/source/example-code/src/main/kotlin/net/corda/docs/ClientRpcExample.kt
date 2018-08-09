@file:Suppress("unused")

package net.corda.docs

// START 1
import net.corda.client.rpc.CordaRPCClient
import net.corda.core.utilities.NetworkHostAndPort.Companion.parse
import net.corda.core.utilities.loggerFor
import org.slf4j.Logger

class ExampleRpcClient {
    companion object {
        val logger: Logger = loggerFor<ExampleRpcClient>()
    }

    fun main(args: Array<String>) {
        require(args.size == 3) { "Usage: TemplateClient <node address> <username> <password>" }
        val nodeAddress = parse(args[0])
        val username = args[1]
        val password = args[2]

        val client = CordaRPCClient(nodeAddress)
        val connection = client.start(username, password)
        val cordaRPCOperations = connection.proxy

        logger.info(cordaRPCOperations.currentNodeTime().toString())

        connection.notifyServerAndClose()
    }
}
// END 1