package net.corda.traderdemo

import com.google.common.net.HostAndPort
import joptsimple.OptionParser
import net.corda.client.rpc.CordaRPCClient
import net.corda.core.contracts.DOLLARS
import net.corda.core.crypto.X509Utilities
import net.corda.core.utilities.DUMMY_BANK_A
import net.corda.core.utilities.loggerFor
import org.bouncycastle.asn1.x500.X500Name
import org.slf4j.Logger
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
            val host = HostAndPort.fromString("localhost:10006")
            CordaRPCClient(host).use("demo", "demo") {
                TraderDemoClientApi(this).runBuyer()
            }
        } else {
            val host = HostAndPort.fromString("localhost:10009")
            CordaRPCClient(host).use("demo", "demo") {
                TraderDemoClientApi(this).runSeller(1000.DOLLARS, DUMMY_BANK_A.name)
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
}
