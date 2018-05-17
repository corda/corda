package net.corda.docs.tutorial.testdsl

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.contracts.TransactionVerificationException
import net.corda.core.identity.CordaX500Name
import net.corda.core.node.services.IdentityService
import net.corda.core.utilities.days
import net.corda.finance.DOLLARS
import net.corda.finance.`issued by`
import net.corda.finance.contracts.CP_PROGRAM_ID
import net.corda.finance.contracts.CommercialPaper
import net.corda.finance.contracts.ICommercialPaperState
import net.corda.finance.contracts.asset.CASH
import net.corda.finance.contracts.asset.Cash
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.core.TestIdentity
import net.corda.testing.internal.rigorousMock
import net.corda.testing.node.MockServices
import net.corda.testing.node.ledger
import net.corda.testing.node.transaction
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class CommercialPaperTest {
    private companion object {
        val alice = TestIdentity(ALICE_NAME, 70)
        val bob = TestIdentity(BOB_NAME, 80)
        // DOCSTART 14
        val bigCorp = TestIdentity((CordaX500Name("BigCorp", "New York", "GB")))
        // DOCEND 14
        val dummyNotary = TestIdentity(DUMMY_NOTARY_NAME, 20)
        val megaCorp = TestIdentity(CordaX500Name("MegaCorp", "London", "GB"))
        val TEST_TX_TIME: Instant = Instant.parse("2015-04-17T12:00:00.00Z")
    }

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()
    // DOCSTART 11
    private val ledgerServices = MockServices(
            // A list of packages to scan for cordapps
            listOf("net.corda.finance.contracts"),
            // The identity represented by this set of mock services. Defaults to a test identity.
            // You can also use the alternative parameter initialIdentityName which accepts a
            // [CordaX500Name]
            megaCorp,
            rigorousMock<IdentityService>().also {
        doReturn(megaCorp.party).whenever(it).partyFromKey(megaCorp.publicKey)
        doReturn(null).whenever(it).partyFromKey(bigCorp.publicKey)
        doReturn(null).whenever(it).partyFromKey(alice.publicKey)
    })
    // DOCEND 11

    // DOCSTART 12
    @Suppress("unused")
    private val simpleLedgerServices = MockServices(
            // This is the identity of the node
            megaCorp,
            // Other identities the test node knows about
            bigCorp,
            alice
    )
    // DOCEND 12

    // DOCSTART 1
    fun getPaper(): ICommercialPaperState = CommercialPaper.State(
            issuance = megaCorp.party.ref(123),
            owner = megaCorp.party,
            faceValue = 1000.DOLLARS `issued by` megaCorp.party.ref(123),
            maturityDate = TEST_TX_TIME + 7.days
    )
    // DOCEND 1

    // DOCSTART 2
    // This example test will fail with this exception.
    @Test(expected = IllegalStateException::class)
    fun simpleCP() {
        val inState = getPaper()
        ledgerServices.ledger(dummyNotary.party) {
            transaction {
                attachments(CP_PROGRAM_ID)
                input(CP_PROGRAM_ID, inState)
                verifies()
            }
        }
    }
    // DOCEND 2

    // DOCSTART 3
    // This example test will fail with this exception.
    @Test(expected = TransactionVerificationException.ContractRejection::class)
    fun simpleCPMove() {
        val inState = getPaper()
        ledgerServices.ledger(dummyNotary.party) {
            transaction {
                input(CP_PROGRAM_ID, inState)
                command(megaCorp.publicKey, CommercialPaper.Commands.Move())
                attachments(CP_PROGRAM_ID)
                verifies()
            }
        }
    }
    // DOCEND 3

    // DOCSTART 4
    @Test
    fun simpleCPMoveFails() {
        val inState = getPaper()
        ledgerServices.ledger(dummyNotary.party) {
            transaction {
                input(CP_PROGRAM_ID, inState)
                command(megaCorp.publicKey, CommercialPaper.Commands.Move())
                attachments(CP_PROGRAM_ID)
                `fails with`("the state is propagated")
            }
        }
    }
    // DOCEND 4

    // DOCSTART 5
    @Test
    fun simpleCPMoveFailureAndSuccess() {
        val inState = getPaper()
        ledgerServices.ledger(dummyNotary.party) {
            transaction {
                input(CP_PROGRAM_ID, inState)
                command(megaCorp.publicKey, CommercialPaper.Commands.Move())
                attachments(CP_PROGRAM_ID)
                `fails with`("the state is propagated")
                output(CP_PROGRAM_ID, "alice's paper", inState.withOwner(alice.party))
                verifies()
            }
        }
    }
    // DOCEND 5

    // DOCSTART 13
    @Test
    fun simpleCPMoveSuccess() {
        val inState = getPaper()
        ledgerServices.ledger(dummyNotary.party) {
            transaction {
                input(CP_PROGRAM_ID, inState)
                command(megaCorp.publicKey, CommercialPaper.Commands.Move())
                attachments(CP_PROGRAM_ID)
                timeWindow(TEST_TX_TIME)
                output(CP_PROGRAM_ID, "alice's paper", inState.withOwner(alice.party))
                verifies()
            }
        }
    }
    // DOCEND 13

    // DOCSTART 6
    @Test
    fun `simple issuance with tweak`() {
        ledgerServices.ledger(dummyNotary.party) {
            transaction {
                output(CP_PROGRAM_ID, "paper", getPaper()) // Some CP is issued onto the ledger by MegaCorp.
                attachments(CP_PROGRAM_ID)
                tweak {
                    // The wrong pubkey.
                    command(bigCorp.publicKey, CommercialPaper.Commands.Issue())
                    timeWindow(TEST_TX_TIME)
                    `fails with`("output states are issued by a command signer")
                }
                command(megaCorp.publicKey, CommercialPaper.Commands.Issue())
                timeWindow(TEST_TX_TIME)
                verifies()
            }
        }
    }
    // DOCEND 6

    // DOCSTART 7
    @Test
    fun `simple issuance with tweak and top level transaction`() {
        ledgerServices.transaction(dummyNotary.party) {
            output(CP_PROGRAM_ID, "paper", getPaper()) // Some CP is issued onto the ledger by MegaCorp.
            attachments(CP_PROGRAM_ID)
            tweak {
                // The wrong pubkey.
                command(bigCorp.publicKey, CommercialPaper.Commands.Issue())
                timeWindow(TEST_TX_TIME)
                `fails with`("output states are issued by a command signer")
            }
            command(megaCorp.publicKey, CommercialPaper.Commands.Issue())
            timeWindow(TEST_TX_TIME)
            verifies()
        }
    }
    // DOCEND 7

    // DOCSTART 8
    @Test
    fun `chain commercial paper`() {
        val issuer = megaCorp.party.ref(123)
        ledgerServices.ledger(dummyNotary.party) {
            unverifiedTransaction {
                attachments(Cash.PROGRAM_ID)
                output(Cash.PROGRAM_ID, "alice's $900", 900.DOLLARS.CASH issuedBy issuer ownedBy alice.party)
            }

            // Some CP is issued onto the ledger by MegaCorp.
            transaction("Issuance") {
                output(CP_PROGRAM_ID, "paper", getPaper())
                command(megaCorp.publicKey, CommercialPaper.Commands.Issue())
                attachments(CP_PROGRAM_ID)
                timeWindow(TEST_TX_TIME)
                verifies()
            }


            transaction("Trade") {
                input("paper")
                input("alice's $900")
                output(Cash.PROGRAM_ID, "borrowed $900", 900.DOLLARS.CASH issuedBy issuer ownedBy megaCorp.party)
                output(CP_PROGRAM_ID, "alice's paper", "paper".output<ICommercialPaperState>().withOwner(alice.party))
                command(alice.publicKey, Cash.Commands.Move())
                command(megaCorp.publicKey, CommercialPaper.Commands.Move())
                verifies()
            }
        }
    }
    // DOCEND 8

    // DOCSTART 9
    @Test
    fun `chain commercial paper double spend`() {
        val issuer = megaCorp.party.ref(123)
        ledgerServices.ledger(dummyNotary.party) {
            unverifiedTransaction {
                attachments(Cash.PROGRAM_ID)
                output(Cash.PROGRAM_ID, "alice's $900", 900.DOLLARS.CASH issuedBy issuer ownedBy alice.party)
            }

            // Some CP is issued onto the ledger by MegaCorp.
            transaction("Issuance") {
                output(CP_PROGRAM_ID, "paper", getPaper())
                command(megaCorp.publicKey, CommercialPaper.Commands.Issue())
                attachments(CP_PROGRAM_ID)
                timeWindow(TEST_TX_TIME)
                verifies()
            }

            transaction("Trade") {
                input("paper")
                input("alice's $900")
                output(Cash.PROGRAM_ID, "borrowed $900", 900.DOLLARS.CASH issuedBy issuer ownedBy megaCorp.party)
                output(CP_PROGRAM_ID, "alice's paper", "paper".output<ICommercialPaperState>().withOwner(alice.party))
                command(alice.publicKey, Cash.Commands.Move())
                command(megaCorp.publicKey, CommercialPaper.Commands.Move())
                verifies()
            }

            transaction {
                input("paper")
                // We moved a paper to another pubkey.
                output(CP_PROGRAM_ID, "bob's paper", "paper".output<ICommercialPaperState>().withOwner(bob.party))
                command(megaCorp.publicKey, CommercialPaper.Commands.Move())
                verifies()
            }

            fails()
        }
    }
    // DOCEND 9

    // DOCSTART 10
    @Test
    fun `chain commercial tweak`() {
        val issuer = megaCorp.party.ref(123)
        ledgerServices.ledger(dummyNotary.party) {
            unverifiedTransaction {
                attachments(Cash.PROGRAM_ID)
                output(Cash.PROGRAM_ID, "alice's $900", 900.DOLLARS.CASH issuedBy issuer ownedBy alice.party)
            }

            // Some CP is issued onto the ledger by MegaCorp.
            transaction("Issuance") {
                output(CP_PROGRAM_ID, "paper", getPaper())
                command(megaCorp.publicKey, CommercialPaper.Commands.Issue())
                attachments(CP_PROGRAM_ID)
                timeWindow(TEST_TX_TIME)
                verifies()
            }

            transaction("Trade") {
                input("paper")
                input("alice's $900")
                output(Cash.PROGRAM_ID, "borrowed $900", 900.DOLLARS.CASH issuedBy issuer ownedBy megaCorp.party)
                output(CP_PROGRAM_ID, "alice's paper", "paper".output<ICommercialPaperState>().withOwner(alice.party))
                command(alice.publicKey, Cash.Commands.Move())
                command(megaCorp.publicKey, CommercialPaper.Commands.Move())
                verifies()
            }

            tweak {
                transaction {
                    input("paper")
                    // We moved a paper to another pubkey.
                    output(CP_PROGRAM_ID, "bob's paper", "paper".output<ICommercialPaperState>().withOwner(bob.party))
                    command(megaCorp.publicKey, CommercialPaper.Commands.Move())
                    verifies()
                }
                fails()
            }

            verifies()
        }
    }
    // DOCEND 10
}
