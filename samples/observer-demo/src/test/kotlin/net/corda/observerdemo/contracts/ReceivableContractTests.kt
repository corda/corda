package net.corda.observerdemo.contracts

import net.corda.core.contracts.*
import net.corda.core.utilities.NonEmptySet
import net.corda.finance.GBP
import net.corda.finance.USD
import net.corda.observerdemo.Observed
import net.corda.testing.*
import org.junit.Test
import java.time.Duration
import java.time.ZoneId

class ReceivableContractTests {
    companion object {
        val inStates = arrayOf(
                Receivable(
                        UniqueIdentifier.fromString("9e688c58-a548-3b8e-af69-c9e1005ad0bf"),
                        DUMMY_NOTARY, // Observer should be distinct from notary, but good enough
                        (TEST_TX_TIME - Duration.ofDays(2)).atZone(ZoneId.of("UTC")),
                        TEST_TX_TIME - Duration.ofDays(1),
                        MEGA_CORP,
                        Amount(1000L, USD),
                        MEGA_CORP
                ),
                Receivable(
                        UniqueIdentifier.fromString("55a54008-ad1b-3589-aa21-0d2629c1df41"),
                        DUMMY_NOTARY, // Observer should be distinct from notary, but good enough
                        (TEST_TX_TIME - Duration.ofDays(2)).atZone(ZoneId.of("UTC")),
                        TEST_TX_TIME - Duration.ofDays(1),
                        MEGA_CORP,
                        Amount(2000L, GBP),
                        MEGA_CORP
                )
        )
    }

    @Test
    fun `observed`() {
        transaction {
            attachment(ReceivableContract.PROGRAM_ID)
            input(ReceivableContract.PROGRAM_ID) { inStates[0] }
            output(ReceivableContract.PROGRAM_ID) { inStates[0].withNewOwner(MINI_CORP).ownableState }
            command(MEGA_CORP_PUBKEY, Commands.Move(null, listOf(Pair(inStates[0].linearId, MINI_CORP))))
            timeWindow(TEST_TX_TIME)
            this `fails with` "Required net.corda.observerdemo.Observed command"

            tweak {
                command(MINI_CORP_PUBKEY, Observed())
                this `fails with` "transaction has been sent to all observers"
            }

            tweak {
                command(DUMMY_NOTARY.owningKey, Observed())
                verifies()
            }
        }
    }

    @Test
    fun `issue`() {
        // Testing that arbitrary new outputs are rejected is covered in trivial()
        transaction {
            attachment(ReceivableContract.PROGRAM_ID)
            output(ReceivableContract.PROGRAM_ID) { inStates[0] }
            command(MEGA_CORP_PUBKEY, Commands.Issue(NonEmptySet.of(inStates[0].linearId)))
            command(DUMMY_NOTARY.owningKey, Observed())
            timeWindow(TEST_TX_TIME)
            verifies()
        }
        transaction {
            attachment(ReceivableContract.PROGRAM_ID)
            output(ReceivableContract.PROGRAM_ID) { inStates[0] }
            command(ALICE_PUBKEY, Commands.Issue(NonEmptySet.of(inStates[0].linearId)))
            command(DUMMY_NOTARY.owningKey, Observed())
            timeWindow(TEST_TX_TIME)
            this `fails with` "the owning keys are a subset of the signing keys"
        }
    }

    @Test
    fun `move`() {
        transaction {
            attachment(ReceivableContract.PROGRAM_ID)
            input(ReceivableContract.PROGRAM_ID) { inStates[0] }
            output(ReceivableContract.PROGRAM_ID) { inStates[0].copy(owner = MINI_CORP) }
            command(DUMMY_NOTARY.owningKey, Observed())
            timeWindow(TEST_TX_TIME)
            tweak {
                command(MEGA_CORP_PUBKEY, Commands.Move(null, listOf(Pair(inStates[0].linearId, MINI_CORP))))
                verifies()
            }
            // Test that moves enforce the correct new owner
            tweak {
                command(MEGA_CORP_PUBKEY, Commands.Move(null, listOf(Pair(inStates[0].linearId, ALICE))))
                this `fails with` "outputs match inputs with expected changes applied"
            }
        }
    }

    @Test
    fun `exit`() {
        // Testing that arbitrary disappearing outputs are rejected is covered in trivial()
        transaction {
            attachment(ReceivableContract.PROGRAM_ID)
            input(ReceivableContract.PROGRAM_ID) { inStates[0] }
            timeWindow(TEST_TX_TIME)
            command(MEGA_CORP_PUBKEY, Commands.Exit(NonEmptySet.of(inStates[0].linearId)))
            command(DUMMY_NOTARY.owningKey, Observed())
            verifies()
        }
        transaction {
            attachment(ReceivableContract.PROGRAM_ID)
            input(ReceivableContract.PROGRAM_ID) { inStates[0] }
            timeWindow(TEST_TX_TIME)
            command(ALICE_PUBKEY, Commands.Exit(NonEmptySet.of(inStates[0].linearId)))
            command(DUMMY_NOTARY.owningKey, Observed())
            this `fails with` "the owning keys are a subset of the signing keys"
        }
    }

    /** Generate a fake state reference for use in testing */
    private fun <T: ContractState> fakeRef(state: T) = StateAndRef(TransactionState(state, ReceivableContract.PROGRAM_ID, DUMMY_NOTARY), StateRef(state.hash(), 0))
}
