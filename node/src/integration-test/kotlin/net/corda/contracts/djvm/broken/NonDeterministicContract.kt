package net.corda.contracts.djvm.broken

import net.corda.core.contracts.Contract
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.TypeOnlyCommandData
import net.corda.core.identity.AbstractParty
import net.corda.core.transactions.LedgerTransaction
import java.time.Instant
import java.util.*

class NonDeterministicContract : Contract {
    override fun verify(tx: LedgerTransaction) {
        when {
            tx.commandsOfType<InstantNow>().isNotEmpty() -> verifyInstantNow()
            tx.commandsOfType<CurrentTimeMillis>().isNotEmpty() -> verifyCurrentTimeMillis()
            tx.commandsOfType<NanoTime>().isNotEmpty() -> verifyNanoTime()
            tx.commandsOfType<RandomUUID>().isNotEmpty() -> UUID.randomUUID()
            tx.commandsOfType<WithReflection>().isNotEmpty() -> verifyNoReflection()
            else -> {}
        }
    }

    private fun verifyInstantNow() {
        Instant.now()
    }

    private fun verifyCurrentTimeMillis() {
        System.currentTimeMillis()
    }

    private fun verifyNanoTime() {
        System.nanoTime()
    }

    private fun verifyNoReflection() {
        Date::class.java.getDeclaredConstructor().newInstance()
    }

    @Suppress("CanBeParameter", "MemberVisibilityCanBePrivate")
    class State(val issuer: AbstractParty) : ContractState {
        override val participants: List<AbstractParty> = listOf(issuer)
    }

    class InstantNow : TypeOnlyCommandData()
    class CurrentTimeMillis : TypeOnlyCommandData()
    class NanoTime : TypeOnlyCommandData()
    class RandomUUID : TypeOnlyCommandData()
    class WithReflection : TypeOnlyCommandData()
    class NoOperation : TypeOnlyCommandData()
}
