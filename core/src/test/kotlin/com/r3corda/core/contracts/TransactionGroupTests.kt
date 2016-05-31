package com.r3corda.core.contracts

import com.r3corda.core.crypto.Party
import com.r3corda.core.crypto.SecureHash
import com.r3corda.core.node.services.testing.MockStorageService
import com.r3corda.core.testing.*
import org.junit.Test
import java.security.PublicKey
import java.security.SecureRandom
import java.util.Currency
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

val TEST_PROGRAM_ID = TransactionGroupTests.TestCash()

class TransactionGroupTests {
    val A_THOUSAND_POUNDS = TestCash.State(MINI_CORP.ref(1, 2, 3), 1000.POUNDS, MINI_CORP_PUBKEY, DUMMY_NOTARY)

    class TestCash : Contract {
        override val legalContractReference = SecureHash.sha256("TestCash")

        override fun verify(tx: TransactionForVerification) {
        }

        data class State(
                val deposit: PartyAndReference,
                val amount: Amount<Currency>,
                override val owner: PublicKey,
                override val notary: Party) : OwnableState {
            override val contract: Contract = TEST_PROGRAM_ID
            override fun withNewOwner(newOwner: PublicKey) = Pair(Commands.Move(), copy(owner = newOwner))
        }
        interface Commands : CommandData {
            class Move() : TypeOnlyCommandData(), Commands
            data class Issue(val nonce: Long = SecureRandom.getInstanceStrong().nextLong()) : Commands
            data class Exit(val amount: Amount<Currency>) : Commands
        }
    }

    infix fun TestCash.State.`owned by`(owner: PublicKey) = copy(owner = owner)

    @Test
    fun success() {
        transactionGroup {
            roots {
                transaction(A_THOUSAND_POUNDS label "£1000")
            }

            transaction {
                input("£1000")
                output("alice's £1000") { A_THOUSAND_POUNDS `owned by` ALICE_PUBKEY }
                arg(MINI_CORP_PUBKEY) { TestCash.Commands.Move() }
            }

            transaction {
                input("alice's £1000")
                arg(ALICE_PUBKEY) { TestCash.Commands.Move() }
                arg(MINI_CORP_PUBKEY) { TestCash.Commands.Exit(1000.POUNDS) }
            }

            verify()
        }
    }

    @Test
    fun conflict() {
        transactionGroup {
            val t = transaction {
                output("cash") { A_THOUSAND_POUNDS }
                arg(MINI_CORP_PUBKEY) { TestCash.Commands.Issue() }
            }

            val conflict1 = transaction {
                input("cash")
                val HALF = A_THOUSAND_POUNDS.copy(amount = 500.POUNDS) `owned by` BOB_PUBKEY
                output { HALF }
                output { HALF }
                arg(MINI_CORP_PUBKEY) { TestCash.Commands.Move() }
            }

            verify()

            // Alice tries to double spend back to herself.
            val conflict2 = transaction {
                input("cash")
                val HALF = A_THOUSAND_POUNDS.copy(amount = 500.POUNDS) `owned by` ALICE_PUBKEY
                output { HALF }
                output { HALF }
                arg(MINI_CORP_PUBKEY) { TestCash.Commands.Move() }
            }

            assertNotEquals(conflict1, conflict2)

            val e = assertFailsWith(TransactionConflictException::class) {
                verify()
            }
            assertEquals(StateRef(t.id, 0), e.conflictRef)
            assertEquals(setOf(conflict1.id, conflict2.id), setOf(e.tx1.id, e.tx2.id))
        }
    }

    @Test
    fun disconnected() {
        // Check that if we have a transaction in the group that doesn't connect to anything else, it's rejected.
        val tg = transactionGroup {
            transaction {
                output("cash") { A_THOUSAND_POUNDS }
                arg(MINI_CORP_PUBKEY) { TestCash.Commands.Issue() }
            }

            transaction {
                input("cash")
                output { A_THOUSAND_POUNDS `owned by` BOB_PUBKEY }
            }
        }

        // We have to do this manually without the DSL because transactionGroup { } won't let us create a tx that
        // points nowhere.
        val input = generateStateRef()
        tg.txns += TransactionBuilder().apply {
            addInputState(input)
            addOutputState(A_THOUSAND_POUNDS)
            addCommand(TestCash.Commands.Move(), BOB_PUBKEY)
        }.toWireTransaction()

        val e = assertFailsWith(TransactionResolutionException::class) {
            tg.verify()
        }
        assertEquals(e.hash, input.txhash)
    }

    @Test
    fun duplicatedInputs() {
        // Check that a transaction cannot refer to the same input more than once.
        transactionGroup {
            roots {
                transaction(A_THOUSAND_POUNDS label "£1000")
            }

            transaction {
                input("£1000")
                input("£1000")
                output { A_THOUSAND_POUNDS.copy(amount = A_THOUSAND_POUNDS.amount * 2) }
                arg(MINI_CORP_PUBKEY) { TestCash.Commands.Move() }
            }

            assertFailsWith(TransactionConflictException::class) {
                verify()
            }
        }
    }

    @Test
    fun signGroup() {
        val signedTxns: List<SignedTransaction> = transactionGroup {
            transaction {
                output("£1000") { A_THOUSAND_POUNDS }
                arg(MINI_CORP_PUBKEY) { TestCash.Commands.Issue() }
            }

            transaction {
                input("£1000")
                output("alice's £1000") { A_THOUSAND_POUNDS `owned by` ALICE_PUBKEY }
                arg(MINI_CORP_PUBKEY) { TestCash.Commands.Move() }
            }

            transaction {
                input("alice's £1000")
                arg(ALICE_PUBKEY) { TestCash.Commands.Move() }
                arg(MINI_CORP_PUBKEY) { TestCash.Commands.Exit(1000.POUNDS) }
            }
        }.signAll()

        // Now go through the conversion -> verification path with them.
        val ltxns = signedTxns.map {
            it.verifyToLedgerTransaction(MOCK_IDENTITY_SERVICE, MockStorageService().attachments)
        }.toSet()
        TransactionGroup(ltxns, emptySet()).verify()
    }
}