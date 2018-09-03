package net.corda.notaryhealthcheck.contract

import net.corda.core.contracts.Command
import net.corda.core.contracts.Contract
import net.corda.core.contracts.TypeOnlyCommandData
import net.corda.core.transactions.LedgerTransaction
import java.security.PublicKey

class SchedulingContract : Contract {
    override fun verify(tx: LedgerTransaction) {
    }

    companion object {
        val PROGRAM_ID = SchedulingContract::class.java.name

        object EmptyCommandData : TypeOnlyCommandData()

        fun emptyCommand(vararg signers: PublicKey) = Command<TypeOnlyCommandData>(EmptyCommandData, signers.toList())
    }
}