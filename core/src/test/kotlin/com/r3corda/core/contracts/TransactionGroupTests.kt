package com.r3corda.core.contracts

import com.r3corda.core.crypto.Party
import com.r3corda.core.crypto.SecureHash
import com.r3corda.core.crypto.newSecureRandom
import com.r3corda.core.node.services.testing.MockStorageService
import com.r3corda.core.testing.*
import org.junit.Test
import java.security.PublicKey
import java.security.SecureRandom
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

val TEST_PROGRAM_ID = TransactionGroupTests.TestCash()

class TransactionGroupTests {
    val A_THOUSAND_POUNDS = TestCash.State(MINI_CORP.ref(1, 2, 3), 1000.POUNDS, MINI_CORP_PUBKEY)

    class TestCash : Contract {
        override val legalContractReference = SecureHash.sha256("TestCash")

        override fun verify(tx: TransactionForContract) {
        }

        data class State(
                val deposit: PartyAndReference,
                val amount: Amount<Currency>,
                override val owner: PublicKey) : OwnableState {
            override val contract: Contract = TEST_PROGRAM_ID
            override val participants: List<PublicKey>
                get() = listOf(owner)

            override fun withNewOwner(newOwner: PublicKey) = Pair(Commands.Move(), copy(owner = newOwner))
        }

        interface Commands : CommandData {
            class Move() : TypeOnlyCommandData(), Commands
            data class Issue(val nonce: Long = newSecureRandom().nextLong()) : Commands
            data class Exit(val amount: Amount<Currency>) : Commands
        }
    }

    infix fun TestCash.State.`owned by`(owner: PublicKey) = copy(owner = owner)
    infix fun TestCash.State.`with notary`(notary: Party) = TransactionState(this, notary)

    @Test
    fun success() {
        ledger {
            nonVerifiedTransaction {
                output("£1000") { A_THOUSAND_POUNDS }
            }

            transaction {
                input("£1000")
                output("alice's £1000") { A_THOUSAND_POUNDS `owned by` ALICE_PUBKEY }
                command(MINI_CORP_PUBKEY) { TestCash.Commands.Move() }
                this.verifies()
            }

            transaction {
                input("alice's £1000")
                command(ALICE_PUBKEY) { TestCash.Commands.Move() }
                command(MINI_CORP_PUBKEY) { TestCash.Commands.Exit(1000.POUNDS) }
                this.verifies()
            }

            this.verifies()
        }
    }

    @Test
    fun conflict() {
        ledger {
            val t = transaction {
                output("cash") { A_THOUSAND_POUNDS }
                command(MINI_CORP_PUBKEY) { TestCash.Commands.Issue() }
                this.verifies()
            }

            val conflict1 = transaction {
                input("cash")
                val HALF = A_THOUSAND_POUNDS.copy(amount = 500.POUNDS) `owned by` BOB_PUBKEY
                output { HALF }
                output { HALF }
                command(MINI_CORP_PUBKEY) { TestCash.Commands.Move() }
                this.verifies()
            }

            verifies()

            // Alice tries to double spend back to herself.
            val conflict2 = transaction {
                input("cash")
                val HALF = A_THOUSAND_POUNDS.copy(amount = 500.POUNDS) `owned by` ALICE_PUBKEY
                output { HALF }
                output { HALF }
                command(MINI_CORP_PUBKEY) { TestCash.Commands.Move() }
                this.verifies()
            }

            assertNotEquals(conflict1, conflict2)

            val e = assertFailsWith(TransactionConflictException::class) {
                verifies()
            }
            assertEquals(StateRef(t.id, 0), e.conflictRef)
            assertEquals(setOf(conflict1.id, conflict2.id), setOf(e.tx1.id, e.tx2.id))
        }
    }

    @Test
    fun disconnected() {
        // Check that if we have a transaction in the group that doesn't connect to anything else, it's rejected.
        val tg = ledger {
            transaction {
                output("cash") { A_THOUSAND_POUNDS }
                command(MINI_CORP_PUBKEY) { TestCash.Commands.Issue() }
                this.verifies()
            }

            transaction {
                input("cash")
                output { A_THOUSAND_POUNDS `owned by` BOB_PUBKEY }
                this.verifies()
            }
        }

        val input = StateAndRef(A_THOUSAND_POUNDS `with notary` DUMMY_NOTARY, generateStateRef())
        tg.apply {
            transaction {
                assertFailsWith(TransactionResolutionException::class) {
                    input(input.ref)
                }
                this.verifies()
            }
        }
    }

    @Test
    fun duplicatedInputs() {
        // Check that a transaction cannot refer to the same input more than once.
        ledger {
            nonVerifiedTransaction {
                output("£1000") { A_THOUSAND_POUNDS }
            }

            transaction {
                input("£1000")
                input("£1000")
                output { A_THOUSAND_POUNDS.copy(amount = A_THOUSAND_POUNDS.amount * 2) }
                command(MINI_CORP_PUBKEY) { TestCash.Commands.Move() }
                this.verifies()
            }

            assertFailsWith(TransactionConflictException::class) {
                verifies()
            }
        }
    }

    @Test
    fun signGroup() {
        val signedTxns: List<SignedTransaction> = ledger {
            transaction {
                output("£1000") { A_THOUSAND_POUNDS }
                command(MINI_CORP_PUBKEY) { TestCash.Commands.Issue() }
                this.verifies()
            }

            transaction {
                input("£1000")
                output("alice's £1000") { A_THOUSAND_POUNDS `owned by` ALICE_PUBKEY }
                command(MINI_CORP_PUBKEY) { TestCash.Commands.Move() }
                this.verifies()
            }

            transaction {
                input("alice's £1000")
                command(ALICE_PUBKEY) { TestCash.Commands.Move() }
                command(MINI_CORP_PUBKEY) { TestCash.Commands.Exit(1000.POUNDS) }
                this.verifies()
            }
        }.interpreter.wireTransactions.let { signAll(it) }

        // Now go through the conversion -> verification path with them.
        val ltxns = signedTxns.map {
            it.verifyToLedgerTransaction(MOCK_IDENTITY_SERVICE, MockStorageService().attachments)
        }.toSet()
        TransactionGroup(ltxns, emptySet()).verify()
    }
}
