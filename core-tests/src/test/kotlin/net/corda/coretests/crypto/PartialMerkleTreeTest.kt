package net.corda.coretests.crypto

import net.corda.core.contracts.Command
import net.corda.core.contracts.PrivacySalt
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TimeWindow
import net.corda.core.contracts.TransactionState
import net.corda.core.crypto.DigestService
import net.corda.core.crypto.MerkleTree
import net.corda.core.crypto.MerkleTreeException
import net.corda.core.crypto.PartialMerkleTree
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.internal.DigestAlgorithmFactory
import net.corda.core.crypto.keys
import net.corda.core.crypto.randomHash
import net.corda.core.crypto.sha256
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.BLAKE2s256DigestAlgorithm
import net.corda.core.internal.SHA256BLAKE2s256DigestAlgorithm
import net.corda.core.node.NotaryInfo
import net.corda.core.node.services.IdentityService
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.transactions.ReferenceStateRef
import net.corda.core.transactions.WireTransaction
import net.corda.coretesting.internal.TEST_TX_TIME
import net.corda.finance.DOLLARS
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.`issued by`
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.core.TestIdentity
import net.corda.testing.dsl.LedgerDSL
import net.corda.testing.dsl.TestLedgerDSLInterpreter
import net.corda.testing.dsl.TestTransactionDSLInterpreter
import net.corda.testing.internal.createWireTransaction
import net.corda.testing.node.MockServices
import net.corda.testing.node.ledger
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.security.PublicKey
import java.util.function.Predicate
import java.util.stream.IntStream
import kotlin.streams.toList
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(Parameterized::class)
class PartialMerkleTreeTest(private var digestService: DigestService) {
    private companion object {
        val DUMMY_NOTARY = TestIdentity(DUMMY_NOTARY_NAME, 20).party
        val megaCorp = TestIdentity(CordaX500Name("MegaCorp", "London", "GB"))
        val miniCorp = TestIdentity(CordaX500Name("MiniCorp", "London", "GB"))
        val MEGA_CORP get() = megaCorp.party
        val MEGA_CORP_PUBKEY get() = megaCorp.publicKey
        val MINI_CORP get() = miniCorp.party
        val MINI_CORP_PUBKEY get() = miniCorp.publicKey

        init {
            DigestAlgorithmFactory.registerClass(BLAKE2s256DigestAlgorithm::class.java.name)
            DigestAlgorithmFactory.registerClass(SHA256BLAKE2s256DigestAlgorithm::class.java.name)
        }

        @JvmStatic
        @Parameterized.Parameters
        fun data(): Collection<DigestService> = listOf(
                DigestService.sha2_256,
                DigestService.sha2_384,
                DigestService.sha2_512,
                DigestService("BLAKE_TEST"),
                DigestService("SHA256-BLAKE2S256-TEST")
        )
    }

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    private val nodes = "abcdef"
    private lateinit var hashed: List<SecureHash>
    private lateinit var expectedRoot: SecureHash
    private lateinit var merkleTree: MerkleTree
    private lateinit var testLedger: LedgerDSL<TestTransactionDSLInterpreter, TestLedgerDSLInterpreter>
    private lateinit var txs: List<WireTransaction>
    private lateinit var testTx: WireTransaction

    @Before
    fun init() {
        digestService = DigestService.default
        hashed = nodes.map { digestService.hash(it.serialize().bytes) }
        expectedRoot = MerkleTree.getMerkleTree(hashed.toMutableList() + listOf(digestService.zeroHash, digestService.zeroHash), digestService).hash
        merkleTree = MerkleTree.getMerkleTree(hashed, digestService)

        testLedger = MockServices(
                cordappPackages = emptyList(),
                initialIdentity = TestIdentity(MEGA_CORP.name),
                identityService = mock<IdentityService>().also {
                    doReturn(MEGA_CORP).whenever(it).partyFromKey(MEGA_CORP_PUBKEY)
                },
                networkParameters = testNetworkParameters(minimumPlatformVersion = 4, notaries = listOf(NotaryInfo(DUMMY_NOTARY, true)))
        ).ledger(DUMMY_NOTARY) {
            unverifiedTransaction {
                attachments(Cash.PROGRAM_ID)
                output(Cash.PROGRAM_ID, "MEGA_CORP cash",
                        Cash.State(
                                amount = 1000.DOLLARS `issued by` MEGA_CORP.ref(1, 1),
                                owner = MEGA_CORP))
                output(Cash.PROGRAM_ID, "dummy cash 1",
                        Cash.State(
                                amount = 900.DOLLARS `issued by` MEGA_CORP.ref(1, 1),
                                owner = MINI_CORP))
            }
            transaction {
                attachments(Cash.PROGRAM_ID)
                input("MEGA_CORP cash")
                reference("dummy cash 1")
                output(Cash.PROGRAM_ID, "MEGA_CORP cash".output<Cash.State>().copy(owner = MINI_CORP))
                command(MEGA_CORP_PUBKEY, Cash.Commands.Move())
                timeWindow(TEST_TX_TIME)
                this.verifies()
            }
        }
        txs = testLedger.interpreter.transactionsToVerify
        testTx = txs[0]
    }

