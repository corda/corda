package net.corda.coretests.transactions

import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever
import net.corda.core.contracts.*
import net.corda.core.crypto.*
import net.corda.core.crypto.CompositeKey
import net.corda.core.identity.Party
import net.corda.core.internal.AbstractAttachment
import net.corda.core.internal.HashAgility
import net.corda.core.internal.TESTDSL_UPLOADER
import net.corda.core.internal.createLedgerTransaction
import net.corda.core.node.NotaryInfo
import net.corda.core.serialization.internal.AttachmentsClassLoaderCacheImpl
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.WireTransaction
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.contracts.DummyContract
import net.corda.testing.core.*
import net.corda.testing.internal.createWireTransaction
import net.corda.testing.internal.fakeAttachment
import net.corda.coretesting.internal.rigorousMock
import net.corda.testing.internal.TestingNamedCacheFactory
import org.assertj.core.api.Assertions.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.math.BigInteger
import java.security.KeyPair
import java.security.PublicKey
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

@RunWith(Parameterized::class)
class TransactionTests(private val digestService : DigestService) {
    private companion object {
        const val ISOLATED_JAR = "isolated-4.0.jar"
        val DUMMY_KEY_1 = generateKeyPair()
        val DUMMY_KEY_2 = generateKeyPair()
        val DUMMY_CASH_ISSUER_KEY = entropyToKeyPair(BigInteger.valueOf(10))
        val ALICE = TestIdentity(ALICE_NAME, 70).party
        val BOB = TestIdentity(BOB_NAME, 80).party
        val dummyNotary = TestIdentity(DUMMY_NOTARY_NAME, 20)
        val DUMMY_NOTARY get() = dummyNotary.party
        val DUMMY_NOTARY_KEY get() = dummyNotary.keyPair

        @JvmStatic
        @Parameterized.Parameters
        fun data(): Collection<DigestService> = listOf(
                DigestService.sha2_256
//                DigestService.sha2_384,
//                DigestService.sha2_512
        )
    }

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    @Before
    fun before() {
        HashAgility.init(txHashAlgoName = digestService.hashAlgorithm)
    }

    @Before
    fun after() {
        HashAgility.init()
    }

    private fun makeSigned(wtx: WireTransaction, vararg keys: KeyPair, notarySig: Boolean = true): SignedTransaction {
        val keySigs = keys.map { it.sign(SignableData(wtx.id, SignatureMetadata(1, Crypto.findSignatureScheme(it.public).schemeNumberID))) }
        val sigs = if (notarySig) {
            keySigs + DUMMY_NOTARY_KEY.sign(SignableData(wtx.id, SignatureMetadata(1, Crypto.findSignatureScheme(DUMMY_NOTARY_KEY.public).schemeNumberID)))
        } else {
            keySigs
        }
        return SignedTransaction(wtx, sigs)
    }

    @Test(timeout=300_000)
	fun `signed transaction missing signatures - CompositeKey`() {
        val ak = generateKeyPair()
        val bk = generateKeyPair()
        val ck = generateKeyPair()
        val apub = ak.public
        val bpub = bk.public
        val cpub = ck.public
        val c1 = CompositeKey.Builder().addKeys(apub, bpub).build(2)
        val compKey = CompositeKey.Builder().addKeys(c1, cpub).build(1)
        val wtx = createWireTransaction(
                inputs = listOf(StateRef(SecureHash.randomSHA256(), 0)),
                attachments = emptyList(),
                outputs = emptyList(),
                commands = listOf(dummyCommand(compKey, DUMMY_KEY_1.public, DUMMY_KEY_2.public)),
                notary = DUMMY_NOTARY,
                timeWindow = null
        )
        assertEquals(
                setOf(compKey, DUMMY_KEY_2.public),
                assertFailsWith<SignedTransaction.SignaturesMissingException> { makeSigned(wtx, DUMMY_KEY_1).verifyRequiredSignatures() }.missing
        )

        assertEquals(
                setOf(compKey, DUMMY_KEY_2.public),
                assertFailsWith<SignedTransaction.SignaturesMissingException> { makeSigned(wtx, DUMMY_KEY_1, ak).verifyRequiredSignatures() }.missing
        )
        makeSigned(wtx, DUMMY_KEY_1, DUMMY_KEY_2, ak, bk).verifyRequiredSignatures()
        makeSigned(wtx, DUMMY_KEY_1, DUMMY_KEY_2, ck).verifyRequiredSignatures()
        makeSigned(wtx, DUMMY_KEY_1, DUMMY_KEY_2, ak, bk, ck).verifyRequiredSignatures()
        makeSigned(wtx, DUMMY_KEY_1, DUMMY_KEY_2, ak).verifySignaturesExcept(compKey)
        makeSigned(wtx, DUMMY_KEY_1, ak).verifySignaturesExcept(compKey, DUMMY_KEY_2.public) // Mixed allowed to be missing.
    }

    @Test(timeout=300_000)
	fun `signed transaction missing signatures`() {
        val wtx = createWireTransaction(
                inputs = listOf(StateRef(SecureHash.randomSHA256(), 0)),
                attachments = emptyList(),
                outputs = emptyList(),
                commands = listOf(dummyCommand(DUMMY_KEY_1.public, DUMMY_KEY_2.public)),
                notary = DUMMY_NOTARY,
                timeWindow = null
        )
        assertFailsWith<IllegalArgumentException> { makeSigned(wtx, notarySig = false).verifyRequiredSignatures() }

        assertEquals(
                setOf(DUMMY_KEY_1.public),
                assertFailsWith<SignedTransaction.SignaturesMissingException> { makeSigned(wtx, DUMMY_KEY_2).verifyRequiredSignatures() }.missing
        )
        assertEquals(
                setOf(DUMMY_KEY_2.public),
                assertFailsWith<SignedTransaction.SignaturesMissingException> { makeSigned(wtx, DUMMY_KEY_1).verifyRequiredSignatures() }.missing
        )
        assertEquals(
                setOf(DUMMY_KEY_2.public),
                assertFailsWith<SignedTransaction.SignaturesMissingException> { makeSigned(wtx, DUMMY_CASH_ISSUER_KEY).verifySignaturesExcept(DUMMY_KEY_1.public) }.missing
        )

        makeSigned(wtx, DUMMY_KEY_1).verifySignaturesExcept(DUMMY_KEY_2.public)
        makeSigned(wtx, DUMMY_KEY_2).verifySignaturesExcept(DUMMY_KEY_1.public)

        makeSigned(wtx, DUMMY_KEY_1, DUMMY_KEY_2).verifyRequiredSignatures()
    }

