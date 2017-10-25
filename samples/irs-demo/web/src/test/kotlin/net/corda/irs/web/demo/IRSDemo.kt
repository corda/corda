@file:JvmName("IRSDemo")

package net.corda.irs.web.demo

import joptsimple.OptionParser
import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.NetworkHostAndPort
import kotlin.system.exitProcess

enum class Role {
    UploadRates,
    Trade,
    Date
}

fun main(args: Array<String>) {
    val parser = OptionParser()
    val roleArg = parser.accepts("role").withRequiredArg().ofType(Role::class.java).required()
    val valueArg = parser.nonOptions()
    val options = try {
        parser.parse(*args)
    } catch (e: Exception) {
        println(e.message)
        printHelp(parser)
        exitProcess(1)
    }

    // What happens next depends on the role. The buyer sits around waiting for a trade to start. The seller role
    // will contact the buyer and actually make something happen.
    val role = options.valueOf(roleArg)!!
    val value = options.valueOf(valueArg)
    when (role) {
        Role.UploadRates -> IRSDemoClientApi(NetworkHostAndPort("localhost", 10004)).runUploadRates()
        Role.Trade -> IRSDemoClientApi(NetworkHostAndPort("localhost", 10007)).runTrade(value, CordaX500Name.parse("O=Notary Service,L=Zurich,C=CH"))
        Role.Date -> IRSDemoClientApi(NetworkHostAndPort("localhost", 10010)).runDateChange(value)
    }
}

fun printHelp(parser: OptionParser) {
    println("""
Usage: irs-demo --role [UploadRates|Trade|Date] [trade ID|yyy-mm-dd]
Please refer to the documentation in docs/build/index.html for more info.

""".trimIndent())
    parser.printHelpOn(System.out)
}


