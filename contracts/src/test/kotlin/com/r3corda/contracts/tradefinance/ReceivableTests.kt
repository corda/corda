package com.r3corda.contracts.tradefinance

import com.r3corda.contracts.asset.DUMMY_CASH_ISSUER
import com.r3corda.core.contracts.*
import com.r3corda.core.serialization.OpaqueBytes
import com.r3corda.core.utilities.NonEmptySet
import com.r3corda.core.utilities.TEST_TX_TIME
import com.r3corda.testing.*
import org.junit.Test
import java.time.Duration
import java.time.ZoneId
import java.util.*

class ReceivableTests {
    val inStates = arrayOf(
            Receivable.State(
                    UniqueIdentifier.fromString("9e688c58-a548-3b8e-af69-c9e1005ad0bf"),
                    (TEST_TX_TIME - Duration.ofDays(2)).atZone(ZoneId.of("UTC")),
                    TEST_TX_TIME - Duration.ofDays(1),
                    ALICE,
                    BOB,
                    OpaqueBytes(ByteArray(1, { 1 })),
                    OpaqueBytes(ByteArray(1, { 2 })),
                    Amount<Issued<Currency>>(1000L, USD `issued by` DUMMY_CASH_ISSUER),
                    emptySet(),
                    emptyList(),
                    MEGA_CORP_PUBKEY
            ),
            Receivable.State(
                    UniqueIdentifier.fromString("55a54008-ad1b-3589-aa21-0d2629c1df41"),
                    (TEST_TX_TIME - Duration.ofDays(2)).atZone(ZoneId.of("UTC")),
                    TEST_TX_TIME - Duration.ofDays(1),
                    ALICE,
                    BOB,
                    OpaqueBytes(ByteArray(1, { 3 })),
                    OpaqueBytes(ByteArray(1, { 4 })),
                    Amount<Issued<Currency>>(2000L, GBP `issued by` DUMMY_CASH_ISSUER),
                    emptySet(),
                    emptyList(),
                    MEGA_CORP_PUBKEY
            )
    )

    @Test
    fun trivial() {
        transaction {
            input { inStates[0] }
            timestamp(TEST_TX_TIME)
            this `fails with` "Inputs and outputs must match unless commands indicate otherwise"

            tweak {
                output { inStates[0] }
                verifies()
            }

            tweak {
                output { inStates[1] }
                this `fails with` "Inputs and outputs must match unless commands indicate otherwise"
            }
        }

        transaction {
            output { inStates[0] }
            timestamp(TEST_TX_TIME)
            this `fails with` "Inputs and outputs must match unless commands indicate otherwise"
        }
    }

    @Test
    fun `order and uniqueness is enforced`() {
        transaction {
            input { inStates[0] }
            input { inStates[1] }
            output { inStates[0] }
            output { inStates[1] }
            timestamp(TEST_TX_TIME)
            verifies()
        }

        transaction {
            input { inStates[1] }
            input { inStates[0] }
            output { inStates[0] }
            output { inStates[1] }
            timestamp(TEST_TX_TIME)
            this `fails with` "receivables are ordered and unique"
        }

        transaction {
            input { inStates[0] }
            input { inStates[0] }
            output { inStates[0] }
            output { inStates[0] }
            timestamp(TEST_TX_TIME)
            this `fails with` "receivables are ordered and unique"
        }
    }

    @Test
    fun `issue`() {
        // Testing that arbitrary new outputs are rejected is covered in trivial()
        transaction {
            output { inStates[0] }
            command(MEGA_CORP_PUBKEY, Receivable.Commands.Issue(NonEmptySet(inStates[0].linearId)))
            timestamp(TEST_TX_TIME)
            verifies()
        }
        transaction {
            output { inStates[0] }
            command(ALICE_PUBKEY, Receivable.Commands.Issue(NonEmptySet(inStates[0].linearId)))
            timestamp(TEST_TX_TIME)
            this `fails with` "the owning keys are the same as the signing keys"
        }
    }

    @Test
    fun `move`() {
        transaction {
            input { inStates[0] }
            output { inStates[0].copy(owner = MINI_CORP_PUBKEY) }
            timestamp(TEST_TX_TIME)
            this `fails with` "Inputs and outputs must match unless commands indicate otherwise"
            tweak {
                command(MEGA_CORP_PUBKEY, Receivable.Commands.Move(null, mapOf(Pair(inStates[0].linearId, MINI_CORP_PUBKEY))))
                verifies()
            }
            // Test that moves enforce the correct new owner
            tweak {
                command(MEGA_CORP_PUBKEY, Receivable.Commands.Move(null, mapOf(Pair(inStates[0].linearId, ALICE_PUBKEY))))
                this `fails with` "outputs match inputs with expected changes applied"
            }
        }
    }

    @Test
    fun `exit`() {
        // Testing that arbitrary disappearing outputs are rejected is covered in trivial()
        transaction {
            input { inStates[0] }
            timestamp(TEST_TX_TIME)
            command(MEGA_CORP_PUBKEY, Receivable.Commands.Exit(NonEmptySet(inStates[0].linearId)))
            verifies()
        }
        transaction {
            input { inStates[0] }
            timestamp(TEST_TX_TIME)
            command(ALICE_PUBKEY, Receivable.Commands.Exit(NonEmptySet(inStates[0].linearId)))
            this `fails with` "the owning keys are the same as the signing keys"
        }
    }

    // TODO: Test adding and removing notices
}