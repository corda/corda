package net.corda.bn.contracts

import net.corda.bn.states.GroupState
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.CommandWithParties
import net.corda.core.contracts.Contract
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.contracts.requireThat
import net.corda.core.transactions.LedgerTransaction
import java.lang.IllegalArgumentException
import java.security.PublicKey

/**
 * Contract that verifies an evolution of [GroupState].
 */
open class GroupContract : Contract {

    companion object {
        const val CONTRACT_NAME = "net.corda.bn.contracts.GroupContract"
    }

    open class Commands(val requiredSigners: List<PublicKey>) : CommandData {
        class Create(requiredSigners: List<PublicKey>) : Commands(requiredSigners)
        class Modify(requiredSigners: List<PublicKey>) : Commands(requiredSigners)
        class Exit(requiredSigners: List<PublicKey>) : Commands(requiredSigners)
    }

    @Suppress("ComplexMethod")
    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<Commands>()
        val input = if (tx.inputStates.isNotEmpty()) tx.inputs.single() else null
        val inputState = input?.state?.data as? GroupState
        val output = if (tx.outputStates.isNotEmpty()) tx.outputs.single() else null
        val outputState = output?.data as? GroupState

        requireThat {
            input?.apply {
                "Input state has to be validated by ${contractName()}" using (state.contract == contractName())
            }
            inputState?.apply {
                "Input state's modified timestamp should be greater or equal to issued timestamp" using (issued <= modified)
            }
            output?.apply {
                "Output state has to be validated by ${contractName()}" using (contract == contractName())
            }
            outputState?.apply {
                "Output state's modified timestamp should be greater or equal to issued timestamp" using (issued <= modified)
            }
            if (inputState != null && outputState != null) {
                "Input and output state should have same network IDs" using (inputState.networkId == outputState.networkId)
                "Input and output state should have same issued timestamps" using (inputState.issued == outputState.issued)
                "Output state's modified timestamp should be greater or equal than input's" using (inputState.modified <= outputState.modified)
                "Input and output state should have same linear IDs" using (inputState.linearId == outputState.linearId)
                "Transaction must be signed by all signers specified inside command" using (command.signers.toSet() == command.value.requiredSigners.toSet())
            }
        }

        when (command.value) {
            is Commands.Create -> verifyCreate(tx, command, outputState!!)
            is Commands.Modify -> verifyModify(tx, command, inputState!!, outputState!!)
            is Commands.Exit -> verifyExit(tx, command, inputState!!)
            else -> throw IllegalArgumentException("Unsupported command ${command.value}")
        }
    }

    open fun contractName() = CONTRACT_NAME

    open fun verifyCreate(tx: LedgerTransaction, command: CommandWithParties<Commands>, outputGroup: GroupState) = requireThat {
        "Membership request transaction shouldn't contain any inputs" using (tx.inputs.isEmpty())
    }

    open fun verifyModify(tx: LedgerTransaction, command: CommandWithParties<Commands>, inputGroup: GroupState, outputGroup: GroupState) = requireThat {
        "Input and output states of group modification transaction should have different name or participants field" using (inputGroup.name != outputGroup.name || inputGroup.participants.toSet() != outputGroup.participants.toSet())
    }

    open fun verifyExit(tx: LedgerTransaction, command: CommandWithParties<Commands>, inputGroup: GroupState) = requireThat {
        "Membership revocation transaction shouldn't contain any outputs" using (tx.outputs.isEmpty())
    }
}