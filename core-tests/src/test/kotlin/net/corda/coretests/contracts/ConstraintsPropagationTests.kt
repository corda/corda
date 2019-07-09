package net.corda.coretests.contracts

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.contracts.*
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.SecureHash.Companion.allOnesHash
import net.corda.core.crypto.SecureHash.Companion.zeroHash
import net.corda.core.crypto.SignableData
import net.corda.core.crypto.SignatureMetadata
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.canBeTransitionedFrom
import net.corda.core.internal.inputStream
import net.corda.core.internal.toPath
import net.corda.core.node.NotaryInfo
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.WireTransaction
import net.corda.finance.POUNDS
import net.corda.finance.`issued by`
import net.corda.finance.contracts.asset.Cash
import net.corda.node.services.api.IdentityServiceInternal
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.core.TestIdentity
import net.corda.testing.core.internal.ContractJarTestUtils
import net.corda.testing.core.internal.JarSignatureTestUtils.generateKey
import net.corda.testing.core.internal.SelfCleaningDir
import net.corda.testing.internal.MockCordappProvider
import net.corda.testing.node.MockServices
import net.corda.testing.node.ledger
import org.junit.*
import java.security.PublicKey
import java.util.jar.Attributes
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConstraintsPropagationTests {

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    private companion object {
        val DUMMY_NOTARY = TestIdentity(DUMMY_NOTARY_NAME, 20).party
        val ALICE = TestIdentity(CordaX500Name("ALICE", "London", "GB"))
        val ALICE_PARTY get() = ALICE.party
        val ALICE_PUBKEY get() = ALICE.publicKey
        val BOB = TestIdentity(CordaX500Name("BOB", "London", "GB"))
        val BOB_PARTY get() = BOB.party
        val BOB_PUBKEY get() = BOB.publicKey
        const val noPropagationContractClassName = "net.corda.coretests.contracts.NoPropagationContract"
        const val propagatingContractClassName = "net.corda.core.contracts.PropagationContract"

        private lateinit var keyStoreDir: SelfCleaningDir
        private lateinit var hashToSignatureConstraintsKey: PublicKey

        @BeforeClass
        @JvmStatic
        fun setUpBeforeClass() {
            keyStoreDir = SelfCleaningDir()
            hashToSignatureConstraintsKey = keyStoreDir.path.generateKey("testAlias", "testPassword", ALICE_NAME.toString())
        }

        @AfterClass
        @JvmStatic
        fun cleanUpAfterClass() {
            keyStoreDir.close()
        }
    }

    private lateinit var ledgerServices: MockServices

    @Before
    fun setUp() {
        ledgerServices = object : MockServices(
                cordappPackages = listOf("net.corda.finance.contracts.asset"),
                initialIdentity = ALICE,
                identityService = mock<IdentityServiceInternal>().also {
                    doReturn(ALICE_PARTY).whenever(it).partyFromKey(ALICE_PUBKEY)
                    doReturn(BOB_PARTY).whenever(it).partyFromKey(BOB_PUBKEY)
                },
                networkParameters = testNetworkParameters(minimumPlatformVersion = 4)
                        .copy(whitelistedContractImplementations = mapOf(
                                Cash.PROGRAM_ID to listOf(SecureHash.zeroHash, SecureHash.allOnesHash),
                                noPropagationContractClassName to listOf(SecureHash.zeroHash)),
                                notaries = listOf(NotaryInfo(DUMMY_NOTARY, true)))
        ) {
            override fun loadContractAttachment(stateRef: StateRef) = servicesForResolution.loadContractAttachment(stateRef)
        }
    }

    @Test
    fun `Happy path with the HashConstraint`() {
        ledgerServices.ledger(DUMMY_NOTARY) {
            ledgerServices.recordTransaction(transaction {
                attachment(Cash.PROGRAM_ID, SecureHash.allOnesHash)
                output(Cash.PROGRAM_ID, "c1", DUMMY_NOTARY, null, HashAttachmentConstraint(allOnesHash), Cash.State(1000.POUNDS `issued by` ALICE_PARTY.ref(1), ALICE_PARTY))
                command(ALICE_PUBKEY, Cash.Commands.Issue())
                verifies()
            })
            transaction {
                attachment(Cash.PROGRAM_ID, SecureHash.allOnesHash)
                input("c1")
                output(Cash.PROGRAM_ID, "c2", DUMMY_NOTARY, null, HashAttachmentConstraint(allOnesHash), Cash.State(1000.POUNDS `issued by` ALICE_PARTY.ref(1), BOB_PARTY))
                command(ALICE_PUBKEY, Cash.Commands.Move())
                verifies()
            }
        }
    }

    @Test
    @Ignore    // TODO(mike): rework
    fun `Happy path for Hash to Signature Constraint migration`() {
        val cordapps = (ledgerServices.cordappProvider as MockCordappProvider).cordapps
        val cordappAttachmentIds =
            cordapps.map { cordapp ->
                val unsignedAttId =
                    cordapp.jarPath.toPath().inputStream().use { unsignedJarStream ->
                        ledgerServices.attachments.importContractAttachment(cordapp.contractClassNames,  "rpc", unsignedJarStream,null)
                    }
                val jarAndSigner = ContractJarTestUtils.signContractJar(cordapp.jarPath, copyFirst = true, keyStoreDir = keyStoreDir.path)
                val signedJar = jarAndSigner.first
                val signedAttId =
                    signedJar.inputStream().use { signedJarStream ->
                        ledgerServices.attachments.importContractAttachment(cordapp.contractClassNames,  "rpc", signedJarStream,null, listOf(jarAndSigner.second))
                    }
                Pair(unsignedAttId, signedAttId)
            }

        val unsignedAttachmentId = cordappAttachmentIds.first().first
        println("Unsigned: $unsignedAttachmentId")
        val signedAttachmentId = cordappAttachmentIds.first().second
        println("Signed: $signedAttachmentId")

        ledgerServices.ledger(DUMMY_NOTARY) {
            ledgerServices.recordTransaction(
                    unverifiedTransaction {
                attachment(Cash.PROGRAM_ID, unsignedAttachmentId)
                output(Cash.PROGRAM_ID, "c1", DUMMY_NOTARY, null, HashAttachmentConstraint(unsignedAttachmentId), Cash.State(1000.POUNDS `issued by` ALICE_PARTY.ref(1), ALICE_PARTY))
                command(ALICE_PUBKEY, Cash.Commands.Issue())
            })
            unverifiedTransaction {
                attachment(Cash.PROGRAM_ID, signedAttachmentId)
                input("c1")
                output(Cash.PROGRAM_ID, "c2", DUMMY_NOTARY, null, SignatureAttachmentConstraint(hashToSignatureConstraintsKey), Cash.State(1000.POUNDS `issued by` ALICE_PARTY.ref(1), BOB_PARTY))
                command(ALICE_PUBKEY, Cash.Commands.Move())
                verifies()
            }
        }
    }

    @Test
    fun `Fail early in the TransactionBuilder when attempting to change the hash of the HashConstraint on the spending transaction`() {
        ledgerServices.ledger(DUMMY_NOTARY) {
            transaction {
                attachment(Cash.PROGRAM_ID, SecureHash.zeroHash)
                output(Cash.PROGRAM_ID, "c1", DUMMY_NOTARY, null, HashAttachmentConstraint(zeroHash), Cash.State(1000.POUNDS `issued by` ALICE_PARTY.ref(1), ALICE_PARTY))
                command(ALICE_PUBKEY, Cash.Commands.Issue())
                verifies()
            }
            assertFailsWith<IllegalArgumentException> {
                transaction {
                    attachment(Cash.PROGRAM_ID, SecureHash.allOnesHash)
                    input("c1")
                    output(Cash.PROGRAM_ID, "c2", DUMMY_NOTARY, null, HashAttachmentConstraint(allOnesHash), Cash.State(1000.POUNDS `issued by` ALICE_PARTY.ref(1), BOB_PARTY))
                    command(ALICE_PUBKEY, Cash.Commands.Move())
                    verifies()
                }
            }
        }
    }

    @Test
    fun `Transaction validation fails, when constraints do not propagate correctly`() {
        ledgerServices.ledger(DUMMY_NOTARY) {
            ledgerServices.recordTransaction(transaction {
                attachment(Cash.PROGRAM_ID, SecureHash.zeroHash)
                output(Cash.PROGRAM_ID, "c1", DUMMY_NOTARY, null, HashAttachmentConstraint(zeroHash), Cash.State(1000.POUNDS `issued by` ALICE_PARTY.ref(1), ALICE_PARTY))
                command(ALICE_PUBKEY, Cash.Commands.Issue())
                verifies()
            })
            ledgerServices.recordTransaction(transaction {
                attachment(Cash.PROGRAM_ID, SecureHash.zeroHash)
                input("c1")
                output(Cash.PROGRAM_ID, "c2", DUMMY_NOTARY, null, WhitelistedByZoneAttachmentConstraint, Cash.State(1000.POUNDS `issued by` ALICE_PARTY.ref(1), BOB_PARTY))
                command(ALICE_PUBKEY, Cash.Commands.Move())
                failsWith("are not propagated correctly")
            })
            ledgerServices.recordTransaction(transaction {
                attachment(Cash.PROGRAM_ID, SecureHash.zeroHash)
                input("c1")
                output(Cash.PROGRAM_ID, "c3", DUMMY_NOTARY, null, SignatureAttachmentConstraint(ALICE_PUBKEY), Cash.State(1000.POUNDS `issued by` ALICE_PARTY.ref(1), BOB_PARTY))
                command(ALICE_PUBKEY, Cash.Commands.Move())
                fails()
            })
            transaction {
                attachment(Cash.PROGRAM_ID, SecureHash.zeroHash)
                input("c1")
                output(Cash.PROGRAM_ID, "c4", DUMMY_NOTARY, null, AlwaysAcceptAttachmentConstraint, Cash.State(1000.POUNDS `issued by` ALICE_PARTY.ref(1), BOB_PARTY))
                command(ALICE_PUBKEY, Cash.Commands.Move())
                fails()
            }
        }
    }

    @Test
    fun `When the constraint of the output state is a valid transition from the input state, transaction validation works`() {
        ledgerServices.ledger(DUMMY_NOTARY) {
            ledgerServices.recordTransaction(transaction {
                attachment(Cash.PROGRAM_ID, SecureHash.zeroHash)
                output(Cash.PROGRAM_ID, "c1", DUMMY_NOTARY, null, WhitelistedByZoneAttachmentConstraint, Cash.State(1000.POUNDS `issued by` ALICE_PARTY.ref(1), ALICE_PARTY))
                command(ALICE_PUBKEY, Cash.Commands.Issue())
                verifies()
            })
            transaction {
                attachment(Cash.PROGRAM_ID, SecureHash.zeroHash)
                input("c1")
                output(Cash.PROGRAM_ID, "c2", DUMMY_NOTARY, null, HashAttachmentConstraint(zeroHash), Cash.State(1000.POUNDS `issued by` ALICE_PARTY.ref(1), BOB_PARTY))
                command(ALICE_PUBKEY, Cash.Commands.Move())
                verifies()
            }
        }
    }

    @Test
    fun `Switching from the WhitelistConstraint to the Signature Constraint is possible if the attachment satisfies both constraints, and the signature constraint inherits all jar signatures`() {

        ledgerServices.ledger(DUMMY_NOTARY) {
            ledgerServices.recordTransaction(transaction {
                attachment(Cash.PROGRAM_ID, SecureHash.zeroHash)
                output(Cash.PROGRAM_ID, "w1", DUMMY_NOTARY, null, WhitelistedByZoneAttachmentConstraint, Cash.State(1000.POUNDS `issued by` ALICE_PARTY.ref(1), ALICE_PARTY))
                command(ALICE_PUBKEY, Cash.Commands.Issue())
                verifies()
            })

            // the attachment is signed
            transaction {
                attachment(Cash.PROGRAM_ID, SecureHash.allOnesHash, listOf(hashToSignatureConstraintsKey))
                input("w1")
                output(Cash.PROGRAM_ID, "w2", DUMMY_NOTARY, null, SignatureAttachmentConstraint(hashToSignatureConstraintsKey), Cash.State(1000.POUNDS `issued by` ALICE_PARTY.ref(1), BOB_PARTY))
                command(ALICE_PUBKEY, Cash.Commands.Move())
                verifies()
            }
        }
    }

    @Test
    fun `Switching from the WhitelistConstraint to the Signature Constraint fails if the signature constraint does not inherit all jar signatures`() {
        ledgerServices.ledger(DUMMY_NOTARY) {
            ledgerServices.recordTransaction(transaction {
                attachment(Cash.PROGRAM_ID, SecureHash.zeroHash)
                output(Cash.PROGRAM_ID, "w1", DUMMY_NOTARY, null, WhitelistedByZoneAttachmentConstraint, Cash.State(1000.POUNDS `issued by` ALICE_PARTY.ref(1), ALICE_PARTY))
                command(ALICE_PUBKEY, Cash.Commands.Issue())
                verifies()
            })
            // the attachment is not signed
            transaction {
                attachment(Cash.PROGRAM_ID, SecureHash.zeroHash)
                input("w1")
                output(Cash.PROGRAM_ID, "w2", DUMMY_NOTARY, null, SignatureAttachmentConstraint(ALICE_PUBKEY), Cash.State(1000.POUNDS `issued by` ALICE_PARTY.ref(1), BOB_PARTY))
                command(ALICE_PUBKEY, Cash.Commands.Move())
                // Note that it fails after the constraints propagation check, because the attachment is not signed.
                fails()
            }
        }
    }

    @Test
    fun `On contract annotated with NoConstraintPropagation there is no platform check for propagation, but the transaction builder can't use the AutomaticPlaceholderConstraint`() {
        ledgerServices.ledger(DUMMY_NOTARY) {
            ledgerServices.recordTransaction(transaction {
                attachment(noPropagationContractClassName, SecureHash.zeroHash)
                output(noPropagationContractClassName, "c1", DUMMY_NOTARY, null, HashAttachmentConstraint(zeroHash), NoPropagationContractState())
                command(ALICE_PUBKEY, NoPropagationContract.Create())
                verifies()
            })
            ledgerServices.recordTransaction(transaction {
                attachment(noPropagationContractClassName, SecureHash.zeroHash)
                input("c1")
                output(noPropagationContractClassName, "c2", DUMMY_NOTARY, null, WhitelistedByZoneAttachmentConstraint, NoPropagationContractState())
                command(ALICE_PUBKEY, NoPropagationContract.Create())
                verifies()
            })
            assertFailsWith<IllegalArgumentException> {
                transaction {
                    attachment(noPropagationContractClassName, SecureHash.zeroHash)
                    input("c1")
                    output(noPropagationContractClassName, "c3", DUMMY_NOTARY, null, AutomaticPlaceholderConstraint, NoPropagationContractState())
                    command(ALICE_PUBKEY, NoPropagationContract.Create())
                    verifies()
                }
            }
        }
    }

    @Test
    fun `Signature Constraints canBeTransitionedFrom Hash Constraints behaves as expected`() {

        // unsigned attachment (for hash constraint)
        val attachmentUnsigned = mock<ContractAttachment>()
        val attachmentIdUnsigned = allOnesHash
        whenever(attachmentUnsigned.contract).thenReturn(propagatingContractClassName)

        // signed attachment (for signature constraint)
        val attachmentSigned = mock<ContractAttachment>()
        val attachmentIdSigned = zeroHash
        whenever(attachmentSigned.signerKeys).thenReturn(listOf(ALICE_PARTY.owningKey))
        whenever(attachmentSigned.allContracts).thenReturn(setOf(propagatingContractClassName))

        // network parameters
        val netParams = testNetworkParameters(minimumPlatformVersion = 4,
                packageOwnership = mapOf( "net.corda.core.contracts" to ALICE_PARTY.owningKey))

        ledgerServices.attachments.importContractAttachment(attachmentIdSigned, attachmentSigned)
        ledgerServices.attachments.importContractAttachment(attachmentIdUnsigned, attachmentUnsigned)

        // propagation check
        // TODO - enable once the logic to transition has been added.
        assertFalse(SignatureAttachmentConstraint(ALICE_PUBKEY).canBeTransitionedFrom(HashAttachmentConstraint(allOnesHash), attachmentSigned))
    }

    @Test
    fun `Attachment canBeTransitionedFrom behaves as expected`() {

        // signed attachment (for signature constraint)
        val attachment = mock<ContractAttachment>()
        whenever(attachment.signerKeys).thenReturn(listOf(ALICE_PARTY.owningKey))
        whenever(attachment.allContracts).thenReturn(setOf(propagatingContractClassName))

        // Exhaustive positive check
        assertTrue(HashAttachmentConstraint(SecureHash.randomSHA256()).canBeTransitionedFrom(SignatureAttachmentConstraint(ALICE_PUBKEY), attachment))
        assertTrue(HashAttachmentConstraint(SecureHash.randomSHA256()).canBeTransitionedFrom(WhitelistedByZoneAttachmentConstraint, attachment))

        assertTrue(SignatureAttachmentConstraint(ALICE_PUBKEY).canBeTransitionedFrom(SignatureAttachmentConstraint(ALICE_PUBKEY), attachment))
        assertTrue(SignatureAttachmentConstraint(ALICE_PUBKEY).canBeTransitionedFrom(WhitelistedByZoneAttachmentConstraint, attachment))

        assertTrue(WhitelistedByZoneAttachmentConstraint.canBeTransitionedFrom(WhitelistedByZoneAttachmentConstraint, attachment))

        assertTrue(AlwaysAcceptAttachmentConstraint.canBeTransitionedFrom(AlwaysAcceptAttachmentConstraint, attachment))

        // Exhaustive negative check
        assertFalse(HashAttachmentConstraint(SecureHash.randomSHA256()).canBeTransitionedFrom(AlwaysAcceptAttachmentConstraint, attachment))
        assertFalse(WhitelistedByZoneAttachmentConstraint.canBeTransitionedFrom(AlwaysAcceptAttachmentConstraint, attachment))
        assertFalse(SignatureAttachmentConstraint(ALICE_PUBKEY).canBeTransitionedFrom(AlwaysAcceptAttachmentConstraint, attachment))

        assertFalse(HashAttachmentConstraint(SecureHash.randomSHA256()).canBeTransitionedFrom(HashAttachmentConstraint(SecureHash.randomSHA256()), attachment))

        assertFalse(WhitelistedByZoneAttachmentConstraint.canBeTransitionedFrom(HashAttachmentConstraint(SecureHash.randomSHA256()), attachment))
        assertFalse(WhitelistedByZoneAttachmentConstraint.canBeTransitionedFrom(SignatureAttachmentConstraint(ALICE_PUBKEY), attachment))

        assertFalse(SignatureAttachmentConstraint(ALICE_PUBKEY).canBeTransitionedFrom(HashAttachmentConstraint(SecureHash.randomSHA256()), attachment))
        assertFalse(SignatureAttachmentConstraint(BOB_PUBKEY).canBeTransitionedFrom(WhitelistedByZoneAttachmentConstraint, attachment))
        assertFalse(SignatureAttachmentConstraint(BOB_PUBKEY).canBeTransitionedFrom(SignatureAttachmentConstraint(ALICE_PUBKEY), attachment))

        assertFalse(AlwaysAcceptAttachmentConstraint.canBeTransitionedFrom(SignatureAttachmentConstraint(ALICE_PUBKEY), attachment))
        assertFalse(AlwaysAcceptAttachmentConstraint.canBeTransitionedFrom(WhitelistedByZoneAttachmentConstraint, attachment))
        assertFalse(AlwaysAcceptAttachmentConstraint.canBeTransitionedFrom(HashAttachmentConstraint(SecureHash.randomSHA256()), attachment))

        // Fail when encounter a AutomaticPlaceholderConstraint
        assertFailsWith<IllegalArgumentException> {
            HashAttachmentConstraint(SecureHash.randomSHA256())
                    .canBeTransitionedFrom(AutomaticPlaceholderConstraint, attachment)
        }
        assertFailsWith<IllegalArgumentException> { AutomaticPlaceholderConstraint.canBeTransitionedFrom(AutomaticPlaceholderConstraint, attachment) }
    }

    private fun MockServices.recordTransaction(wireTransaction: WireTransaction){
        val nodeKey = ALICE_PUBKEY
        val sigs = listOf(keyManagementService.sign(
                SignableData(wireTransaction.id, SignatureMetadata(4, Crypto.findSignatureScheme(nodeKey).schemeNumberID)), nodeKey))
        recordTransactions(SignedTransaction(wireTransaction, sigs))
    }
    @Test
    fun `Input state contract version may be incompatible with lower version`() {
        ledgerServices.ledger(DUMMY_NOTARY) {
            ledgerServices.recordTransaction(transaction {
                attachment(Cash.PROGRAM_ID, SecureHash.allOnesHash, listOf(hashToSignatureConstraintsKey), mapOf(Attributes.Name.IMPLEMENTATION_VERSION.toString() to  "2"))
                output(Cash.PROGRAM_ID, "c1", DUMMY_NOTARY, null, SignatureAttachmentConstraint(hashToSignatureConstraintsKey), Cash.State(1000.POUNDS `issued by` ALICE_PARTY.ref(1), ALICE_PARTY))
                command(ALICE_PUBKEY, Cash.Commands.Issue())
                verifies()
            })
            transaction {
                attachment(Cash.PROGRAM_ID, SecureHash.zeroHash, listOf(hashToSignatureConstraintsKey), mapOf(Attributes.Name.IMPLEMENTATION_VERSION.toString() to  "1"))
                input("c1")
                output(Cash.PROGRAM_ID, "c2", DUMMY_NOTARY, null, SignatureAttachmentConstraint(hashToSignatureConstraintsKey), Cash.State(1000.POUNDS `issued by` ALICE_PARTY.ref(1), BOB_PARTY))
                command(ALICE_PUBKEY, Cash.Commands.Move())
                verifies()
            }
        }
    }

    @Test
    fun `Input state contract version is compatible with the same version`() {
        ledgerServices.ledger(DUMMY_NOTARY) {
            ledgerServices.recordTransaction(transaction {
                attachment(Cash.PROGRAM_ID, SecureHash.allOnesHash, listOf(hashToSignatureConstraintsKey), mapOf(Attributes.Name.IMPLEMENTATION_VERSION.toString() to  "3"))
                output(Cash.PROGRAM_ID, "c1", DUMMY_NOTARY, null, SignatureAttachmentConstraint(hashToSignatureConstraintsKey), Cash.State(1000.POUNDS `issued by` ALICE_PARTY.ref(1), ALICE_PARTY))
                command(ALICE_PUBKEY, Cash.Commands.Issue())
                verifies()
            })
            transaction {
                attachment(Cash.PROGRAM_ID, SecureHash.zeroHash, listOf(hashToSignatureConstraintsKey), mapOf(Attributes.Name.IMPLEMENTATION_VERSION.toString() to  "3"))
                input("c1")
                output(Cash.PROGRAM_ID, "c2", DUMMY_NOTARY, null, SignatureAttachmentConstraint(hashToSignatureConstraintsKey), Cash.State(1000.POUNDS `issued by` ALICE_PARTY.ref(1), BOB_PARTY))
                command(ALICE_PUBKEY, Cash.Commands.Move())
                verifies()
            }
        }
    }

    @Test
    fun `Input state contract version is compatible with higher version`() {
        ledgerServices.ledger(DUMMY_NOTARY) {
            ledgerServices.recordTransaction(transaction {
                attachment(Cash.PROGRAM_ID, SecureHash.allOnesHash, listOf(hashToSignatureConstraintsKey), mapOf(Attributes.Name.IMPLEMENTATION_VERSION.toString() to  "1"))
                output(Cash.PROGRAM_ID, "c1", DUMMY_NOTARY, null, SignatureAttachmentConstraint(hashToSignatureConstraintsKey), Cash.State(1000.POUNDS `issued by` ALICE_PARTY.ref(1), ALICE_PARTY))
                command(ALICE_PUBKEY, Cash.Commands.Issue())
                verifies()
            })
            transaction {
                attachment(Cash.PROGRAM_ID, SecureHash.zeroHash, listOf(hashToSignatureConstraintsKey), mapOf(Attributes.Name.IMPLEMENTATION_VERSION.toString() to  "2"))
                input("c1")
                output(Cash.PROGRAM_ID, "c2", DUMMY_NOTARY, null, SignatureAttachmentConstraint(hashToSignatureConstraintsKey), Cash.State(1000.POUNDS `issued by` ALICE_PARTY.ref(1), BOB_PARTY))
                command(ALICE_PUBKEY, Cash.Commands.Move())
                verifies()
            }
        }
    }

    @Test
    fun `Input states contract version may be lower that current contract version`() {
        ledgerServices.ledger(DUMMY_NOTARY) {
            ledgerServices.recordTransaction(transaction {
                attachment(Cash.PROGRAM_ID, SecureHash.allOnesHash, listOf(hashToSignatureConstraintsKey), mapOf(Attributes.Name.IMPLEMENTATION_VERSION.toString() to  "1"))
                output(Cash.PROGRAM_ID, "c1", DUMMY_NOTARY, null, SignatureAttachmentConstraint(hashToSignatureConstraintsKey), Cash.State(1000.POUNDS `issued by` ALICE_PARTY.ref(1), ALICE_PARTY))
                command(ALICE_PUBKEY, Cash.Commands.Issue())
                verifies()
            })
            ledgerServices.recordTransaction(transaction {
                attachment(Cash.PROGRAM_ID, SecureHash.zeroHash, listOf(hashToSignatureConstraintsKey), mapOf(Attributes.Name.IMPLEMENTATION_VERSION.toString() to  "2"))
                output(Cash.PROGRAM_ID, "c2", DUMMY_NOTARY, null, SignatureAttachmentConstraint(hashToSignatureConstraintsKey), Cash.State(1000.POUNDS `issued by` ALICE_PARTY.ref(1), ALICE_PARTY))
                command(ALICE_PUBKEY, Cash.Commands.Issue())
                verifies()
            })
            transaction {
                input("c1")
                input("c2")
                output(Cash.PROGRAM_ID, "c3", DUMMY_NOTARY, null, SignatureAttachmentConstraint(hashToSignatureConstraintsKey), Cash.State(2000.POUNDS `issued by` ALICE_PARTY.ref(1), BOB_PARTY))
                command(ALICE_PUBKEY, Cash.Commands.Move())
                verifies()
            }
        }
    }

    @Test
    fun `Input state with contract version can be downgraded to no version`() {
        ledgerServices.ledger(DUMMY_NOTARY) {
            ledgerServices.recordTransaction(transaction {
                attachment(Cash.PROGRAM_ID, SecureHash.allOnesHash, listOf(hashToSignatureConstraintsKey), mapOf(Attributes.Name.IMPLEMENTATION_VERSION.toString() to  "2"))
                output(Cash.PROGRAM_ID, "c1", DUMMY_NOTARY, null, SignatureAttachmentConstraint(hashToSignatureConstraintsKey), Cash.State(1000.POUNDS `issued by` ALICE_PARTY.ref(1), ALICE_PARTY))
                command(ALICE_PUBKEY, Cash.Commands.Issue())
                verifies()
            })
            transaction {
                attachment(Cash.PROGRAM_ID, SecureHash.zeroHash, listOf(hashToSignatureConstraintsKey), emptyMap())
                input("c1")
                output(Cash.PROGRAM_ID, "c2", DUMMY_NOTARY, null, SignatureAttachmentConstraint(hashToSignatureConstraintsKey), Cash.State(1000.POUNDS `issued by` ALICE_PARTY.ref(1), BOB_PARTY))
                command(ALICE_PUBKEY, Cash.Commands.Move())
                verifies()
            }
        }
    }

    @Test
    fun `Input state without contract version is compatible with any version`() {
        ledgerServices.ledger(DUMMY_NOTARY) {
            ledgerServices.recordTransaction(transaction {
                attachment(Cash.PROGRAM_ID, SecureHash.allOnesHash, listOf(hashToSignatureConstraintsKey), emptyMap())
                output(Cash.PROGRAM_ID, "c1", DUMMY_NOTARY, null, SignatureAttachmentConstraint(hashToSignatureConstraintsKey), Cash.State(1000.POUNDS `issued by` ALICE_PARTY.ref(1), ALICE_PARTY))
                command(ALICE_PUBKEY, Cash.Commands.Issue())
                verifies()
            })
            transaction {
                attachment(Cash.PROGRAM_ID, SecureHash.zeroHash, listOf(hashToSignatureConstraintsKey), mapOf(Attributes.Name.IMPLEMENTATION_VERSION.toString() to  "2"))
                input("c1")
                output(Cash.PROGRAM_ID, "c2", DUMMY_NOTARY, null, SignatureAttachmentConstraint(hashToSignatureConstraintsKey), Cash.State(1000.POUNDS `issued by` ALICE_PARTY.ref(1), BOB_PARTY))
                command(ALICE_PUBKEY, Cash.Commands.Move())
                verifies()
            }
        }
    }
}

@BelongsToContract(NoPropagationContract::class)
class NoPropagationContractState : ContractState {
    override val participants: List<AbstractParty>
        get() = emptyList()
}

@NoConstraintPropagation
class NoPropagationContract : Contract {
    interface Commands : CommandData
    class Create : Commands

    override fun verify(tx: LedgerTransaction) {
        //do nothing
    }
}
