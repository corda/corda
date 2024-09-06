package net.corda.coretests.contracts

import net.corda.core.contracts.AlwaysAcceptAttachmentConstraint
import net.corda.core.contracts.AutomaticPlaceholderConstraint
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.ContractAttachment
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.HashAttachmentConstraint
import net.corda.core.contracts.NoConstraintPropagation
import net.corda.core.contracts.RotatedKeysData
import net.corda.core.contracts.SignatureAttachmentConstraint
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.WhitelistedByZoneAttachmentConstraint
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.SecureHash.Companion.allOnesHash
import net.corda.core.crypto.SecureHash.Companion.zeroHash
import net.corda.core.crypto.SignableData
import net.corda.core.crypto.SignatureMetadata
import net.corda.core.crypto.sign
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.canBeTransitionedFrom
import net.corda.core.internal.read
import net.corda.core.internal.requireSupportedHashType
import net.corda.core.node.NotaryInfo
import net.corda.core.node.services.IdentityService
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.WireTransaction
import net.corda.finance.POUNDS
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.`issued by`
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
import org.junit.AfterClass
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
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
        val DUMMY_NOTARY_IDENTITY = TestIdentity(DUMMY_NOTARY_NAME, 20)
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
                identityService = mock<IdentityService>().also {
                    doReturn(ALICE_PARTY).whenever(it).partyFromKey(ALICE_PUBKEY)
                    doReturn(BOB_PARTY).whenever(it).partyFromKey(BOB_PUBKEY)
                },
                networkParameters = testNetworkParameters(minimumPlatformVersion = 4)
                        .copy(whitelistedContractImplementations = mapOf(
                                Cash.PROGRAM_ID to listOf(zeroHash, allOnesHash),
                                noPropagationContractClassName to listOf(zeroHash)),
                                notaries = listOf(NotaryInfo(DUMMY_NOTARY, true)))
        ) {
            override fun loadContractAttachment(stateRef: StateRef) = servicesForResolution.loadContractAttachment(stateRef)
        }
    }

    @Test(timeout=300_000)
	fun `Happy path with the HashConstraint`() {
        ledgerServices.ledger(DUMMY_NOTARY) {
            ledgerServices.recordTransaction(transaction {
                attachment(Cash.PROGRAM_ID, allOnesHash)
                output(Cash.PROGRAM_ID, "c1", DUMMY_NOTARY, null, HashAttachmentConstraint(allOnesHash), Cash.State(1000.POUNDS `issued by` ALICE_PARTY.ref(1), ALICE_PARTY))
                command(ALICE_PUBKEY, Cash.Commands.Issue())
                verifies()
            })
            transaction {
                attachment(Cash.PROGRAM_ID, allOnesHash)
                input("c1")
                output(Cash.PROGRAM_ID, "c2", DUMMY_NOTARY, null, HashAttachmentConstraint(allOnesHash), Cash.State(1000.POUNDS `issued by` ALICE_PARTY.ref(1), BOB_PARTY))
                command(ALICE_PUBKEY, Cash.Commands.Move())
                verifies()
            }
        }
    }

    @Test(timeout=300_000)
