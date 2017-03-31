package net.corda.attachmentdemo

import com.google.common.net.HostAndPort
import joptsimple.OptionParser
import net.corda.client.rpc.CordaRPCClient
import net.corda.core.contracts.TransactionType
import net.corda.core.crypto.Party
import net.corda.core.crypto.SecureHash
import net.corda.core.div
import net.corda.core.getOrThrow
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.Emoji
import net.corda.flows.FinalityFlow
import net.corda.nodeapi.config.SSLConfiguration
import net.corda.testing.ALICE_KEY
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.system.exitProcess
import kotlin.test.assertEquals
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.io.ByteArrayInputStream
import java.io.IOException
import com.google.common.io.ByteStreams
import net.corda.core.crypto.sha256

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
            val host = HostAndPort.fromString("localhost:10006")
            println("Connecting to sender node ($host)")
            CordaRPCClient(host).use("demo", "demo") {
                sender(this)
            }
        }
        Role.RECIPIENT -> {
            val host = HostAndPort.fromString("localhost:10009")
            println("Connecting to the recipient node ($host)")
            CordaRPCClient(host).use("demo", "demo") {
                recipient(this)
            }
        }
    }
}

val BANK_OF_LONDON_CP_JAR_HASH = SecureHash.parse("decd098666b9657314870e192ced0c3519c2c9d395507a238338f8d003929de9")
var EXPECTED_HASH = BANK_OF_LONDON_CP_JAR_HASH

/**
 * If numOfClearBytes <=0 or not provided, bank-of-london-cp.jar will be used as attachment.
 * Otherwise, an in memory test .zip attachment of at least numOfClearBytes size, will be used.
 *
 */
fun sender(rpc: CordaRPCOps, numOfClearBytes: Int = 0) {
    if (numOfClearBytes <= 0)
        sender(rpc, Thread.currentThread().contextClassLoader.getResourceAsStream("bank-of-london-cp.jar"), BANK_OF_LONDON_CP_JAR_HASH)
    else {
        val (inputStream, hash) = sizedInputStreamAndHash(numOfClearBytes)
        sender(rpc, inputStream, hash)
    }
}

fun sender(rpc: CordaRPCOps, inputStream: InputStream, hash: SecureHash.SHA256) {
    EXPECTED_HASH = hash
    // Get the identity key of the other side (the recipient).
    val otherSide: Party = rpc.partyFromName("Bank B")!!

    // Make sure we have the file in storage
    // TODO: We should have our own demo file, not share the trader demo file
    if (!rpc.attachmentExists(hash)) {
        inputStream.use {
            val id = rpc.uploadAttachment(it)
            assertEquals(hash, id)
        }
    }

    // Create a trivial transaction that just passes across the attachment - in normal cases there would be
    // inputs, outputs and commands that refer to this attachment.
    val ptx = TransactionType.General.Builder(notary = null)
    require(rpc.attachmentExists(hash))
    ptx.addAttachment(hash)
    // TODO: Add a dummy state and specify a notary, so that the tx hash is randomised each time and the demo can be repeated.

    // Despite not having any states, we have to have at least one signature on the transaction
    ptx.signWith(ALICE_KEY)

    // Send the transaction to the other recipient
    val stx = ptx.toSignedTransaction()
    println("Sending ${stx.id}")
    val flowHandle = rpc.startFlow(::FinalityFlow, stx, setOf(otherSide))
    flowHandle.progress.subscribe(::println)
    flowHandle.returnValue.getOrThrow()
}

fun recipient(rpc: CordaRPCOps) {
    println("Waiting to receive transaction ...")
    val stx = rpc.verifiedTransactions().second.toBlocking().first()
    val wtx = stx.tx
    if (wtx.attachments.isNotEmpty()) {
        assertEquals(EXPECTED_HASH, wtx.attachments.first())
        require(rpc.attachmentExists(EXPECTED_HASH))
        println("File received - we're happy!\n\nFinal transaction is:\n\n${Emoji.renderIfSupported(wtx)}")
    } else {
        println("Error: no attachments found in ${wtx.id}")
    }
}

private fun printHelp(parser: OptionParser) {
    println("""
    Usage: attachment-demo --role [RECIPIENT|SENDER] [options]
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

// A valid InputStream from an in-memory zip as required for tests. Note that we expect a slightly bigger than numOfExpectedBytes size.
@Throws(IOException::class, TypeCastException::class, IllegalArgumentException::class)
fun sizedInputStreamAndHash(numOfExpectedBytes : Int) : InputStreamAndHash {
    if (numOfExpectedBytes <= 0) throw IllegalArgumentException("A positive number of numOfExpectedBytes is required.")
    val baos = ByteArrayOutputStream()
    try {
        val stream = Thread.currentThread().contextClassLoader.getResourceAsStream("attachment-demo24KB.zip") as BufferedInputStream
        val bytes = ByteStreams.toByteArray(stream) // size of attachment-demo24KB.zip = 23848 bytes.
        ZipOutputStream(baos).use({ zos ->
            /* As each entry is a 23848-sized .zip file, if we run it for 10,000,000 bytes, it will return 420 entries, as 420x23848 = 10,016,160 bytes.
             * However, the final .zip is expected to be slightly bigger than the above number, because each entry is already a compressed file,
             * while there is an additional overhead, due to the zip entries structure.
             */
            val n = (numOfExpectedBytes - 1) / 23848 + 1 // same as Math.ceil(numOfExpectedBytes/23848).
            for (i in 0 until n) {
                zos.putNextEntry(ZipEntry("$i"))
                zos.write(bytes, 0, 23848)
                zos.closeEntry()
            }
        })
    } catch (ioe: IOException) {
        throw IOException(ioe)
    } catch (tce: TypeCastException) {
        throw TypeCastException("Attachment file does not exist.")
    }
    return getInputStreamAndHashFromOutputStream(baos)
}

fun getInputStreamAndHashFromOutputStream(baos: ByteArrayOutputStream) : InputStreamAndHash {
    val bytes = baos.toByteArray()
    println(bytes.size)
    return InputStreamAndHash(ByteArrayInputStream(bytes), bytes.sha256())
}

data class InputStreamAndHash(val inputStream: InputStream, val sha256: SecureHash.SHA256)