    // Building full Merkle Tree tests.
    @Test(timeout=300_000)
	fun `building Merkle tree with 6 nodes - no rightmost nodes`() {
        assertEquals(expectedRoot, merkleTree.hash)
    }

    @Test(timeout=300_000)
	fun `building Merkle tree - no hashes`() {
        assertFailsWith<MerkleTreeException> { MerkleTree.getMerkleTree(emptyList(), digestService) }
    }

    @Test(timeout=300_000)
	fun `building Merkle tree one node`() {
        val node = 'a'.serialize().sha256()
        val mt = MerkleTree.getMerkleTree(listOf(node), digestService)
        assertEquals(node, mt.hash)
    }

    @Test(timeout=300_000)
	fun `building Merkle tree odd number of nodes`() {
        val odd = hashed.subList(0, 3)
        val h1 = hashed[0].concatenate(hashed[1])
        val h2 = hashed[2].concatenate(digestService.zeroHash)
        val expected = h1.concatenate(h2)
        val mt = MerkleTree.getMerkleTree(odd, digestService)
        assertEquals(mt.hash, expected)
    }

    @Test(timeout=300_000)
	fun `check full tree`() {
        val h = digestService.randomHash()
        val left = MerkleTree.Node(h, MerkleTree.Node(h, MerkleTree.Leaf(h), MerkleTree.Leaf(h)),
                MerkleTree.Node(h, MerkleTree.Leaf(h), MerkleTree.Leaf(h)))
        val right = MerkleTree.Node(h, MerkleTree.Leaf(h), MerkleTree.Leaf(h))
        val tree = MerkleTree.Node(h, left, right)
        assertFailsWith<MerkleTreeException> { PartialMerkleTree.build(tree, listOf(h)) }
        PartialMerkleTree.build(right, listOf(h, h)) // Node and two leaves.
        PartialMerkleTree.build(MerkleTree.Leaf(h), listOf(h)) // Just a leaf.
    }

    @Test(timeout=300_000)
	fun `building Merkle tree for a tx and nonce test`() {
        fun filtering(elem: Any): Boolean {
            return when (elem) {
                is StateRef -> true
                is TransactionState<*> -> elem.data.participants[0].owningKey.keys == MINI_CORP_PUBKEY.keys
                is Command<*> -> MEGA_CORP_PUBKEY in elem.signers
                is TimeWindow -> true
                is PublicKey -> elem == MEGA_CORP_PUBKEY
                else -> false
            }
        }

        val d = testTx.serialize().deserialize()
        assertEquals(testTx.id, d.id)

        val ftx = testTx.buildFilteredTransaction(Predicate(::filtering))

        // We expect 5 and not 4 component groups, because there is at least one command in the ftx and thus,
        // the signers component is also sent (required for visibility purposes).
        assertEquals(5, ftx.filteredComponentGroups.size)
        assertEquals(1, ftx.inputs.size)
        assertEquals(0, ftx.references.size)
        assertEquals(0, ftx.attachments.size)
        assertEquals(1, ftx.outputs.size)
        assertEquals(1, ftx.commands.size)
        assertNull(ftx.notary)
        assertNotNull(ftx.timeWindow)
        assertNull(ftx.networkParametersHash)
        ftx.verify()
    }

    @Test(timeout=300_000)
	fun `same transactions with different notaries have different ids`() {
        // We even use the same privacySalt, and thus the only difference between the two transactions is the notary party.
        val privacySalt = PrivacySalt()
        val wtx1 = makeSimpleCashWtx(DUMMY_NOTARY, privacySalt)
        val wtx2 = makeSimpleCashWtx(MEGA_CORP, privacySalt)
        assertEquals(wtx1.privacySalt, wtx2.privacySalt)
        assertNotEquals(wtx1.id, wtx2.id)
    }

