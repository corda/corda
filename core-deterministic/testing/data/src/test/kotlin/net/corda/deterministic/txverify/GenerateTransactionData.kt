package net.corda.deterministic.txverify

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.crypto.entropyToKeyPair
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.deterministic.common.LocalSerializationRule
import net.corda.deterministic.common.MockContractAttachment
import net.corda.deterministic.common.SampleCommandData
import net.corda.deterministic.common.TransactionVerificationRequest
import net.corda.finance.POUNDS
import net.corda.finance.`issued by`
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.contracts.asset.Cash.Commands.*
import net.corda.node.services.api.IdentityServiceInternal
import net.corda.testing.core.*
import net.corda.testing.internal.rigorousMock
import net.corda.testing.node.MockServices
import net.corda.testing.node.ledger
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.FileNotFoundException
import java.io.OutputStream
import java.math.BigInteger
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.FileTime
import java.security.KeyPair
import java.security.PublicKey
import java.util.*
import java.util.Calendar.FEBRUARY
import java.util.jar.JarOutputStream
import java.util.zip.Deflater.NO_COMPRESSION
import java.util.zip.ZipEntry
import java.util.zip.ZipEntry.*
import kotlin.reflect.jvm.jvmName

/**
 * Use the JUnit framework to generate a JAR of test data.
 */
class GenerateTransactionData {
    companion object {
        private val CONSTANT_TIME: FileTime = FileTime.fromMillis(
            GregorianCalendar(1980, FEBRUARY, 1).apply { timeZone = TimeZone.getTimeZone("UTC") }.timeInMillis
        )
        val TX_JAR: Path = Paths.get("build", "txverify-data.jar")
        val DUMMY_NOTARY: Party = TestIdentity(DUMMY_NOTARY_NAME, 20).party

        private val DUMMY_CASH_ISSUER_KEY: KeyPair = entropyToKeyPair(BigInteger.valueOf(10))
        private val DUMMY_CASH_ISSUER_IDENTITY = getTestPartyAndCertificate(Party(CordaX500Name("Snake Oil Issuer", "London", "GB"), DUMMY_CASH_ISSUER_KEY.public))
        val DUMMY_CASH_ISSUER = DUMMY_CASH_ISSUER_IDENTITY.party.ref(1)

        private val megaCorp = TestIdentity(CordaX500Name("MegaCorp", "London", "GB"))
        val MEGA_CORP: Party = megaCorp.party
        val MEGA_CORP_PUBKEY: PublicKey = megaCorp.keyPair.public
        val MINI_CORP_PUBKEY: PublicKey = TestIdentity(CordaX500Name("MiniCorp", "London", "GB")).keyPair.public

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
    val testSerialization = LocalSerializationRule(GenerateTransactionData::class.jvmName)

    private val ledgerServices = MockServices(emptyList(), MEGA_CORP.name, rigorousMock<IdentityServiceInternal>().also {
        doReturn(MEGA_CORP).whenever(it).partyFromKey(MEGA_CORP_PUBKEY)
        doReturn(DUMMY_CASH_ISSUER.party).whenever(it).partyFromKey(DUMMY_CASH_ISSUER_KEY.public)
    })

    @Before
    fun createTransactions() {
        JarOutputStream(Files.newOutputStream(TX_JAR)).use { jar ->
            jar.setComment("Serialised transactions for the Enclavelet")
            jar.setLevel(NO_COMPRESSION)

            jar.putNextEntry(directory("txverify"))
            jar.putNextEntry(compressed("txverify/tx-success.bin"))
            writeSuccess(jar)
            jar.putNextEntry(compressed("txverify/tx-failure.bin"))
            writeFailure(jar)
        }
        testSerialization.reset()
    }

    private fun writeSuccess(output: OutputStream) {
        ledgerServices.ledger(DUMMY_NOTARY) {
            // Issue a couple of cash states and spend them.
            val wtx1 = transaction {
                attachments(Cash.PROGRAM_ID)
                output(Cash.PROGRAM_ID, "c1", Cash.State(1000.POUNDS `issued by` DUMMY_CASH_ISSUER, AnonymousParty(MEGA_CORP_PUBKEY)))
                command(DUMMY_CASH_ISSUER.party.owningKey, Issue())
                verifies()
            }
            val wtx2 = transaction {
                attachments(Cash.PROGRAM_ID)
                output(Cash.PROGRAM_ID, "c2", Cash.State(2000.POUNDS `issued by` DUMMY_CASH_ISSUER, AnonymousParty(MEGA_CORP_PUBKEY)))
                command(DUMMY_CASH_ISSUER.party.owningKey, Issue())
                verifies()
            }
            val wtx3 = transaction {
                attachments(Cash.PROGRAM_ID)
                input("c1")
                input("c2")
                output(Cash.PROGRAM_ID, "c3", Cash.State(3000.POUNDS `issued by` DUMMY_CASH_ISSUER, AnonymousParty(MINI_CORP_PUBKEY)))
                command(MEGA_CORP_PUBKEY, Move())
                verifies()
            }
            val contractAttachment = MockContractAttachment(interpreter.services.cordappProvider.getContractAttachmentID(Cash.PROGRAM_ID)!!, Cash.PROGRAM_ID)
            TransactionVerificationRequest(
                    wtx3.serialize(),
                    arrayOf(wtx1.serialize(), wtx2.serialize()),
                    arrayOf(contractAttachment.serialize().bytes))
                .serialize()
                .writeTo(output)
        }
    }

    private fun writeFailure(output: OutputStream) {
        ledgerServices.ledger(DUMMY_NOTARY) {
            // Issue a couple of cash states and spend them.
            val wtx1 = transaction {
                attachments(Cash.PROGRAM_ID)
                output(Cash.PROGRAM_ID, "c1", Cash.State(1000.POUNDS `issued by` DUMMY_CASH_ISSUER, AnonymousParty(MEGA_CORP_PUBKEY)))
                command(DUMMY_CASH_ISSUER.party.owningKey, Issue())
                verifies()
            }
            val wtx2 = transaction {
                attachments(Cash.PROGRAM_ID)
                output(Cash.PROGRAM_ID, "c2", Cash.State(2000.POUNDS `issued by` DUMMY_CASH_ISSUER, AnonymousParty(MEGA_CORP_PUBKEY)))
                command(DUMMY_CASH_ISSUER.party.owningKey, Issue())
                verifies()
            }
            val wtx3 = transaction {
                attachments(Cash.PROGRAM_ID)
                input("c1")
                input("c2")
                command(DUMMY_CASH_ISSUER.party.owningKey, SampleCommandData)
                output(Cash.PROGRAM_ID, "c3", Cash.State(3000.POUNDS `issued by` DUMMY_CASH_ISSUER, AnonymousParty(MINI_CORP_PUBKEY)))
                failsWith("Required ${Move::class.java.canonicalName} command")
            }
            val contractAttachment = MockContractAttachment(interpreter.services.cordappProvider.getContractAttachmentID(Cash.PROGRAM_ID)!!, Cash.PROGRAM_ID)
            TransactionVerificationRequest(
                    wtx3.serialize(),
                    arrayOf(wtx1.serialize(), wtx2.serialize()),
                    arrayOf(contractAttachment.serialize().bytes))
                .serialize()
                .writeTo(output)
        }
    }

    @Test
    fun verifyTransactions() {
        URLClassLoader(arrayOf(TX_JAR.toUri().toURL())).use { cl ->
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
