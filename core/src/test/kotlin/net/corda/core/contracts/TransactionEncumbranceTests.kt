package net.corda.core.contracts

import net.corda.contracts.asset.Cash
import net.corda.core.identity.AbstractParty
import net.corda.core.transactions.LedgerTransaction
import net.corda.finance.DOLLARS
import net.corda.finance.`issued by`
import net.corda.testing.MEGA_CORP
import net.corda.testing.MINI_CORP
import net.corda.testing.ledger
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

val TEST_TIMELOCK_ID = TransactionEncumbranceTests.DummyTimeLock()

class TransactionEncumbranceTests {
    val defaultIssuer = MEGA_CORP.ref(1)

    val state = Cash.State(
            amount = 1000.DOLLARS `issued by` defaultIssuer,
            owner = MEGA_CORP
    )
    val stateWithNewOwner = state.copy(owner = MINI_CORP)

    val FOUR_PM: Instant = Instant.parse("2015-04-17T16:00:00.00Z")
    val FIVE_PM: Instant = FOUR_PM.plus(1, ChronoUnit.HOURS)
    val timeLock = DummyTimeLock.State(FIVE_PM)

    class DummyTimeLock : Contract {
        override fun verify(tx: LedgerTransaction) {
            val timeLockInput = tx.inputsOfType<State>().singleOrNull() ?: return
            val time = tx.timeWindow?.untilTime ?: throw IllegalArgumentException("Transactions containing time-locks must have a time-window")
            requireThat {
                "the time specified in the time-lock has passed" using (time >= timeLockInput.validFrom)
            }
        }

        data class State(
                val validFrom: Instant
        ) : ContractState {
            override val participants: List<AbstractParty> = emptyList()
            override val contract: Contract = TEST_TIMELOCK_ID
        }
    }

    @Test
    fun `state can be encumbered`() {
        ledger {
            transaction {
                input { state }
                output(encumbrance = 1) { stateWithNewOwner }
                output("5pm time-lock") { timeLock }
                command(MEGA_CORP.owningKey) { Cash.Commands.Move() }
                verifies()
            }
        }
    }

    @Test
    fun `state can transition if encumbrance rules are met`() {
        ledger {
            unverifiedTransaction {
                output("state encumbered by 5pm time-lock") { state }
                output("5pm time-lock") { timeLock }
            }
            // Un-encumber the output if the time of the transaction is later than the timelock.
            transaction {
                input("state encumbered by 5pm time-lock")
                input("5pm time-lock")
                output { stateWithNewOwner }
                command(MEGA_CORP.owningKey) { Cash.Commands.Move() }
                timeWindow(FIVE_PM)
                verifies()
            }
        }
    }

    @Test
    fun `state cannot transition if the encumbrance contract fails to verify`() {
        ledger {
            unverifiedTransaction {
                output("state encumbered by 5pm time-lock") { state }
                output("5pm time-lock") { timeLock }
            }
            // The time of the transaction is earlier than the time specified in the encumbering timelock.
            transaction {
                input("state encumbered by 5pm time-lock")
                input("5pm time-lock")
                output { state }
                command(MEGA_CORP.owningKey) { Cash.Commands.Move() }
                timeWindow(FOUR_PM)
                this `fails with` "the time specified in the time-lock has passed"
            }
        }
    }

    @Test
    fun `state must be consumed along with its encumbrance`() {
        ledger {
            unverifiedTransaction {
                output("state encumbered by 5pm time-lock", encumbrance = 1) { state }
                output("5pm time-lock") { timeLock }
            }
            transaction {
                input("state encumbered by 5pm time-lock")
                output { stateWithNewOwner }
                command(MEGA_CORP.owningKey) { Cash.Commands.Move() }
                timeWindow(FIVE_PM)
                this `fails with` "Missing required encumbrance 1 in INPUT"
            }
        }
    }

    @Test
    fun `state cannot be encumbered by itself`() {
        ledger {
            transaction {
                input { state }
                output(encumbrance = 0) { stateWithNewOwner }
                command(MEGA_CORP.owningKey) { Cash.Commands.Move() }
                this `fails with` "Missing required encumbrance 0 in OUTPUT"
            }
        }
    }

    @Test
    fun `encumbrance state index must be valid`() {
        ledger {
            transaction {
                input { state }
                output(encumbrance = 2) { stateWithNewOwner }
                output { timeLock }
                command(MEGA_CORP.owningKey) { Cash.Commands.Move() }
                this `fails with` "Missing required encumbrance 2 in OUTPUT"
            }
        }
    }

    @Test
    fun `correct encumbrance state must be provided`() {
        ledger {
            unverifiedTransaction {
                output("state encumbered by some other state", encumbrance = 1) { state }
                output("some other state") { state }
                output("5pm time-lock") { timeLock }
            }
            transaction {
                input("state encumbered by some other state")
                input("5pm time-lock")
                output { stateWithNewOwner }
                command(MEGA_CORP.owningKey) { Cash.Commands.Move() }
                timeWindow(FIVE_PM)
                this `fails with` "Missing required encumbrance 1 in INPUT"
            }
        }
    }
}
