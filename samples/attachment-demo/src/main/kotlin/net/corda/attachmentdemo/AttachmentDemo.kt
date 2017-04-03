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
import net.corda.core.crypto.sha256
import java.util.zip.Deflater

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

/**
 * A valid InputStream from an in-memory zip as required for tests.
 * Note that a slightly bigger than numOfExpectedBytes size is expected.
 */
fun sizedInputStreamAndHash(numOfExpectedBytes : Int) : InputStreamAndHash {
    if (numOfExpectedBytes <= 0) throw IllegalArgumentException("A positive number of numOfExpectedBytes is required.")
    val baos = ByteArrayOutputStream()
    ZipOutputStream(baos).use({ zos ->
        val arraySize = 1024
        val bytes = ByteArray(arraySize)
        val n = (numOfExpectedBytes - 1) / arraySize + 1 // same as Math.ceil(numOfExpectedBytes/arraySize).
        zos.setLevel(Deflater.NO_COMPRESSION);
        zos.putNextEntry(ZipEntry("z"))
        for (i in 0 until n) {
            zos.write(bytes, 0, arraySize)
        }
        zos.closeEntry()
    })
    return getInputStreamAndHashFromOutputStream(baos)
}

fun getInputStreamAndHashFromOutputStream(baos: ByteArrayOutputStream) : InputStreamAndHash {
    // TODO: Consider converting OutputStream to InputStream without creating a ByteArray, probably using piped streams.
    val bytes = baos.toByteArray()
    // TODO: Consider calculating sha256 on the fly using a DigestInputStream.
    return InputStreamAndHash(ByteArrayInputStream(bytes), bytes.sha256())
}

data class InputStreamAndHash(val inputStream: InputStream, val sha256: SecureHash.SHA256)
