package net.corda.deterministic.data

import net.corda.core.serialization.deserialize
import net.corda.deterministic.verifier.LocalSerializationRule
import net.corda.deterministic.verifier.TransactionVerificationRequest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.FileNotFoundException
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.FileTime
import java.util.*
import java.util.Calendar.*
import java.util.jar.JarOutputStream
import java.util.zip.Deflater.NO_COMPRESSION
import java.util.zip.ZipEntry
import java.util.zip.ZipEntry.*
import kotlin.reflect.jvm.jvmName

/**
 * Use the JUnit framework to generate a JAR of test data.
 */
class GenerateData {
    companion object {
        private val CONSTANT_TIME: FileTime = FileTime.fromMillis(
            GregorianCalendar(1980, FEBRUARY, 1).apply { timeZone = TimeZone.getTimeZone("UTC") }.timeInMillis
        )
        private const val KEYSTORE_ALIAS = "tx"
        private val KEYSTORE_PASSWORD = "deterministic".toCharArray()
        private val TEST_DATA: Path = Paths.get("build", "test-data.jar")

        private fun compressed(name: String) = ZipEntry(name).apply {
            lastModifiedTime = CONSTANT_TIME
            method = DEFLATED
        }

        private fun directory(name: String) = ZipEntry(name).apply {
            lastModifiedTime = CONSTANT_TIME
            method = STORED
            compressedSize = 0
            size = 0
            crc = 0
        }
    }

    @Rule
    @JvmField
    val testSerialization = LocalSerializationRule(GenerateData::class.jvmName)

    @Before
    fun createTransactions() {
        JarOutputStream(Files.newOutputStream(TEST_DATA)).use { jar ->
            jar.setComment("Test data for Deterministic Corda")
            jar.setLevel(NO_COMPRESSION)

            // Serialised transactions for the Enclavelet
            jar.putNextEntry(directory("txverify"))
            jar.putNextEntry(compressed("txverify/tx-success.bin"))
            TransactionGenerator.writeSuccess(jar)
            jar.putNextEntry(compressed("txverify/tx-failure.bin"))
            TransactionGenerator.writeFailure(jar)

            // KeyStore containing an EC private key.
            jar.putNextEntry(directory("keystore"))
            jar.putNextEntry(compressed("keystore/txsignature.pfx"))
            KeyStoreGenerator.writeKeyStore(jar, KEYSTORE_ALIAS, KEYSTORE_PASSWORD)
        }
        testSerialization.reset()
    }

    @Test
    fun verifyTransactions() {
        URLClassLoader(arrayOf(TEST_DATA.toUri().toURL())).use { cl ->
            cl.loadResource("txverify/tx-success.bin")
                .deserialize<TransactionVerificationRequest>()
                .toLedgerTransaction()
                .verify()

            cl.loadResource("txverify/tx-failure.bin")
                .deserialize<TransactionVerificationRequest>()
                .toLedgerTransaction()
        }
    }

    private fun ClassLoader.loadResource(resourceName: String): ByteArray {
        return getResourceAsStream(resourceName)?.use { it.readBytes() }
                    ?: throw FileNotFoundException(resourceName)
    }
}