@Ignore    // TODO(mike): rework
    fun `Happy path for Hash to Signature Constraint migration`() {
        val cordapps = (ledgerServices.cordappProvider as MockCordappProvider).cordapps
        val cordappAttachmentIds =
                cordapps.map { cordapp ->
                    val unsignedAttId =
                            cordapp.jarPath.openStream().use { unsignedJarStream ->
                                ledgerServices.attachments.importContractAttachment(cordapp.contractClassNames, "rpc", unsignedJarStream, null)
                            }
                    val jarAndSigner = ContractJarTestUtils.signContractJar(cordapp.jarPath, copyFirst = true, keyStoreDir = keyStoreDir.path)
                    val signedJar = jarAndSigner.first
                    val signedAttId =
                            signedJar.read { signedJarStream ->
                                ledgerServices.attachments.importContractAttachment(cordapp.contractClassNames, "rpc", signedJarStream, null, listOf(jarAndSigner.second))
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

    @Test(timeout=300_000)
	fun `Fail early in the TransactionBuilder when attempting to change the hash of the HashConstraint on the spending transaction`() {
        ledgerServices.ledger(DUMMY_NOTARY) {
            transaction {
                attachment(Cash.PROGRAM_ID, zeroHash)
                output(Cash.PROGRAM_ID, "c1", DUMMY_NOTARY, null, HashAttachmentConstraint(zeroHash), Cash.State(1000.POUNDS `issued by` ALICE_PARTY.ref(1), ALICE_PARTY))
                command(ALICE_PUBKEY, Cash.Commands.Issue())
                verifies()
            }
            assertFailsWith<IllegalArgumentException> {
                transaction {
                    attachment(Cash.PROGRAM_ID, allOnesHash)
                    input("c1")
                    output(Cash.PROGRAM_ID, "c2", DUMMY_NOTARY, null, HashAttachmentConstraint(allOnesHash), Cash.State(1000.POUNDS `issued by` ALICE_PARTY.ref(1), BOB_PARTY))
                    command(ALICE_PUBKEY, Cash.Commands.Move())
                    verifies()
                }
            }
        }
    }

    @Test(timeout=300_000)
	fun `Transaction validation fails, when constraints do not propagate correctly`() {
        ledgerServices.ledger(DUMMY_NOTARY) {
            ledgerServices.recordTransaction(transaction {
                attachment(Cash.PROGRAM_ID, zeroHash)
                output(Cash.PROGRAM_ID, "c1", DUMMY_NOTARY, null, HashAttachmentConstraint(zeroHash), Cash.State(1000.POUNDS `issued by` ALICE_PARTY.ref(1), ALICE_PARTY))
                command(ALICE_PUBKEY, Cash.Commands.Issue())
                verifies()
            })
            ledgerServices.recordTransaction(transaction {
                attachment(Cash.PROGRAM_ID, zeroHash)
                input("c1")
                output(Cash.PROGRAM_ID, "c2", DUMMY_NOTARY, null, WhitelistedByZoneAttachmentConstraint, Cash.State(1000.POUNDS `issued by` ALICE_PARTY.ref(1), BOB_PARTY))
                command(ALICE_PUBKEY, Cash.Commands.Move())
                failsWith("are not propagated correctly")
            })
            ledgerServices.recordTransaction(transaction {
                attachment(Cash.PROGRAM_ID, zeroHash)
                input("c1")
                output(Cash.PROGRAM_ID, "c3", DUMMY_NOTARY, null, SignatureAttachmentConstraint(ALICE_PUBKEY), Cash.State(1000.POUNDS `issued by` ALICE_PARTY.ref(1), BOB_PARTY))
                command(ALICE_PUBKEY, Cash.Commands.Move())
                fails()
            })
            transaction {
                attachment(Cash.PROGRAM_ID, zeroHash)
                input("c1")
                output(Cash.PROGRAM_ID, "c4", DUMMY_NOTARY, null, AlwaysAcceptAttachmentConstraint, Cash.State(1000.POUNDS `issued by` ALICE_PARTY.ref(1), BOB_PARTY))
                command(ALICE_PUBKEY, Cash.Commands.Move())
                fails()
            }
        }
    }

    @Test(timeout=300_000)
	fun `When the constraint of the output state is a valid transition from the input state, transaction validation works`() {
        ledgerServices.ledger(DUMMY_NOTARY) {
            ledgerServices.recordTransaction(transaction {
                attachment(Cash.PROGRAM_ID, zeroHash)
                output(Cash.PROGRAM_ID, "c1", DUMMY_NOTARY, null, WhitelistedByZoneAttachmentConstraint, Cash.State(1000.POUNDS `issued by` ALICE_PARTY.ref(1), ALICE_PARTY))
                command(ALICE_PUBKEY, Cash.Commands.Issue())
                verifies()
            })
            transaction {
                attachment(Cash.PROGRAM_ID, zeroHash)
                input("c1")
                output(Cash.PROGRAM_ID, "c2", DUMMY_NOTARY, null, HashAttachmentConstraint(zeroHash), Cash.State(1000.POUNDS `issued by` ALICE_PARTY.ref(1), BOB_PARTY))
                command(ALICE_PUBKEY, Cash.Commands.Move())
                verifies()
            }
        }
    }

    @Test(timeout=300_000)
	fun `Switching from the WhitelistConstraint to the Signature Constraint is possible if the attachment satisfies both constraints, and the signature constraint inherits all jar signatures`() {

        ledgerServices.ledger(DUMMY_NOTARY) {
            ledgerServices.recordTransaction(transaction {
                attachment(Cash.PROGRAM_ID, zeroHash)
                output(Cash.PROGRAM_ID, "w1", DUMMY_NOTARY, null, WhitelistedByZoneAttachmentConstraint, Cash.State(1000.POUNDS `issued by` ALICE_PARTY.ref(1), ALICE_PARTY))
                command(ALICE_PUBKEY, Cash.Commands.Issue())
                verifies()
            })

            // the attachment is signed
            transaction {
                attachment(Cash.PROGRAM_ID, allOnesHash, listOf(hashToSignatureConstraintsKey))
                input("w1")
                output(Cash.PROGRAM_ID, "w2", DUMMY_NOTARY, null, SignatureAttachmentConstraint(hashToSignatureConstraintsKey), Cash.State(1000.POUNDS `issued by` ALICE_PARTY.ref(1), BOB_PARTY))
                command(ALICE_PUBKEY, Cash.Commands.Move())
                verifies()
            }
        }
    }

    @Test(timeout=300_000)
	fun `Switching from the WhitelistConstraint to the Signature Constraint fails if the signature constraint does not inherit all jar signatures`() {
        ledgerServices.ledger(DUMMY_NOTARY) {
            ledgerServices.recordTransaction(transaction {
                attachment(Cash.PROGRAM_ID, zeroHash)
                output(Cash.PROGRAM_ID, "w1", DUMMY_NOTARY, null, WhitelistedByZoneAttachmentConstraint, Cash.State(1000.POUNDS `issued by` ALICE_PARTY.ref(1), ALICE_PARTY))
                command(ALICE_PUBKEY, Cash.Commands.Issue())
                verifies()
            })
            // the attachment is not signed
            transaction {
                attachment(Cash.PROGRAM_ID, zeroHash)
                input("w1")
                output(Cash.PROGRAM_ID, "w2", DUMMY_NOTARY, null, SignatureAttachmentConstraint(ALICE_PUBKEY), Cash.State(1000.POUNDS `issued by` ALICE_PARTY.ref(1), BOB_PARTY))
                command(ALICE_PUBKEY, Cash.Commands.Move())
                // Note that it fails after the constraints propagation check, because the attachment is not signed.
                fails()
            }
        }
    }

    @Test(timeout=300_000)
	fun `On contract annotated with NoConstraintPropagation there is no platform check for propagation, but the transaction builder can't use the AutomaticPlaceholderConstraint`() {
        ledgerServices.ledger(DUMMY_NOTARY) {
            ledgerServices.recordTransaction(transaction {
                attachment(noPropagationContractClassName, zeroHash)
                output(noPropagationContractClassName, "c1", DUMMY_NOTARY, null, HashAttachmentConstraint(zeroHash), NoPropagationContractState())
                command(ALICE_PUBKEY, NoPropagationContract.Create())
                verifies()
            })
            ledgerServices.recordTransaction(transaction {
                attachment(noPropagationContractClassName, zeroHash)
                input("c1")
                output(noPropagationContractClassName, "c2", DUMMY_NOTARY, null, WhitelistedByZoneAttachmentConstraint, NoPropagationContractState())
                command(ALICE_PUBKEY, NoPropagationContract.Create())
                verifies()
            })
            assertFailsWith<IllegalArgumentException> {
                transaction {
                    attachment(noPropagationContractClassName, zeroHash)
                    input("c1")
                    output(noPropagationContractClassName, "c3", DUMMY_NOTARY, null, AutomaticPlaceholderConstraint, NoPropagationContractState())
                    command(ALICE_PUBKEY, NoPropagationContract.Create())
                    verifies()
                }
            }
        }
    }

    @Test(timeout=300_000)
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
        testNetworkParameters(minimumPlatformVersion = 4,
                packageOwnership = mapOf("net.corda.core.contracts" to ALICE_PARTY.owningKey))

        ledgerServices.attachments.importContractAttachment(attachmentIdSigned, attachmentSigned)
        ledgerServices.attachments.importContractAttachment(attachmentIdUnsigned, attachmentUnsigned)

        // propagation check
        // TODO - enable once the logic to transition has been added.
        assertFalse(SignatureAttachmentConstraint(ALICE_PUBKEY).canBeTransitionedFrom(HashAttachmentConstraint(allOnesHash), attachmentSigned, RotatedKeysData()))
    }

    @Test(timeout=300_000)
	fun `Attachment canBeTransitionedFrom behaves as expected`() {

        // signed attachment (for signature constraint)
        val rotatedKeysData = RotatedKeysData()
        val attachment = mock<ContractAttachment>()
        whenever(attachment.signerKeys).thenReturn(listOf(ALICE_PARTY.owningKey))
        whenever(attachment.allContracts).thenReturn(setOf(propagatingContractClassName))

        // Exhaustive positive check
        assertTrue(HashAttachmentConstraint(SecureHash.randomSHA256()).canBeTransitionedFrom(SignatureAttachmentConstraint(ALICE_PUBKEY), attachment, rotatedKeysData))
        assertTrue(HashAttachmentConstraint(SecureHash.randomSHA256()).canBeTransitionedFrom(WhitelistedByZoneAttachmentConstraint, attachment, rotatedKeysData))

        assertTrue(SignatureAttachmentConstraint(ALICE_PUBKEY).canBeTransitionedFrom(SignatureAttachmentConstraint(ALICE_PUBKEY), attachment, rotatedKeysData))
        assertTrue(SignatureAttachmentConstraint(ALICE_PUBKEY).canBeTransitionedFrom(WhitelistedByZoneAttachmentConstraint, attachment, rotatedKeysData))

        assertTrue(WhitelistedByZoneAttachmentConstraint.canBeTransitionedFrom(WhitelistedByZoneAttachmentConstraint, attachment, rotatedKeysData))

        assertTrue(AlwaysAcceptAttachmentConstraint.canBeTransitionedFrom(AlwaysAcceptAttachmentConstraint, attachment, rotatedKeysData))

        // Exhaustive negative check
        assertFalse(HashAttachmentConstraint(SecureHash.randomSHA256()).canBeTransitionedFrom(AlwaysAcceptAttachmentConstraint, attachment, rotatedKeysData))
        assertFalse(WhitelistedByZoneAttachmentConstraint.canBeTransitionedFrom(AlwaysAcceptAttachmentConstraint, attachment, rotatedKeysData))
        assertFalse(SignatureAttachmentConstraint(ALICE_PUBKEY).canBeTransitionedFrom(AlwaysAcceptAttachmentConstraint, attachment, rotatedKeysData))

        assertFalse(HashAttachmentConstraint(SecureHash.randomSHA256()).canBeTransitionedFrom(HashAttachmentConstraint(SecureHash.randomSHA256()), attachment, rotatedKeysData))

        assertFalse(WhitelistedByZoneAttachmentConstraint.canBeTransitionedFrom(HashAttachmentConstraint(SecureHash.randomSHA256()), attachment, rotatedKeysData))
        assertFalse(WhitelistedByZoneAttachmentConstraint.canBeTransitionedFrom(SignatureAttachmentConstraint(ALICE_PUBKEY), attachment, rotatedKeysData))

        assertFalse(SignatureAttachmentConstraint(ALICE_PUBKEY).canBeTransitionedFrom(HashAttachmentConstraint(SecureHash.randomSHA256()), attachment, rotatedKeysData))
        assertFalse(SignatureAttachmentConstraint(BOB_PUBKEY).canBeTransitionedFrom(WhitelistedByZoneAttachmentConstraint, attachment, rotatedKeysData))
        assertFalse(SignatureAttachmentConstraint(BOB_PUBKEY).canBeTransitionedFrom(SignatureAttachmentConstraint(ALICE_PUBKEY), attachment, rotatedKeysData))

        assertFalse(AlwaysAcceptAttachmentConstraint.canBeTransitionedFrom(SignatureAttachmentConstraint(ALICE_PUBKEY), attachment, rotatedKeysData))
        assertFalse(AlwaysAcceptAttachmentConstraint.canBeTransitionedFrom(WhitelistedByZoneAttachmentConstraint, attachment, rotatedKeysData))
        assertFalse(AlwaysAcceptAttachmentConstraint.canBeTransitionedFrom(HashAttachmentConstraint(SecureHash.randomSHA256()), attachment, rotatedKeysData))

        // Fail when encounter a AutomaticPlaceholderConstraint
        assertFailsWith<IllegalArgumentException> {
            HashAttachmentConstraint(SecureHash.randomSHA256())
                    .canBeTransitionedFrom(AutomaticPlaceholderConstraint, attachment, rotatedKeysData)
        }
        assertFailsWith<IllegalArgumentException> { AutomaticPlaceholderConstraint.canBeTransitionedFrom(AutomaticPlaceholderConstraint, attachment, rotatedKeysData) }
    }

    private fun MockServices.recordTransaction(wireTransaction: WireTransaction) {
        requireSupportedHashType(wireTransaction)
        val nodeKey = ALICE_PUBKEY
        val sigs = listOf(keyManagementService.sign(
                SignableData(wireTransaction.id, SignatureMetadata(4, Crypto.findSignatureScheme(nodeKey).schemeNumberID)), nodeKey),
                DUMMY_NOTARY_IDENTITY.keyPair.sign(SignableData(wireTransaction.id, SignatureMetadata(4, Crypto.findSignatureScheme(DUMMY_NOTARY_IDENTITY.publicKey).schemeNumberID))))
        recordTransactions(SignedTransaction(wireTransaction, sigs))
    }

    @Test(timeout=300_000)
	fun `Input state contract version may be incompatible with lower version`() {
        ledgerServices.ledger(DUMMY_NOTARY) {
            ledgerServices.recordTransaction(transaction {
                attachment(Cash.PROGRAM_ID, allOnesHash, listOf(hashToSignatureConstraintsKey), mapOf(Attributes.Name.IMPLEMENTATION_VERSION.toString() to "2"))
                output(Cash.PROGRAM_ID, "c1", DUMMY_NOTARY, null, SignatureAttachmentConstraint(hashToSignatureConstraintsKey), Cash.State(1000.POUNDS `issued by` ALICE_PARTY.ref(1), ALICE_PARTY))
                command(ALICE_PUBKEY, Cash.Commands.Issue())
                verifies()
            })
            transaction {
                attachment(Cash.PROGRAM_ID, zeroHash, listOf(hashToSignatureConstraintsKey), mapOf(Attributes.Name.IMPLEMENTATION_VERSION.toString() to "1"))
                input("c1")
                output(Cash.PROGRAM_ID, "c2", DUMMY_NOTARY, null, SignatureAttachmentConstraint(hashToSignatureConstraintsKey), Cash.State(1000.POUNDS `issued by` ALICE_PARTY.ref(1), BOB_PARTY))
                command(ALICE_PUBKEY, Cash.Commands.Move())
                verifies()
            }
        }
    }

    @Test(timeout=300_000)
	fun `Input state contract version is compatible with the same version`() {
        ledgerServices.ledger(DUMMY_NOTARY) {
            ledgerServices.recordTransaction(transaction {
                attachment(Cash.PROGRAM_ID, allOnesHash, listOf(hashToSignatureConstraintsKey), mapOf(Attributes.Name.IMPLEMENTATION_VERSION.toString() to "3"))
                output(Cash.PROGRAM_ID, "c1", DUMMY_NOTARY, null, SignatureAttachmentConstraint(hashToSignatureConstraintsKey), Cash.State(1000.POUNDS `issued by` ALICE_PARTY.ref(1), ALICE_PARTY))
                command(ALICE_PUBKEY, Cash.Commands.Issue())
                verifies()
            })
            transaction {
                attachment(Cash.PROGRAM_ID, zeroHash, listOf(hashToSignatureConstraintsKey), mapOf(Attributes.Name.IMPLEMENTATION_VERSION.toString() to "3"))
                input("c1")
                output(Cash.PROGRAM_ID, "c2", DUMMY_NOTARY, null, SignatureAttachmentConstraint(hashToSignatureConstraintsKey), Cash.State(1000.POUNDS `issued by` ALICE_PARTY.ref(1), BOB_PARTY))
                command(ALICE_PUBKEY, Cash.Commands.Move())
                verifies()
            }
        }
    }

    @Test(timeout=300_000)
	fun `Input state contract version is compatible with higher version`() {
        ledgerServices.ledger(DUMMY_NOTARY) {
            ledgerServices.recordTransaction(transaction {
                attachment(Cash.PROGRAM_ID, allOnesHash, listOf(hashToSignatureConstraintsKey), mapOf(Attributes.Name.IMPLEMENTATION_VERSION.toString() to "1"))
                output(Cash.PROGRAM_ID, "c1", DUMMY_NOTARY, null, SignatureAttachmentConstraint(hashToSignatureConstraintsKey), Cash.State(1000.POUNDS `issued by` ALICE_PARTY.ref(1), ALICE_PARTY))
                command(ALICE_PUBKEY, Cash.Commands.Issue())
                verifies()
            })
            transaction {
                attachment(Cash.PROGRAM_ID, zeroHash, listOf(hashToSignatureConstraintsKey), mapOf(Attributes.Name.IMPLEMENTATION_VERSION.toString() to "2"))
                input("c1")
                output(Cash.PROGRAM_ID, "c2", DUMMY_NOTARY, null, SignatureAttachmentConstraint(hashToSignatureConstraintsKey), Cash.State(1000.POUNDS `issued by` ALICE_PARTY.ref(1), BOB_PARTY))
                command(ALICE_PUBKEY, Cash.Commands.Move())
                verifies()
            }
        }
    }

    @Test(timeout=300_000)
	fun `Input states contract version may be lower that current contract version`() {
        ledgerServices.ledger(DUMMY_NOTARY) {
            ledgerServices.recordTransaction(transaction {
                attachment(Cash.PROGRAM_ID, allOnesHash, listOf(hashToSignatureConstraintsKey), mapOf(Attributes.Name.IMPLEMENTATION_VERSION.toString() to "1"))
                output(Cash.PROGRAM_ID, "c1", DUMMY_NOTARY, null, SignatureAttachmentConstraint(hashToSignatureConstraintsKey), Cash.State(1000.POUNDS `issued by` ALICE_PARTY.ref(1), ALICE_PARTY))
                command(ALICE_PUBKEY, Cash.Commands.Issue())
                verifies()
            })
            ledgerServices.recordTransaction(transaction {
                attachment(Cash.PROGRAM_ID, zeroHash, listOf(hashToSignatureConstraintsKey), mapOf(Attributes.Name.IMPLEMENTATION_VERSION.toString() to "2"))
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

    @Test(timeout=300_000)
	fun `Input state with contract version can be downgraded to no version`() {
        ledgerServices.ledger(DUMMY_NOTARY) {
            ledgerServices.recordTransaction(transaction {
                attachment(Cash.PROGRAM_ID, allOnesHash, listOf(hashToSignatureConstraintsKey), mapOf(Attributes.Name.IMPLEMENTATION_VERSION.toString() to "2"))
                output(Cash.PROGRAM_ID, "c1", DUMMY_NOTARY, null, SignatureAttachmentConstraint(hashToSignatureConstraintsKey), Cash.State(1000.POUNDS `issued by` ALICE_PARTY.ref(1), ALICE_PARTY))
                command(ALICE_PUBKEY, Cash.Commands.Issue())
                verifies()
            })
            transaction {
                attachment(Cash.PROGRAM_ID, zeroHash, listOf(hashToSignatureConstraintsKey), emptyMap())
                input("c1")
                output(Cash.PROGRAM_ID, "c2", DUMMY_NOTARY, null, SignatureAttachmentConstraint(hashToSignatureConstraintsKey), Cash.State(1000.POUNDS `issued by` ALICE_PARTY.ref(1), BOB_PARTY))
                command(ALICE_PUBKEY, Cash.Commands.Move())
                verifies()
            }
        }
    }

    @Test(timeout=300_000)
	fun `Input state without contract version is compatible with any version`() {
        ledgerServices.ledger(DUMMY_NOTARY) {
            ledgerServices.recordTransaction(transaction {
                attachment(Cash.PROGRAM_ID, allOnesHash, listOf(hashToSignatureConstraintsKey), emptyMap())
                output(Cash.PROGRAM_ID, "c1", DUMMY_NOTARY, null, SignatureAttachmentConstraint(hashToSignatureConstraintsKey), Cash.State(1000.POUNDS `issued by` ALICE_PARTY.ref(1), ALICE_PARTY))
                command(ALICE_PUBKEY, Cash.Commands.Issue())
                verifies()
            })
            transaction {
                attachment(Cash.PROGRAM_ID, zeroHash, listOf(hashToSignatureConstraintsKey), mapOf(Attributes.Name.IMPLEMENTATION_VERSION.toString() to "2"))
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