    @Test(timeout=300_000)
	fun `nothing filtered`() {
        val ftxNothing = testTx.buildFilteredTransaction { false }
        assertTrue(ftxNothing.componentGroups.isEmpty())
        assertTrue(ftxNothing.attachments.isEmpty())
        assertTrue(ftxNothing.commands.isEmpty())
        assertTrue(ftxNothing.inputs.isEmpty())
        assertTrue(ftxNothing.references.isEmpty())
        assertTrue(ftxNothing.outputs.isEmpty())
        assertNull(ftxNothing.timeWindow)
        assertTrue(ftxNothing.availableComponentGroups.flatten().isEmpty())
        assertNull(ftxNothing.networkParametersHash)
        ftxNothing.verify() // We allow empty ftx transactions (eg from a timestamp authority that blindly signs).
    }

    // Partial Merkle Tree building tests.
    @Test(timeout=300_000)
	fun `build Partial Merkle Tree, only left nodes branch`() {
        val inclHashes = listOf(hashed[3], hashed[5])
        val pmt = PartialMerkleTree.build(merkleTree, inclHashes)
        assertTrue(pmt.verify(merkleTree.hash, inclHashes))
    }

    @Test(timeout=300_000)
	fun `build Partial Merkle Tree, include zero leaves`() {
        val pmt = PartialMerkleTree.build(merkleTree, emptyList())
        assertTrue(pmt.verify(merkleTree.hash, emptyList()))
    }

    @Test(timeout=300_000)
	fun `build Partial Merkle Tree, include all leaves`() {
        val pmt = PartialMerkleTree.build(merkleTree, hashed)
        assertTrue(pmt.verify(merkleTree.hash, hashed))
    }

    @Test(timeout=300_000)
	fun `build Partial Merkle Tree - duplicate leaves failure`() {
        val inclHashes = arrayListOf(hashed[3], hashed[5], hashed[3], hashed[5])
        assertFailsWith<MerkleTreeException> { PartialMerkleTree.build(merkleTree, inclHashes) }
    }

    @Test(timeout=300_000)
	fun `build Partial Merkle Tree - only duplicate leaves, less included failure`() {
        val leaves = "aaa"
        val hashes = leaves.map { it.serialize().hash }
        val mt = MerkleTree.getMerkleTree(hashes, digestService)
        assertFailsWith<MerkleTreeException> { PartialMerkleTree.build(mt, hashes.subList(0, 1)) }
    }

    @Test(timeout=300_000)
	fun `verify Partial Merkle Tree - too many leaves failure`() {
        val inclHashes = arrayListOf(hashed[3], hashed[5])
        val pmt = PartialMerkleTree.build(merkleTree, inclHashes)
        inclHashes.add(hashed[0])
        assertFalse(pmt.verify(merkleTree.hash, inclHashes))
    }

    @Test(timeout=300_000)
	fun `verify Partial Merkle Tree - too little leaves failure`() {
        val inclHashes = arrayListOf(hashed[3], hashed[5], hashed[0])
        val pmt = PartialMerkleTree.build(merkleTree, inclHashes)
        inclHashes.remove(hashed[0])
        assertFalse(pmt.verify(merkleTree.hash, inclHashes))
    }

    @Test(timeout=300_000)
	fun `verify Partial Merkle Tree - duplicate leaves failure`() {
        val mt = MerkleTree.getMerkleTree(hashed.subList(0, 5), digestService) // Odd number of leaves. Last one is duplicated.
        val inclHashes = arrayListOf(hashed[3], hashed[4])
        val pmt = PartialMerkleTree.build(mt, inclHashes)
        inclHashes.add(hashed[4])
        assertFalse(pmt.verify(mt.hash, inclHashes))
    }

    @Test(timeout=300_000)
	fun `verify Partial Merkle Tree - different leaves failure`() {
        val inclHashes = arrayListOf(hashed[3], hashed[5])
        val pmt = PartialMerkleTree.build(merkleTree, inclHashes)
        assertFalse(pmt.verify(merkleTree.hash, listOf(hashed[2], hashed[4])))
    }

    @Test(timeout=300_000)
	fun `verify Partial Merkle Tree - wrong root`() {
        val inclHashes = listOf(hashed[3], hashed[5])
        val pmt = PartialMerkleTree.build(merkleTree, inclHashes)
        val wrongRoot = hashed[3].concatenate(hashed[5])
        assertFalse(pmt.verify(wrongRoot, inclHashes))
    }

