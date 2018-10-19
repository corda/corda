package net.corda.core.transactions

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.contracts.Contract
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.TransactionVerificationException
import net.corda.core.contracts.requireThat
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.finance.DOLLARS
import net.corda.finance.`issued by`
import net.corda.finance.contracts.asset.Cash
import net.corda.node.services.api.IdentityServiceInternal
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.core.TestIdentity
import net.corda.testing.internal.rigorousMock
import net.corda.testing.node.MockServices
import net.corda.testing.node.ledger
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.assertFailsWith

const val TEST_TIMELOCK_ID = "net.corda.core.transactions.TransactionEncumbranceTests\$DummyTimeLock"

class TransactionEncumbranceTests {
    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    private companion object {
        val DUMMY_NOTARY = TestIdentity(DUMMY_NOTARY_NAME, 20).party
        val megaCorp = TestIdentity(CordaX500Name("MegaCorp", "London", "GB"))
        val MINI_CORP = TestIdentity(CordaX500Name("MiniCorp", "London", "GB")).party
        val MEGA_CORP get() = megaCorp.party
        val MEGA_CORP_PUBKEY get() = megaCorp.publicKey

        val defaultIssuer = MEGA_CORP.ref(1)

        val state = Cash.State(
                amount = 1000.DOLLARS `issued by` defaultIssuer,
                owner = MEGA_CORP
        )

        val stateWithNewOwner = state.copy(owner = MINI_CORP)
        val extraCashState = state.copy(amount = state.amount * 3)

        val FOUR_PM: Instant = Instant.parse("2015-04-17T16:00:00.00Z")
        val FIVE_PM: Instant = FOUR_PM.plus(1, ChronoUnit.HOURS)
        val timeLock = DummyTimeLock.State(FIVE_PM)


        val ledgerServices = MockServices(listOf("net.corda.core.transactions", "net.corda.finance.contracts.asset"), MEGA_CORP.name,
                rigorousMock<IdentityServiceInternal>().also {
                    doReturn(MEGA_CORP).whenever(it).partyFromKey(MEGA_CORP_PUBKEY)
                })
    }

    class DummyTimeLock : Contract {
        override fun verify(tx: LedgerTransaction) {
            val timeLockInput = tx.inputsOfType<State>().singleOrNull() ?: return
            val time = tx.timeWindow?.untilTime
                    ?: throw IllegalArgumentException("Transactions containing time-locks must have a time-window")
            requireThat {
                "the time specified in the time-lock has passed" using (time >= timeLockInput.validFrom)
            }
        }

        data class State(
                val validFrom: Instant
        ) : ContractState {
            override val participants: List<AbstractParty> = emptyList()
        }
    }

