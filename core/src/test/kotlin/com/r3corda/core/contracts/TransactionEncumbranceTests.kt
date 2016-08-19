package com.r3corda.core.contracts

import com.r3corda.contracts.asset.Cash
import com.r3corda.core.crypto.SecureHash
import com.r3corda.core.serialization.OpaqueBytes
import com.r3corda.core.testing.*
import org.junit.Test
import java.security.PublicKey
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.assertFailsWith

val TEST_TIMELOCK_ID = TransactionEncumbranceTests.TestTimeLock()

class TransactionEncumbranceTests {
    val defaultRef = OpaqueBytes(ByteArray(1, {1}))
    val defaultIssuer = MEGA_CORP.ref(defaultRef)
    val encumberedState = Cash.State(
            amount = 1000.DOLLARS `issued by` defaultIssuer,
            owner = DUMMY_PUBKEY_1,
            encumbrance = 1
    )
    val unencumberedState = Cash.State(
            amount = 1000.DOLLARS `issued by` defaultIssuer,
            owner = DUMMY_PUBKEY_1
    )
    val stateWithNewOwner = encumberedState.copy(owner = DUMMY_PUBKEY_2)
    val FOUR_PM = Instant.parse("2015-04-17T16:00:00.00Z")
    val FIVE_PM = FOUR_PM.plus(1, ChronoUnit.HOURS)
    val FIVE_PM_TIMELOCK = TestTimeLock.State(FIVE_PM)


    class TestTimeLock : Contract {
        override val legalContractReference = SecureHash.sha256("TestTimeLock")
        override fun verify(tx: TransactionForContract) {
            val timestamp: Timestamp? = tx.timestamp
            val timeLockCommand = tx.commands.select<TestTimeLock.Commands>().first()
            if (timeLockCommand.value is TestTimeLock.Commands.Exit) {
                val time = timestamp?.before ?: throw IllegalArgumentException("Transactions containing time-locks must be timestamped")
                requireThat {
                    "the time specified in the time-lock has passed" by
                            (time >= tx.inputs.filterIsInstance<TestTimeLock.State>().first().validFrom)
                }
            }
        }
        data class State(
                val validFrom: Instant
        ) : ContractState {
            override val participants: List<PublicKey>
                get() = throw UnsupportedOperationException()
            override val contract: Contract = TEST_TIMELOCK_ID
        }
        interface Commands : CommandData {
            class Issue : TypeOnlyCommandData(), Commands
            class Exit : TypeOnlyCommandData(), Commands
        }
    }

    @Test
    fun trivial() {
        // A transaction containing an input state that is encumbered must fail if the encumbrance is missing on the inputs.
        assertFailsWith(TransactionVerificationException.TransactionMissingEncumbranceException::class) {
            transaction {
                input { encumberedState }
                output { unencumberedState }
                command(DUMMY_PUBKEY_1) { Cash.Commands.Move() }
                this.verifies()
            }
        }
        // An encumbered state must not be encumbered by itself.
        assertFailsWith(TransactionVerificationException.TransactionMissingEncumbranceException::class) {
            transaction {
                input { unencumberedState }
                input { unencumberedState }
                output { unencumberedState }
                // The encumbered state refers to an encumbrance in position 1, so what follows is wrong.
                output { encumberedState }
                command(DUMMY_PUBKEY_1) { Cash.Commands.Move() }
                this.verifies()
            }
        }
        // An encumbered state must not reference an index greater than the size of the output states.
        assertFailsWith(TransactionVerificationException.TransactionMissingEncumbranceException::class) {
            transaction {
                input { unencumberedState }
                // The encumbered state refers to an encumbrance in position 1, so there should be at least 2 outputs.
                output { encumberedState }
                command(DUMMY_PUBKEY_1) { Cash.Commands.Move() }
                this.verifies()
            }
        }

    }

    @Test
    fun testEncumbranceEffects() {
        // A transaction containing an input state that is encumbered must fail if the encumbrance is not in the correct position.
        assertFailsWith(TransactionVerificationException.TransactionMissingEncumbranceException::class) {
            ledger {
                unverifiedTransaction {
                    output("state encumbered by 5pm time-lock") { encumberedState }
                    output { unencumberedState }
                    output("5pm time-lock") { FIVE_PM_TIMELOCK }
                }
                transaction {
                    input("state encumbered by 5pm time-lock")
                    input("5pm time-lock")
                    output { stateWithNewOwner }
                    command(DUMMY_PUBKEY_1) { Cash.Commands.Move() }
                    command(DUMMY_PUBKEY_1) { TestTimeLock.Commands.Exit() }
                    timestamp(FIVE_PM)
                    this.verifies()
                }
            }
        }
        // A transaction containing an input state that is encumbered must fail if the encumbrance is not in the correct transaction.
        assertFailsWith(TransactionVerificationException.TransactionMissingEncumbranceException::class) {
            ledger {
                unverifiedTransaction {
                    output("state encumbered by 5pm time-lock") { encumberedState }
                    output { unencumberedState }
                }
                unverifiedTransaction {
                    output("5pm time-lock") { FIVE_PM_TIMELOCK }
                }
                transaction {
                    input("state encumbered by 5pm time-lock")
                    input("5pm time-lock")
                    output { stateWithNewOwner }
                    command(DUMMY_PUBKEY_1) { Cash.Commands.Move() }
                    command(DUMMY_PUBKEY_1) { TestTimeLock.Commands.Exit() }
                    timestamp(FIVE_PM)
                    this.verifies()
                }
            }
        }
        // A transaction with an input state that is encumbered must succeed if the rules of the encumbrance are met.
        ledger {
            unverifiedTransaction {
                output("state encumbered by 5pm time-lock") { encumberedState }
                output("5pm time-lock") { FIVE_PM_TIMELOCK }
            }
            // Un-encumber the output if the time of the transaction is later than the timelock.
            transaction {
                input("state encumbered by 5pm time-lock")
                input("5pm time-lock")
                output { stateWithNewOwner }
                command(DUMMY_PUBKEY_1) { Cash.Commands.Move() }
                command(DUMMY_PUBKEY_1) { TestTimeLock.Commands.Exit() }
                timestamp(FIVE_PM)
                this.verifies()
            }
        }
        // A transaction with an input state that is encumbered must fail if the rules of the encumbrance are not met.
        ledger {
            unverifiedTransaction {
                output("state encumbered by 5pm time-lock") { encumberedState }
                output("5pm time-lock") { FIVE_PM_TIMELOCK }
            }
            // The time of the transaction is earlier than the time specified in the encumbering timelock.
            transaction {
                input("state encumbered by 5pm time-lock")
                input("5pm time-lock")
                output { stateWithNewOwner }
                command(DUMMY_PUBKEY_1) { Cash.Commands.Move() }
                command(DUMMY_PUBKEY_1) { TestTimeLock.Commands.Exit() }
                timestamp(FOUR_PM)
                this `fails with` "the time specified in the time-lock has passed"
            }
        }
    }
}