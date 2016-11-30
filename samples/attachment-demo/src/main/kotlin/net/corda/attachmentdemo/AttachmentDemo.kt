package net.corda.attachmentdemo

import com.google.common.net.HostAndPort
import joptsimple.OptionParser
import net.corda.core.utilities.loggerFor
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    AttachmentDemo().main(args)
}

private class AttachmentDemo {
    internal enum class Role() {
        SENDER,
        RECIPIENT
    }

    private companion object {
        val log = loggerFor<AttachmentDemo>()
    }

    fun main(args: Array<String>) {
        val parser = OptionParser()

        val roleArg = parser.accepts("role").withRequiredArg().ofType(Role::class.java).required()
        val options = try {
            parser.parse(*args)
        } catch (e: Exception) {
            log.error(e.message)
            printHelp(parser)
            exitProcess(1)
        }

        val role = options.valueOf(roleArg)!!
        when (role) {
            Role.SENDER -> {
                val api = AttachmentDemoClientApi(HostAndPort.fromString("localhost:10005"))
                api.runSender(api.getOtherSideKey())
            }
            Role.RECIPIENT -> AttachmentDemoClientApi(HostAndPort.fromString("localhost:10007")).runRecipient()
        }
    }

    private fun printHelp(parser: OptionParser) {
        println("""
    Usage: attachment-demo --role [RECIPIENT|SENDER] [options]
    Please refer to the documentation in docs/build/index.html for more info.

    """.trimIndent())
        parser.printHelpOn(System.out)
    }

}
