/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.core.transactions

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.contracts.Contract
import net.corda.core.contracts.ContractState
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

val TEST_TIMELOCK_ID = "net.corda.core.transactions.TransactionEncumbranceTests\$DummyTimeLock"

class TransactionEncumbranceTests {
    private companion object {
        val DUMMY_NOTARY = TestIdentity(DUMMY_NOTARY_NAME, 20).party
        val megaCorp = TestIdentity(CordaX500Name("MegaCorp", "London", "GB"))
        val MINI_CORP = TestIdentity(CordaX500Name("MiniCorp", "London", "GB")).party
        val MEGA_CORP get() = megaCorp.party
        val MEGA_CORP_PUBKEY get() = megaCorp.publicKey
    }

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()
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

    private val ledgerServices = MockServices(listOf("net.corda.core.transactions", "net.corda.finance.contracts.asset"), MEGA_CORP.name,
            rigorousMock<IdentityServiceInternal>().also {
                doReturn(MEGA_CORP).whenever(it).partyFromKey(MEGA_CORP_PUBKEY)
            })

    @Test
    fun `state can be encumbered`() {
        ledgerServices.ledger(DUMMY_NOTARY) {
            transaction {
                attachments(Cash.PROGRAM_ID, TEST_TIMELOCK_ID)
                input(Cash.PROGRAM_ID, state)
                output(Cash.PROGRAM_ID, encumbrance = 1, contractState = stateWithNewOwner)
                output(TEST_TIMELOCK_ID, "5pm time-lock", timeLock)
                command(MEGA_CORP.owningKey, Cash.Commands.Move())
                verifies()
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
                output(TEST_TIMELOCK_ID, "5pm time-lock", timeLock)
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
                output(Cash.PROGRAM_ID, encumbrance = 0, contractState = stateWithNewOwner)
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
                output(TEST_TIMELOCK_ID, encumbrance = 2, contractState = stateWithNewOwner)
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
                output(Cash.PROGRAM_ID, "some other state", state)
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