    @Test(timeout=300_000)
	fun `transactions with no inputs can have any notary`() {
        val baseOutState = TransactionState(DummyContract.SingleOwnerState(0, ALICE), DummyContract.PROGRAM_ID, DUMMY_NOTARY, constraint = AlwaysAcceptAttachmentConstraint)
        val inputs = emptyList<StateAndRef<*>>()
        val outputs = listOf(baseOutState, baseOutState.copy(notary = ALICE), baseOutState.copy(notary = BOB))
        val commands = emptyList<CommandWithParties<CommandData>>()
        val attachments = listOf<Attachment>(ContractAttachment(rigorousMock<Attachment>().also {
            doReturn(SecureHash.zeroHash).whenever(it).id
            doReturn(fakeAttachment("nothing", "nada").inputStream()).whenever(it).open()
        }, DummyContract.PROGRAM_ID, uploader = "app"))
        val id = digestService.randomHash()
        val timeWindow: TimeWindow? = null
        val privacySalt = PrivacySalt()
        val attachmentsClassLoaderCache = AttachmentsClassLoaderCacheImpl(TestingNamedCacheFactory())
        val transaction = createLedgerTransaction(
                inputs,
                outputs,
                commands,
                attachments,
                id,
                null,
                timeWindow,
                privacySalt,
                testNetworkParameters(),
                emptyList(),
                isAttachmentTrusted = { true },
                attachmentsClassLoaderCache = attachmentsClassLoaderCache,
                digestService = digestService
        )

        transaction.verify()
    }

    @Test(timeout=300_000)
	fun `transaction cannot have duplicate inputs`() {
        val stateRef = StateRef(SecureHash.randomSHA256(), 0)
        fun buildTransaction() = createWireTransaction(
                inputs = listOf(stateRef, stateRef),
                attachments = emptyList(),
                outputs = emptyList(),
                commands = listOf(dummyCommand(DUMMY_KEY_1.public, DUMMY_KEY_2.public)),
                notary = DUMMY_NOTARY,
                timeWindow = null
        )

        assertFailsWith<IllegalStateException> { buildTransaction() }
    }

    @Test(timeout=300_000)
	fun `general transactions cannot change notary`() {
        val notary: Party = DUMMY_NOTARY
        val inState = TransactionState(DummyContract.SingleOwnerState(0, ALICE), DummyContract.PROGRAM_ID, notary)
        val outState = inState.copy(notary = ALICE)
        val inputs = listOf(StateAndRef(inState, StateRef(SecureHash.randomSHA256(), 0)))

        val outputs = listOf(outState)
        val commands = emptyList<CommandWithParties<CommandData>>()
        val attachments = listOf(ContractAttachment(object : AbstractAttachment({
            (AttachmentsClassLoaderTests::class.java.getResource(ISOLATED_JAR) ?: fail("Missing $ISOLATED_JAR")).openStream().readBytes()
        }, TESTDSL_UPLOADER) {
            @Suppress("OVERRIDE_DEPRECATION")
            override val signers: List<Party> = emptyList()
            override val signerKeys: List<PublicKey> = emptyList()
            override val size: Int = 1234
            override val id: SecureHash = SecureHash.zeroHash
        }, DummyContract.PROGRAM_ID))
        val id = digestService.randomHash()
        val timeWindow: TimeWindow? = null
        val privacySalt = PrivacySalt(digestService.digestLength)
        val attachmentsClassLoaderCache = AttachmentsClassLoaderCacheImpl(TestingNamedCacheFactory())

        fun buildTransaction() = createLedgerTransaction(
                inputs,
                outputs,
                commands,
                attachments,
                id,
                notary,
                timeWindow,
                privacySalt,
                testNetworkParameters(notaries = listOf(NotaryInfo(DUMMY_NOTARY, true))),
                emptyList(),
                isAttachmentTrusted = { true },
                attachmentsClassLoaderCache = attachmentsClassLoaderCache,
                digestService = digestService
        )

        assertFailsWith<TransactionVerificationException.NotaryChangeInWrongTransactionType> { buildTransaction().verify() }
    }

    @Test(timeout=300_000)
	fun `transactions with identical contents must have different ids`() {
        val outputState = TransactionState(DummyContract.SingleOwnerState(0, ALICE), DummyContract.PROGRAM_ID, DUMMY_NOTARY)
        fun buildTransaction() = createWireTransaction(
                inputs = emptyList(),
                attachments = emptyList(),
                outputs = listOf(outputState),
                commands = listOf(dummyCommand(DUMMY_KEY_1.public, DUMMY_KEY_2.public)),
                notary = null,
                timeWindow = null,
                privacySalt = PrivacySalt(digestService.digestLength), // Randomly-generated – used for calculating the id
                digestService = digestService
        )

        val issueTx1 = buildTransaction()
        val issueTx2 = buildTransaction()

        assertNotEquals(issueTx1.id, issueTx2.id)
    }
}
