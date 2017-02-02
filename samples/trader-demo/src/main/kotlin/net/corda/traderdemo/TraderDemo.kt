package net.corda.traderdemo

import com.google.common.net.HostAndPort
import joptsimple.OptionParser
import net.corda.core.contracts.DOLLARS
import net.corda.core.div
import net.corda.core.utilities.loggerFor
import net.corda.node.services.config.SSLConfiguration
import net.corda.node.services.messaging.CordaRPCClient
import org.slf4j.Logger
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.system.exitProcess

/**
 * This entry point allows for command line running of the trader demo functions on nodes started by Main.kt.
 */
fun main(args: Array<String>) {
    TraderDemo().main(args)
}

private class TraderDemo {
    enum class Role {
        BUYER,
        SELLER
    }

    companion object {
        val logger: Logger = loggerFor<TraderDemo>()
    }

    fun main(args: Array<String>) {
        val parser = OptionParser()
        val certsPath = parser.accepts("certificates").withRequiredArg()

        val roleArg = parser.accepts("role").withRequiredArg().ofType(Role::class.java).required()
        val options = try {
            parser.parse(*args)
        } catch (e: Exception) {
            logger.error(e.message)
            printHelp(parser)
            exitProcess(1)
        }

        // What happens next depends on the role. The buyer sits around waiting for a trade to start. The seller role
        // will contact the buyer and actually make something happen.
        val role = options.valueOf(roleArg)!!
        if (role == Role.BUYER) {
            val host = HostAndPort.fromString("localhost:10004")
            CordaRPCClient(host, sslConfigFor("BankA", options.valueOf(certsPath))).use("demo", "demo") {
                TraderDemoClientApi(this).runBuyer()
            }
        } else {
            val host = HostAndPort.fromString("localhost:10006")
            CordaRPCClient(host, sslConfigFor("BankB", options.valueOf(certsPath))).use("demo", "demo") {
                TraderDemoClientApi(this).runSeller(1000.DOLLARS, "Bank A")
            }
        }
    }

    fun printHelp(parser: OptionParser) {
        println("""
        Usage: trader-demo --role [BUYER|SELLER]
        Please refer to the documentation in docs/build/index.html for more info.

        """.trimIndent())
        parser.printHelpOn(System.out)
    }

    // TODO: Take this out once we have a dedicated RPC port and allow SSL on it to be optional.
    private fun sslConfigFor(nodename: String, certsPath: String?): SSLConfiguration {
        return object : SSLConfiguration {
            override val keyStorePassword: String = "cordacadevpass"
            override val trustStorePassword: String = "trustpass"
            override val certificatesDirectory: Path = if (certsPath != null) Paths.get(certsPath) else Paths.get("build") / "nodes" / nodename / "certificates"
        }
    }
}