    @Test
    fun `states can be bi-directionally encumbered`() {
        // Basic encumbrance example for encumbrance index links 0 -> 1 and 1 -> 0
        ledgerServices.ledger(DUMMY_NOTARY) {
            transaction {
                attachments(Cash.PROGRAM_ID, TEST_TIMELOCK_ID)
                input(Cash.PROGRAM_ID, state)
                output(Cash.PROGRAM_ID, "state encumbered by 5pm time-lock", encumbrance = 1, contractState = stateWithNewOwner)
                output(TEST_TIMELOCK_ID, "5pm time-lock", 0, timeLock)
                command(MEGA_CORP.owningKey, Cash.Commands.Move())
                verifies()
            }
        }

        // Full cycle example with 4 elements 0 -> 1, 1 -> 2, 2 -> 3 and 3 -> 0
        // All 3 Cash states and the TimeLock are linked and should be consumed in the same transaction.
        // Note that all of the Cash states are encumbered both together and with time lock.
        ledgerServices.ledger(DUMMY_NOTARY) {
            transaction {
                attachments(Cash.PROGRAM_ID, TEST_TIMELOCK_ID)
                input(Cash.PROGRAM_ID, extraCashState)
                output(Cash.PROGRAM_ID, "state encumbered by state 1", encumbrance = 1, contractState = stateWithNewOwner)
                output(Cash.PROGRAM_ID, "state encumbered by state 2", encumbrance = 2, contractState = stateWithNewOwner)
                output(Cash.PROGRAM_ID, "state encumbered by state 3", encumbrance = 3, contractState = stateWithNewOwner)
                output(TEST_TIMELOCK_ID, "5pm time-lock", 0, timeLock)
                command(MEGA_CORP.owningKey, Cash.Commands.Move())
                verifies()
            }
        }

        // A transaction that includes multiple independent encumbrance chains.
        // Each Cash state is encumbered with its own TimeLock.
        // Note that all of the Cash states are encumbered both together and with time lock.
        ledgerServices.ledger(DUMMY_NOTARY) {
            transaction {
                attachments(Cash.PROGRAM_ID, TEST_TIMELOCK_ID)
                input(Cash.PROGRAM_ID, extraCashState)
                output(Cash.PROGRAM_ID, "state encumbered by 5pm time-lock A", encumbrance = 3, contractState = stateWithNewOwner)
                output(Cash.PROGRAM_ID, "state encumbered by 5pm time-lock B", encumbrance = 4, contractState = stateWithNewOwner)
                output(Cash.PROGRAM_ID, "state encumbered by 5pm time-lock C", encumbrance = 5, contractState = stateWithNewOwner)
                output(TEST_TIMELOCK_ID, "5pm time-lock A", 0, timeLock)
                output(TEST_TIMELOCK_ID, "5pm time-lock B", 1, timeLock)
                output(TEST_TIMELOCK_ID, "5pm time-lock C", 2, timeLock)
                command(MEGA_CORP.owningKey, Cash.Commands.Move())
                verifies()
            }
        }

        // Full cycle example with 4 elements (different combination) 0 -> 3, 1 -> 2, 2 -> 0 and 3 -> 1
        ledgerServices.ledger(DUMMY_NOTARY) {
            transaction {
                attachments(Cash.PROGRAM_ID, TEST_TIMELOCK_ID)
                input(Cash.PROGRAM_ID, extraCashState)
                output(Cash.PROGRAM_ID, "state encumbered by state 3", encumbrance = 3, contractState = stateWithNewOwner)
                output(Cash.PROGRAM_ID, "state encumbered by state 2", encumbrance = 2, contractState = stateWithNewOwner)
                output(Cash.PROGRAM_ID, "state encumbered by state 0", encumbrance = 0, contractState = stateWithNewOwner)
                output(TEST_TIMELOCK_ID, "5pm time-lock", 1, timeLock)
                command(MEGA_CORP.owningKey, Cash.Commands.Move())
                verifies()
            }
        }
    }

