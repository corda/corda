package net.corda.traderdemo

import joptsimple.OptionParser
import net.corda.client.rpc.CordaRPCClient
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.contextLogger
import net.corda.finance.DOLLARS
import net.corda.testing.core.DUMMY_BANK_A_NAME
import net.corda.testing.core.DUMMY_BANK_B_NAME
import kotlin.system.exitProcess

/**
 * This entry point allows for command line running of the trader demo functions on nodes started by Main.kt.
 */
fun main(args: Array<String>) {
    TraderDemo().main(args)
}

private class TraderDemo {
    enum class Role {
        BANK,
        SELLER
    }

    companion object {
        private val logger = contextLogger()
        val buyerName = DUMMY_BANK_A_NAME
        val sellerName = DUMMY_BANK_B_NAME
        const val sellerRpcPort = 10009
        const val bankRpcPort = 10012
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
        // will contact the buyer and actually make something happen.  We intentionally use large amounts here.
        val role = options.valueOf(roleArg)!!
        if (role == Role.BANK) {
            val bankHost = NetworkHostAndPort("localhost", bankRpcPort)
            CordaRPCClient(bankHost).use("demo", "demo") {
                TraderDemoClientApi(it.proxy).runIssuer(1_100_000_000_000.DOLLARS, buyerName, sellerName)
            }
        } else {
            val sellerHost = NetworkHostAndPort("localhost", sellerRpcPort)
            CordaRPCClient(sellerHost).use("demo", "demo") {
                TraderDemoClientApi(it.proxy).runSeller(1_000_000_000_000.DOLLARS, buyerName)
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