    @Test(timeout=300_000)
    fun `hash map serialization not allowed`() {
        val hm1 = hashMapOf("a" to 1, "b" to 2, "c" to 3, "e" to 4)
        assertThatIllegalArgumentException().isThrownBy {
            hm1.serialize()
        }
    }

    private fun makeSimpleCashWtx(
            notary: Party,
            privacySalt: PrivacySalt = PrivacySalt(),
            timeWindow: TimeWindow? = null,
            attachments: List<SecureHash> = emptyList()
    ): WireTransaction {
        return createWireTransaction(
                inputs = testTx.inputs,
                attachments = attachments,
                outputs = testTx.outputs,
                commands = testTx.commands,
                notary = notary,
                timeWindow = timeWindow,
                privacySalt = privacySalt,
                digestService = digestService
        )
    }

    @Test(timeout=300_000)
	fun `Find leaf index`() {
        // A Merkle tree with 20 leaves.
        val sampleLeaves = IntStream.rangeClosed(0, 19).toList().map { digestService.hash(it.toString()) }
        val merkleTree = MerkleTree.getMerkleTree(sampleLeaves, digestService)

        // Provided hashes are not in the tree.
        assertFailsWith<MerkleTreeException> { PartialMerkleTree.build(merkleTree, listOf(digestService.hash("20"))) }
        // One of the provided hashes is not in the tree.
        assertFailsWith<MerkleTreeException> { PartialMerkleTree.build(merkleTree, listOf(digestService.hash("20"), digestService.hash("1"), digestService.hash("5"))) }

        val pmt = PartialMerkleTree.build(merkleTree, listOf(digestService.hash("1"), digestService.hash("5"), digestService.hash("0"), digestService.hash("19")))
        // First leaf.
        assertEquals(0, pmt.leafIndex(digestService.hash("0")))
        // Second leaf.
        assertEquals(1, pmt.leafIndex(digestService.hash("1")))
        // A random leaf.
        assertEquals(5, pmt.leafIndex(digestService.hash("5")))
        // The last leaf.
        assertEquals(19, pmt.leafIndex(digestService.hash("19")))
        // The provided hash is not in the tree.
        assertFailsWith<MerkleTreeException> { pmt.leafIndex(digestService.hash("10")) }
        // The provided hash is not in the tree (using a leaf that didn't exist in the original Merkle tree).
        assertFailsWith<MerkleTreeException> { pmt.leafIndex(digestService.hash("30")) }

        val pmtFirstElementOnly = PartialMerkleTree.build(merkleTree, listOf(digestService.hash("0")))
        assertEquals(0, pmtFirstElementOnly.leafIndex(digestService.hash("0")))
        // The provided hash is not in the tree.
        assertFailsWith<MerkleTreeException> { pmtFirstElementOnly.leafIndex(digestService.hash("10")) }

        val pmtLastElementOnly = PartialMerkleTree.build(merkleTree, listOf(digestService.hash("19")))
        assertEquals(19, pmtLastElementOnly.leafIndex(digestService.hash("19")))
        // The provided hash is not in the tree.
        assertFailsWith<MerkleTreeException> { pmtLastElementOnly.leafIndex(digestService.hash("10")) }

        val pmtOneElement = PartialMerkleTree.build(merkleTree, listOf(digestService.hash("5")))
        assertEquals(5, pmtOneElement.leafIndex(digestService.hash("5")))
        // The provided hash is not in the tree.
        assertFailsWith<MerkleTreeException> { pmtOneElement.leafIndex(digestService.hash("10")) }

        val pmtAllIncluded = PartialMerkleTree.build(merkleTree, sampleLeaves)
        for (i in 0..19) assertEquals(i, pmtAllIncluded.leafIndex(digestService.hash(i.toString())))
        // The provided hash is not in the tree (using a leaf that didn't exist in the original Merkle tree).
        assertFailsWith<MerkleTreeException> { pmtAllIncluded.leafIndex(digestService.hash("30")) }
    }

    @Test(timeout=300_000)
	fun `building Merkle for reference states only`() {
        fun filtering(elem: Any): Boolean {
            return when (elem) {
                is ReferenceStateRef -> true
                else -> false
            }
        }

        val ftx = testTx.buildFilteredTransaction(Predicate(::filtering))

        assertEquals(1, ftx.filteredComponentGroups.size)
        assertEquals(0, ftx.inputs.size)
        assertEquals(1, ftx.references.size)
        ftx.verify()
    }
}
