package net.corda.attachmentdemo

import joptsimple.OptionParser
import net.corda.attachmentdemo.contracts.AttachmentContract
import net.corda.attachmentdemo.workflows.AttachmentDemoFlow
import net.corda.client.rpc.CordaRPCClient
import net.corda.core.crypto.SecureHash
import net.corda.core.internal.Emoji
import net.corda.core.internal.InputStreamAndHash
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startTrackedFlow
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.getOrThrow
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.jar.JarInputStream
import javax.servlet.http.HttpServletResponse.SC_OK
import javax.ws.rs.core.HttpHeaders.CONTENT_DISPOSITION
import javax.ws.rs.core.MediaType.APPLICATION_OCTET_STREAM
import kotlin.system.exitProcess

internal enum class Role {
    SENDER,
    RECIPIENT
}

fun main(args: Array<String>) {
    val parser = OptionParser()

    val roleArg = parser.accepts("role").withRequiredArg().ofType(Role::class.java).required()
    val options = try {
        parser.parse(*args)
    } catch (e: Exception) {
        println(e.message)
        printHelp(parser)
        exitProcess(1)
    }

    val role = options.valueOf(roleArg)!!
    when (role) {
        Role.SENDER -> {
            val host = NetworkHostAndPort("localhost", 10006)
            println("Connecting to sender node ($host)")
            CordaRPCClient(host).start("demo", "demo").use {
                sender(it.proxy)
            }
        }
        Role.RECIPIENT -> {
            val host = NetworkHostAndPort("localhost", 10009)
            println("Connecting to the recipient node ($host)")
            CordaRPCClient(host).start("demo", "demo").use {
                recipient(it.proxy, 10010)
            }
        }
    }
}

/** An in memory test zip attachment of at least numOfClearBytes size, will be used. */
// DOCSTART 2
fun sender(rpc: CordaRPCOps, numOfClearBytes: Int = 1024) { // default size 1K.
    val (inputStream, hash) = InputStreamAndHash.createInMemoryTestZip(numOfClearBytes, 0)
    sender(rpc, inputStream, hash)
}

private fun sender(rpc: CordaRPCOps, inputStream: InputStream, hash: SecureHash.SHA256) {
    // Get the identity key of the other side (the recipient).
    val notaryParty = rpc.partiesFromName("Notary", false).firstOrNull() ?: throw IllegalArgumentException("Couldn't find notary party")
    val bankBParty = rpc.partiesFromName("Bank B", false).firstOrNull() ?: throw IllegalArgumentException("Couldn't find Bank B party")
    // Make sure we have the file in storage
    if (!rpc.attachmentExists(hash)) {
        inputStream.use {
            val id = rpc.uploadAttachment(it)
            require(hash == id) { "Id was '$id' instead of '$hash'" }
        }
        require(rpc.attachmentExists(hash)) { "Attachment matching hash: $hash does not exist" }
    }

    val flowHandle = rpc.startTrackedFlow(::AttachmentDemoFlow, bankBParty, notaryParty, hash)
    flowHandle.progress.subscribe(::println)
    val stx = flowHandle.returnValue.getOrThrow()
    println("Sent ${stx.id}")
}
// DOCEND 2

@Suppress("DEPRECATION")
// DOCSTART 1
fun recipient(rpc: CordaRPCOps, webPort: Int) {
    println("Waiting to receive transaction ...")
    val stx = rpc.internalVerifiedTransactionsFeed().updates.toBlocking().first()
    val wtx = stx.tx
    if (wtx.attachments.isNotEmpty()) {
        if (wtx.outputs.isNotEmpty()) {
            val state = wtx.outputsOfType<AttachmentContract.State>().single()
            require(rpc.attachmentExists(state.hash)) { "attachment matching hash: ${state.hash} does not exist" }

            // Download the attachment via the Web endpoint.
            val connection = URL("http://localhost:$webPort/attachments/${state.hash}").openConnection() as HttpURLConnection
            try {
                require(connection.responseCode == SC_OK) { "HTTP status code was ${connection.responseCode}" }
                require(connection.contentType == APPLICATION_OCTET_STREAM) { "Content-Type header was ${connection.contentType}" }
                require(connection.getHeaderField(CONTENT_DISPOSITION) == "attachment; filename=\"${state.hash}.zip\"") {
                    "Content-Disposition header was ${connection.getHeaderField(CONTENT_DISPOSITION)}"
                }

                // Write out the entries inside this jar.
                println("Attachment JAR contains these entries:")
                JarInputStream(connection.inputStream).use {
                    while (true) {
                        val e = it.nextJarEntry ?: break
                        println("Entry> ${e.name}")
                        it.closeEntry()
                    }
                }
            } finally {
                connection.disconnect()
            }
            println("File received - we're happy!\n\nFinal transaction is:\n\n${Emoji.renderIfSupported(wtx)}")
        } else {
            println("Error: no output state found in ${wtx.id}")
        }
    } else {
        println("Error: no attachments found in ${wtx.id}")
    }
}
// DOCEND 1

private fun printHelp(parser: OptionParser) {
    println("""
    Usage: attachment-demo --role [RECIPIENT|SENDER] [options]
    Please refer to the documentation in docs/build/index.html for more info.

    """.trimIndent())
    parser.printHelpOn(System.out)
}
