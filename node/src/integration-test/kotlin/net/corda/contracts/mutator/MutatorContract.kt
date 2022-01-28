package net.corda.contracts.mutator

import net.corda.core.contracts.CommandData
import net.corda.core.contracts.CommandWithParties
import net.corda.core.contracts.Contract
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.TransactionState
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.contracts.requireThat
import net.corda.core.identity.AbstractParty
import net.corda.core.internal.Verifier
import net.corda.core.serialization.SerializationContext
import net.corda.core.transactions.LedgerTransaction

class MutatorContract : Contract {
    override fun verify(tx: LedgerTransaction) {
        tx.transform { componentGroups, serializedInputs, serializedReferences ->
            requireThat {
                "component groups are protected" using componentGroups.isImmutableAnd(isEmpty = true)
                "serialized inputs are protected" using serializedInputs.isImmutableAnd(isEmpty = true)
                "serialized references are protected" using serializedReferences.isImmutableAnd(isEmpty = true)
            }
        }

        requireThat {
            "Cannot add/remove inputs" using tx.inputs.isImmutable()
            "Cannot add/remove outputs" using failToMutateOutputs(tx)
            "Cannot add/remove commands" using failToMutateCommands(tx)
            "Cannot add/remove references" using tx.references.isImmutable()
            "Cannot add/remove attachments" using tx.attachments.isImmutableAnd(isEmpty = false)
            "Cannot specialise transaction" using failToSpecialise(tx)
        }

        requireNotNull(tx.networkParameters).also { networkParameters ->
            requireThat {
                "Cannot add/remove notaries" using networkParameters.notaries.isImmutableAnd(isEmpty = false)
                "Cannot add/remove package ownerships" using networkParameters.packageOwnership.isImmutable()
                "Cannot add/remove whitelisted contracts" using networkParameters.whitelistedContractImplementations.isImmutable()
            }
        }
    }

    private fun List<*>.isImmutableAnd(isEmpty: Boolean): Boolean {
        return isImmutable() && (this.isEmpty() == isEmpty)
    }

    private fun List<*>.isImmutable(): Boolean {
        return try {
            @Suppress("platform_class_mapped_to_kotlin")
            (this as java.util.List<*>).clear()
            false
        } catch (e: UnsupportedOperationException) {
            true
        }
    }

    private fun failToMutateOutputs(tx: LedgerTransaction): Boolean {
        val output = tx.outputsOfType<MutateState>().single()
        val mutableOutputs = tx.outputs as MutableList<in TransactionState<ContractState>>
        return try {
            mutableOutputs += TransactionState(MutateState(output.owner), MutatorContract::class.java.name, tx.notary!!, 0)
            false
        } catch (e: UnsupportedOperationException) {
            true
        }
    }

    private fun failToMutateCommands(tx: LedgerTransaction): Boolean {
        val mutate = tx.commands.requireSingleCommand<MutateCommand>()
        val mutableCommands = tx.commands as MutableList<in CommandWithParties<CommandData>>
        return try {
            mutableCommands += CommandWithParties(mutate.signers, emptyList(), MutateCommand())
            false
        } catch (e: UnsupportedOperationException) {
            true
        }
    }

    private fun Map<*, *>.isImmutable(): Boolean {
        return try {
            @Suppress("platform_class_mapped_to_kotlin")
            (this as java.util.Map<*, *>).clear()
            false
        } catch (e: UnsupportedOperationException) {
            true
        }
    }

    private fun failToSpecialise(ltx: LedgerTransaction): Boolean {
        return try {
            ltx.specialise(::ExtraSpecialise)
            false
        } catch (e: IllegalStateException) {
            true
        }
    }

    private class ExtraSpecialise(private val ltx: LedgerTransaction, private val ctx: SerializationContext) : Verifier {
        override fun verify() {
            ltx.inputStates.forEach(::println)
            println(ctx.deserializationClassLoader)
        }
    }

    class MutateState(val owner: AbstractParty) : ContractState {
        override val participants: List<AbstractParty> = listOf(owner)

        @Override
        override fun toString(): String {
            return "All change!"
        }
    }

    class MutateCommand : CommandData
}