    @Test
    fun `non bi-directional encumbrance will fail`() {
        // Single encumbrance with no back link.
        assertFailsWith<TransactionVerificationException.TransactionNonMatchingEncumbranceException> {
            ledgerServices.ledger(DUMMY_NOTARY) {
                transaction {
                    attachments(Cash.PROGRAM_ID, TEST_TIMELOCK_ID)
                    input(Cash.PROGRAM_ID, state)
                    output(Cash.PROGRAM_ID, "state encumbered by 5pm time-lock", encumbrance = 1, contractState = stateWithNewOwner)
                    output(TEST_TIMELOCK_ID, "5pm time-lock", timeLock)
                    command(MEGA_CORP.owningKey, Cash.Commands.Move())
                    verifies()
                }
            }
        }

        // Full cycle fails due to duplicate encumbrance reference.
        // 0 -> 1, 1 -> 3, 2 -> 3 (thus 3 is referenced two times).
        assertFailsWith<TransactionVerificationException.TransactionDuplicateEncumbranceException> {
            ledgerServices.ledger(DUMMY_NOTARY) {
                transaction {
                    attachments(Cash.PROGRAM_ID, TEST_TIMELOCK_ID)
                    input(Cash.PROGRAM_ID, state)
                    output(Cash.PROGRAM_ID, "state encumbered by state 1", encumbrance = 1, contractState = stateWithNewOwner)
                    output(Cash.PROGRAM_ID, "state encumbered by state 3", encumbrance = 3, contractState = stateWithNewOwner)
                    output(Cash.PROGRAM_ID, "state encumbered by state 3 again", encumbrance = 3, contractState = stateWithNewOwner)
                    output(TEST_TIMELOCK_ID, "5pm time-lock", timeLock)
                    command(MEGA_CORP.owningKey, Cash.Commands.Move())
                    verifies()
                }
            }
        }

        // No Full cycle due to non-matching encumbered-encumbrance elements.
        // 0 -> 1, 1 -> 3, 2 -> 0 (thus offending indices [2, 3], because 2 is not referenced and 3 is not encumbered).
        assertFailsWith<TransactionVerificationException.TransactionNonMatchingEncumbranceException> {
            ledgerServices.ledger(DUMMY_NOTARY) {
                transaction {
                    attachments(Cash.PROGRAM_ID, TEST_TIMELOCK_ID)
                    input(Cash.PROGRAM_ID, state)
                    output(Cash.PROGRAM_ID, "state encumbered by state 1", encumbrance = 1, contractState = stateWithNewOwner)
                    output(Cash.PROGRAM_ID, "state encumbered by state 3", encumbrance = 3, contractState = stateWithNewOwner)
                    output(Cash.PROGRAM_ID, "state encumbered by state 0", encumbrance = 0, contractState = stateWithNewOwner)
                    output(TEST_TIMELOCK_ID, "5pm time-lock", timeLock)
                    command(MEGA_CORP.owningKey, Cash.Commands.Move())
                    verifies()
                }
            }
        }

        // No Full cycle in one of the encumbrance chains due to non-matching encumbered-encumbrance elements.
        // 0 -> 2, 2 -> 0 is valid. On the other hand, there is 1 -> 3 only and 3 -> 1 does not exist.
        // (thus offending indices [1, 3], because 1 is not referenced and 3 is not encumbered).
        assertFailsWith<TransactionVerificationException.TransactionNonMatchingEncumbranceException> {
            ledgerServices.ledger(DUMMY_NOTARY) {
                transaction {
                    attachments(Cash.PROGRAM_ID, TEST_TIMELOCK_ID)
                    input(Cash.PROGRAM_ID, state)
                    output(Cash.PROGRAM_ID, "state encumbered by 5pm time-lock A", encumbrance = 2, contractState = stateWithNewOwner)
                    output(Cash.PROGRAM_ID, "state encumbered by 5pm time-lock B", encumbrance = 3, contractState = stateWithNewOwner)
                    output(TEST_TIMELOCK_ID, "5pm time-lock A", 0, timeLock)
                    output(TEST_TIMELOCK_ID, "5pm time-lock B", timeLock)
                    command(MEGA_CORP.owningKey, Cash.Commands.Move())
                    verifies()
                }
            }
        }
    }

    @Test
    fun `state can transition if encumbrance rules are met`() {
        ledgerServices.ledger(DUMMY_NOTARY) {
            unverifiedTransaction {
                attachments(Cash.PROGRAM_ID, TEST_TIMELOCK_ID)
                output(Cash.PROGRAM_ID, "state encumbered by 5pm time-lock", state)
                output(TEST_TIMELOCK_ID, "5pm time-lock", timeLock)
            }
            // Un-encumber the output if the time of the transaction is later than the timelock.
            transaction {
                attachments(Cash.PROGRAM_ID, TEST_TIMELOCK_ID)
                input("state encumbered by 5pm time-lock")
                input("5pm time-lock")
                output(Cash.PROGRAM_ID, stateWithNewOwner)
                command(MEGA_CORP.owningKey, Cash.Commands.Move())
                timeWindow(FIVE_PM)
                verifies()
            }
        }
    }

