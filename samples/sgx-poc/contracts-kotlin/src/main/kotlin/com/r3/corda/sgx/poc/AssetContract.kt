package com.r3.corda.sgx.poc.contracts

import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.contracts.requireThat
import net.corda.core.transactions.LedgerTransaction

class AssetContract : Contract {
    companion object {
        val ID = "com.r3.corda.sgx.poc.contracts.AssetContract"
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
                val output = tx.outputsOfType<Asset>().single()
                "Issuer and owner cannot be different" using (output.owner == output.issuer)
            }

            is Command.Transfer -> requireThat {
                val inputs = tx.inputsOfType<Asset>()
                val outputs = tx.outputsOfType<Asset>()
                "One input state" using (inputs.size == 1)
                "One output state" using (outputs.size == 1)
                val input = inputs.single()
                val output = outputs.single()
                "Same issuer" using (input.issuer == output.issuer)
                "Same id" using (input.id == output.id)
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
