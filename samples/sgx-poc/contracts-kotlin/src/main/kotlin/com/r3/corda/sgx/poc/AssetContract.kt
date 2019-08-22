package com.r3.corda.sgx.poc

import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.contracts.requireThat
import net.corda.core.transactions.LedgerTransaction

class AssetContract : Contract {
    companion object {
        val ID = "com.r3.corda.sgx.poc.AssetContract"
    }

    /**
     * The verify() function of all the states' contracts must not throw an exception for a transaction to be
     * considered valid.
     */
    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<Command>()
        when (command.value) {
            is Command.Issue -> requireThat {
                "No inputs" using (tx.inputs.isEmpty())
                "Only one output state should be created." using (tx.outputs.size == 1)
                val out = tx.outputsOfType<Asset>().single()
                "Non empty clause" using (out.quantity >= 0)
            }

            is Command.Transfer -> requireThat {
                val balanceIn = tx.inputsOfType<Asset>().map { it.quantity }.sum()
                val outputQuantities = tx.outputsOfType<Asset>().map { it.quantity }
                "At least one output state" using (outputQuantities.isNotEmpty())
                val balanceOut = outputQuantities.sum()
                "Overall asset quantity should be preserved" using (balanceIn == balanceOut)
                "Cannot generate negative assets" using ((outputQuantities.min() ?: 0)  > 0)
                command
            }
        }
        val requiredSigners = (tx.inputsOfType<Asset>() + tx.outputsOfType<Asset>()).map { it.owner.owningKey }
        requireThat { "Missing signer" using (command.signers.containsAll(requiredSigners)) }
    }

    /**
     * This contract only implements one command, Create.
     */
    sealed class Command : CommandData {
        class Issue: Command()
        class Transfer : Command()
    }
}