    @Test
    fun `state cannot transition if the encumbrance contract fails to verify`() {
        ledgerServices.ledger(DUMMY_NOTARY) {
            unverifiedTransaction {
                attachments(Cash.PROGRAM_ID, TEST_TIMELOCK_ID)
                output(Cash.PROGRAM_ID, "state encumbered by 5pm time-lock", state)
                output(TEST_TIMELOCK_ID, "5pm time-lock", timeLock)
            }
            // The time of the transaction is earlier than the time specified in the encumbering timelock.
            transaction {
                attachments(Cash.PROGRAM_ID, TEST_TIMELOCK_ID)
                input("state encumbered by 5pm time-lock")
                input("5pm time-lock")
                output(Cash.PROGRAM_ID, state)
                command(MEGA_CORP.owningKey, Cash.Commands.Move())
                timeWindow(FOUR_PM)
                this `fails with` "the time specified in the time-lock has passed"
            }
        }
    }

    @Test
    fun `state must be consumed along with its encumbrance`() {
        ledgerServices.ledger(DUMMY_NOTARY) {
            unverifiedTransaction {
                attachments(Cash.PROGRAM_ID, TEST_TIMELOCK_ID)
                output(Cash.PROGRAM_ID, "state encumbered by 5pm time-lock", encumbrance = 1, contractState = state)
                output(TEST_TIMELOCK_ID, "5pm time-lock",0, timeLock)
            }
            transaction {
                attachments(Cash.PROGRAM_ID)
                input("state encumbered by 5pm time-lock")
                output(Cash.PROGRAM_ID, stateWithNewOwner)
                command(MEGA_CORP.owningKey, Cash.Commands.Move())
                timeWindow(FIVE_PM)
                this `fails with` "Missing required encumbrance 1 in INPUT"
            }
        }
    }

    @Test
    fun `state cannot be encumbered by itself`() {
        ledgerServices.ledger(DUMMY_NOTARY) {
            transaction {
                attachments(Cash.PROGRAM_ID)
                input(Cash.PROGRAM_ID, state)
                output(Cash.PROGRAM_ID, "state encumbered by itself", encumbrance = 0, contractState = stateWithNewOwner)
                command(MEGA_CORP.owningKey, Cash.Commands.Move())
                this `fails with` "Missing required encumbrance 0 in OUTPUT"
            }
        }
    }

    @Test
    fun `encumbrance state index must be valid`() {
        ledgerServices.ledger(DUMMY_NOTARY) {
            transaction {
                attachments(Cash.PROGRAM_ID, TEST_TIMELOCK_ID)
                input(Cash.PROGRAM_ID, state)
                output(TEST_TIMELOCK_ID, "state encumbered by state 2 which does not exist", encumbrance = 2, contractState = stateWithNewOwner)
                output(TEST_TIMELOCK_ID, timeLock)
                command(MEGA_CORP.owningKey, Cash.Commands.Move())
                this `fails with` "Missing required encumbrance 2 in OUTPUT"
            }
        }
    }

    @Test
    fun `correct encumbrance state must be provided`() {
        ledgerServices.ledger(DUMMY_NOTARY) {
            unverifiedTransaction {
                attachments(Cash.PROGRAM_ID, TEST_TIMELOCK_ID)
                output(Cash.PROGRAM_ID, "state encumbered by some other state", encumbrance = 1, contractState = state)
                output(Cash.PROGRAM_ID, "some other state", encumbrance = 0, contractState = state)
                output(TEST_TIMELOCK_ID, "5pm time-lock", timeLock)
            }
            transaction {
                attachments(Cash.PROGRAM_ID, TEST_TIMELOCK_ID)
                input("state encumbered by some other state")
                input("5pm time-lock")
                output(Cash.PROGRAM_ID, stateWithNewOwner)
                command(MEGA_CORP.owningKey, Cash.Commands.Move())
                timeWindow(FIVE_PM)
                this `fails with` "Missing required encumbrance 1 in INPUT"
            }
        }
    }
}
