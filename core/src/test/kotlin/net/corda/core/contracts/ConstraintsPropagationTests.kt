package net.corda.core.contracts

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.node.NotaryInfo
import net.corda.core.transactions.LedgerTransaction
import net.corda.finance.POUNDS
import net.corda.finance.`issued by`
import net.corda.finance.contracts.asset.Cash
import net.corda.node.services.api.IdentityServiceInternal
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.core.TestIdentity
import net.corda.testing.internal.rigorousMock
import net.corda.testing.node.MockServices
import net.corda.testing.node.ledger
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConstraintsPropagationTests {

    private companion object {
        val DUMMY_NOTARY = TestIdentity(DUMMY_NOTARY_NAME, 20).party
        val ALICE = TestIdentity(CordaX500Name("ALICE", "London", "GB"))
        val ALICE_PARTY get() = ALICE.party
        val ALICE_PUBKEY get() = ALICE.publicKey
        val BOB = TestIdentity(CordaX500Name("BOB", "London", "GB"))
        val BOB_PARTY get() = BOB.party
        val BOB_PUBKEY get() = BOB.publicKey
        val noPropagationContractClassName = "net.corda.core.contracts.NoPropagationContract"
    }

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    private val ledgerServices = MockServices(
            cordappPackages = listOf("net.corda.finance.contracts.asset"),
            initialIdentity = ALICE,
            identityService = rigorousMock<IdentityServiceInternal>().also {
                doReturn(ALICE_PARTY).whenever(it).partyFromKey(ALICE_PUBKEY)
                doReturn(BOB_PARTY).whenever(it).partyFromKey(BOB_PUBKEY)
            },
            networkParameters = testNetworkParameters(
                    minimumPlatformVersion = 4,
                    whitelistedContractImplementations = mapOf(
                            Cash.PROGRAM_ID to listOf(SecureHash.zeroHash, SecureHash.allOnesHash),
                            noPropagationContractClassName to listOf(SecureHash.zeroHash)
                    ),
                    notaries = listOf(NotaryInfo(DUMMY_NOTARY, true))
            )
    )

    @Test
    fun `Happy path with the HashConstraint`() {
        ledgerServices.ledger(DUMMY_NOTARY) {
            transaction {
                attachment(Cash.PROGRAM_ID, SecureHash.allOnesHash)
                output(Cash.PROGRAM_ID, "c1", DUMMY_NOTARY, null, HashAttachmentConstraint(SecureHash.allOnesHash), Cash.State(1000.POUNDS `issued by` ALICE_PARTY.ref(1), ALICE_PARTY))
                command(ALICE_PUBKEY, Cash.Commands.Issue())
                verifies()
            }
            transaction {
                attachment(Cash.PROGRAM_ID, SecureHash.allOnesHash)
                input("c1")
                output(Cash.PROGRAM_ID, "c2", DUMMY_NOTARY, null, HashAttachmentConstraint(SecureHash.allOnesHash), Cash.State(1000.POUNDS `issued by` ALICE_PARTY.ref(1), BOB_PARTY))
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
                output(Cash.PROGRAM_ID, "c1", DUMMY_NOTARY, null, HashAttachmentConstraint(SecureHash.zeroHash), Cash.State(1000.POUNDS `issued by` ALICE_PARTY.ref(1), ALICE_PARTY))
                command(ALICE_PUBKEY, Cash.Commands.Issue())
                verifies()
            }
            assertFailsWith<IllegalArgumentException> {
                transaction {
                    attachment(Cash.PROGRAM_ID, SecureHash.allOnesHash)
                    input("c1")
                    output(Cash.PROGRAM_ID, "c2", DUMMY_NOTARY, null, HashAttachmentConstraint(SecureHash.allOnesHash), Cash.State(1000.POUNDS `issued by` ALICE_PARTY.ref(1), BOB_PARTY))
                    command(ALICE_PUBKEY, Cash.Commands.Move())
                    verifies()
                }
            }
        }
    }

    @Test
    fun `Transaction validation fails, when constraints do not propagate correctly`() {
        ledgerServices.ledger(DUMMY_NOTARY) {
            transaction {
                attachment(Cash.PROGRAM_ID, SecureHash.zeroHash)
                output(Cash.PROGRAM_ID, "c1", DUMMY_NOTARY, null, HashAttachmentConstraint(SecureHash.zeroHash), Cash.State(1000.POUNDS `issued by` ALICE_PARTY.ref(1), ALICE_PARTY))
                command(ALICE_PUBKEY, Cash.Commands.Issue())
                verifies()
            }
            transaction {
                attachment(Cash.PROGRAM_ID, SecureHash.zeroHash)
                input("c1")
                output(Cash.PROGRAM_ID, "c2", DUMMY_NOTARY, null, WhitelistedByZoneAttachmentConstraint, Cash.State(1000.POUNDS `issued by` ALICE_PARTY.ref(1), BOB_PARTY))
                command(ALICE_PUBKEY, Cash.Commands.Move())
                failsWith("are not propagated correctly")
            }
            transaction {
                attachment(Cash.PROGRAM_ID, SecureHash.zeroHash)
                input("c1")
                output(Cash.PROGRAM_ID, "c3", DUMMY_NOTARY, null, SignatureAttachmentConstraint(ALICE_PUBKEY), Cash.State(1000.POUNDS `issued by` ALICE_PARTY.ref(1), BOB_PARTY))
                command(ALICE_PUBKEY, Cash.Commands.Move())
                failsWith("are not propagated correctly")
            }
            transaction {
                attachment(Cash.PROGRAM_ID, SecureHash.zeroHash)
                input("c1")
                output(Cash.PROGRAM_ID, "c4", DUMMY_NOTARY, null, AlwaysAcceptAttachmentConstraint, Cash.State(1000.POUNDS `issued by` ALICE_PARTY.ref(1), BOB_PARTY))
                command(ALICE_PUBKEY, Cash.Commands.Move())
                failsWith("are not propagated correctly")
            }
        }
    }

    @Test
    fun `When the constraint of the output state is a valid transition from the input state, transaction validation works`() {
        ledgerServices.ledger(DUMMY_NOTARY) {
            transaction {
                attachment(Cash.PROGRAM_ID, SecureHash.zeroHash)
                output(Cash.PROGRAM_ID, "c1", DUMMY_NOTARY, null, WhitelistedByZoneAttachmentConstraint, Cash.State(1000.POUNDS `issued by` ALICE_PARTY.ref(1), ALICE_PARTY))
                command(ALICE_PUBKEY, Cash.Commands.Issue())
                verifies()
            }
            transaction {
                attachment(Cash.PROGRAM_ID, SecureHash.zeroHash)
                input("c1")
                output(Cash.PROGRAM_ID, "c2", DUMMY_NOTARY, null, HashAttachmentConstraint(SecureHash.zeroHash), Cash.State(1000.POUNDS `issued by` ALICE_PARTY.ref(1), BOB_PARTY))
                command(ALICE_PUBKEY, Cash.Commands.Move())
                verifies()
            }
        }
    }

    @Test
    fun `Switching from the WhitelistConstraint to the Signature Constraint is possible if the attachment satisfies both constraints, and the signature constraint inherits all jar signatures`() {

        ledgerServices.ledger(DUMMY_NOTARY) {
            transaction {
                attachment(Cash.PROGRAM_ID, SecureHash.zeroHash)
                output(Cash.PROGRAM_ID, "w1", DUMMY_NOTARY, null, WhitelistedByZoneAttachmentConstraint, Cash.State(1000.POUNDS `issued by` ALICE_PARTY.ref(1), ALICE_PARTY))
                command(ALICE_PUBKEY, Cash.Commands.Issue())
                verifies()
            }

            // the attachment is signed
            transaction {
                attachment(Cash.PROGRAM_ID, SecureHash.allOnesHash, listOf(ALICE_PARTY.owningKey))
                input("w1")
                output(Cash.PROGRAM_ID, "w2", DUMMY_NOTARY, null, SignatureAttachmentConstraint(ALICE_PUBKEY), Cash.State(1000.POUNDS `issued by` ALICE_PARTY.ref(1), BOB_PARTY))
                command(ALICE_PUBKEY, Cash.Commands.Move())
                verifies()
            }
        }
    }

    @Test
    fun `Switching from the WhitelistConstraint to the Signature Constraint fails if the signature constraint does not inherit all jar signatures`() {
        ledgerServices.ledger(DUMMY_NOTARY) {
            transaction {
                attachment(Cash.PROGRAM_ID, SecureHash.zeroHash)
                output(Cash.PROGRAM_ID, "w1", DUMMY_NOTARY, null, WhitelistedByZoneAttachmentConstraint, Cash.State(1000.POUNDS `issued by` ALICE_PARTY.ref(1), ALICE_PARTY))
                command(ALICE_PUBKEY, Cash.Commands.Issue())
                verifies()
            }

            // the attachment is not signed
            transaction {
                attachment(Cash.PROGRAM_ID, SecureHash.zeroHash)
                input("w1")
                output(Cash.PROGRAM_ID, "w2", DUMMY_NOTARY, null, SignatureAttachmentConstraint(ALICE_PUBKEY), Cash.State(1000.POUNDS `issued by` ALICE_PARTY.ref(1), BOB_PARTY))
                command(ALICE_PUBKEY, Cash.Commands.Move())
                // Note that it fails after the constraints propagation check, because the attachment is not signed.
                failsWith("are not propagated correctly")
            }
        }
    }

    @Test
    fun `On contract annotated with NoConstraintPropagation there is no platform check for propagation, but the transaction builder can't use the AutomaticPlaceholderConstraint`() {
        ledgerServices.ledger(DUMMY_NOTARY) {
            transaction {
                attachment(noPropagationContractClassName, SecureHash.zeroHash)
                output(noPropagationContractClassName, "c1", DUMMY_NOTARY, null, HashAttachmentConstraint(SecureHash.zeroHash), NoPropagationContractState())
                command(ALICE_PUBKEY, NoPropagationContract.Create())
                verifies()
            }
            transaction {
                attachment(noPropagationContractClassName, SecureHash.zeroHash)
                input("c1")
                output(noPropagationContractClassName, "c2", DUMMY_NOTARY, null, WhitelistedByZoneAttachmentConstraint, NoPropagationContractState())
                command(ALICE_PUBKEY, NoPropagationContract.Create())
                verifies()
            }
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
    fun `Attachment canBeTransitionedFrom behaves as expected`() {

        val attachment = mock<ContractAttachment>()
        whenever(attachment.signerKeys).thenReturn(listOf(ALICE_PARTY.owningKey))

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
        assertFailsWith<IllegalArgumentException> { HashAttachmentConstraint(SecureHash.randomSHA256()).canBeTransitionedFrom(AutomaticPlaceholderConstraint, attachment) }
        assertFailsWith<IllegalArgumentException> { AutomaticPlaceholderConstraint.canBeTransitionedFrom(AutomaticPlaceholderConstraint, attachment) }
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
