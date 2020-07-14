package net.corda.bn.contracts

import net.corda.bn.states.GroupState
import net.corda.bn.states.MembershipState
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.CommandWithParties
import net.corda.core.contracts.Contract
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.contracts.requireThat
import net.corda.core.identity.Party
import net.corda.core.transactions.LedgerTransaction
import java.lang.IllegalArgumentException
import java.security.PublicKey

/**
 * Contract that verifies an evolution of [MembershipState].
 */
open class MembershipContract : Contract {

    companion object {
        const val CONTRACT_NAME = "net.corda.bn.contracts.MembershipContract"
    }

    /**
     * Each new [MembershipContract] command must be wrapped and extend this class.
     *
     * @property requiredSigners List of all required public keys of command's signers.
     */
    open class Commands(val requiredSigners: List<PublicKey>) : CommandData {
        /**
         * Command responsible for pending [MembershipState] issuance.
         *
         * @param requiredSigners List of all required public keys of command's signers.
         */
        class Request(requiredSigners: List<PublicKey>) : Commands(requiredSigners)

        /**
         * Command responsible for [MembershipState] activation.
         *
         * @param requiredSigners List of all required public keys of command's signers.
         * @property bnCreation Flag indicating whether this command is part of transaction creating new Business Network.
         */
        class Activate(requiredSigners: List<PublicKey>, val bnCreation: Boolean = false) : Commands(requiredSigners)

        /**
         * Command responsible for [MembershipState] suspension.
         *
         * @param requiredSigners List of all required public keys of command's signers.
         */
        class Suspend(requiredSigners: List<PublicKey>) : Commands(requiredSigners)

        /**
         * Command responsible for [MembershipState] revocation.
         *
         * @param requiredSigners List of all required public keys of command's signers.
         */
        class Revoke(requiredSigners: List<PublicKey>) : Commands(requiredSigners)

        /**
         * Command responsible for modification of [MembershipState.roles].
         *
         * @param requiredSigners List of all required public keys of command's signers.
         * @property bnCreation Flag indicating whether this command is part of transaction creating new Business Network.
         */
        class ModifyRoles(requiredSigners: List<PublicKey>, val bnCreation: Boolean = false) : Commands(requiredSigners)

        /**
         * Command responsible for modification of [MembershipState.identity.businessIdentity].
         *
         * @param requiredSigners List of all required public keys of command's signers.
         * @property initiator Identity of the party building the transaction.
         */
        class ModifyBusinessIdentity(requiredSigners: List<PublicKey>, val initiator: Party) : Commands(requiredSigners)

        /**
         * Command responsible for modification of [MembershipState.participants].
         *
         * @param requiredSigners List of all required public keys of command's signers.
         */
        class ModifyParticipants(requiredSigners: List<PublicKey>) : Commands(requiredSigners)
    }

    /**
     * Ensures [MembershipState] transition makes sense. Throws exception if there is a problem that should prevent the transition.
     *
     * @param tx Ledger transaction over which contract performs verification.
     */
    @Suppress("ComplexMethod")
    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<Commands>()
        val input = if (tx.inputStates.isNotEmpty()) tx.inputs.single() else null
        val inputState = input?.state?.data as? MembershipState
        val output = if (tx.outputStates.isNotEmpty()) tx.outputs.single() else null
        val outputState = output?.data as? MembershipState

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
                "Required signers should be subset of all output state's participants" using (participants.map { it.owningKey }.containsAll(command.value.requiredSigners))
            }
            if (inputState != null && outputState != null) {
                "Input and output state should have same Corda identity" using (inputState.identity.cordaIdentity == outputState.identity.cordaIdentity)
                "Input and output state should have same network IDs" using (inputState.networkId == outputState.networkId)
                "Input and output state should have same issued timestamps" using (inputState.issued == outputState.issued)
                "Output state's modified timestamp should be greater or equal than input's" using (inputState.modified <= outputState.modified)
                "Input and output state should have same linear IDs" using (inputState.linearId == outputState.linearId)
                "Transaction must be signed by all signers specified inside command" using (command.signers.toSet() == command.value.requiredSigners.toSet())
            }
        }

        when (command.value) {
            is Commands.Request -> verifyRequest(tx, command, outputState!!)
            is Commands.Activate -> verifyActivate(tx, command, inputState!!, outputState!!)
            is Commands.Suspend -> verifySuspend(tx, command, inputState!!, outputState!!)
            is Commands.Revoke -> verifyRevoke(tx, command, inputState!!)
            is Commands.ModifyRoles -> verifyModifyRoles(tx, command, inputState!!, outputState!!)
            is Commands.ModifyBusinessIdentity -> verifyModifyBusinessIdentity(tx, command, inputState!!, outputState!!)
            is Commands.ModifyParticipants -> verifyModifyParticipants(tx, command, inputState!!, outputState!!)
            else -> throw IllegalArgumentException("Unsupported command ${command.value}")
        }
    }

    /**
     * Each contract extending [MembershipContract] must override this method providing associated contract name.
     */
    open fun contractName() = CONTRACT_NAME

    /**
     * Contract verification check specific to [Commands.Request] command. Each contract extending [MembershipContract] can override this
     * method to implement their own custom create command verification logic.
     *
     * @param tx Ledger transaction over which contract performs verification.
     * @param command Command with parties data about membership creation command.
     * @param outputMembership Output [GroupState] of the transaction.
     */
    open fun verifyRequest(tx: LedgerTransaction, command: CommandWithParties<Commands>, outputMembership: MembershipState) = requireThat {
        "Membership request transaction shouldn't contain any inputs" using (tx.inputs.isEmpty())
        "Membership request transaction should contain output state in PENDING status" using (outputMembership.isPending())
        "Membership request transaction should issue membership with empty roles set" using (outputMembership.roles.isEmpty())
        "Pending membership owner should be required signer of membership request transaction" using (outputMembership.identity.cordaIdentity.owningKey in command.value.requiredSigners)
    }

    /**
     * Contract verification check specific to [Commands.Activate] command. Each contract extending [MembershipContract] can override this
     * method to implement their own custom create command verification logic.
     *
     * @param tx Ledger transaction over which contract performs verification.
     * @param command Command with parties data about membership activation command.
     * @param inputMembership Input [GroupState] of the transaction.
     * @param outputMembership Output [GroupState] of the transaction.
     */
    open fun verifyActivate(
            tx: LedgerTransaction,
            command: CommandWithParties<Commands>,
            inputMembership: MembershipState,
            outputMembership: MembershipState
    ) = requireThat {
        "Input state of membership activation transaction shouldn't be already active" using (!inputMembership.isActive())
        "Output state of membership activation transaction should be active" using (outputMembership.isActive())
        "Input and output state of membership activation transaction should have same roles set" using (inputMembership.roles == outputMembership.roles)
        "Input and output state of membership activation transaction should have same business identity" using (inputMembership.identity.businessIdentity == outputMembership.identity.businessIdentity)
        "Input and output state of membership activation transaction should have same participants" using (inputMembership.participants.toSet() == outputMembership.participants.toSet())
        (command.value as Commands.Activate).apply {
            "Input membership owner shouldn't be required signer of membership activation transaction (with an exception of Business Network creation)" using (bnCreation || inputMembership.identity.cordaIdentity.owningKey !in requiredSigners)
        }
    }

    /**
     * Contract verification check specific to [Commands.Suspend] command. Each contract extending [MembershipContract] can override this
     * method to implement their own custom create command verification logic.
     *
     * @param tx Ledger transaction over which contract performs verification.
     * @param command Command with parties data about membership suspension command.
     * @param inputMembership Input [GroupState] of the transaction.
     * @param outputMembership Output [GroupState] of the transaction.
     */
    open fun verifySuspend(
            tx: LedgerTransaction,
            command: CommandWithParties<Commands>,
            inputMembership: MembershipState,
            outputMembership: MembershipState
    ) = requireThat {
        "Input state of membership suspension transaction shouldn't be already suspended" using (!inputMembership.isSuspended())
        "Output state of membership suspension transaction should be suspended" using (outputMembership.isSuspended())
        "Input and output state of membership suspension transaction should have same roles set" using (inputMembership.roles == outputMembership.roles)
        "Input and output state of membership suspension transaction should have same business identity" using (inputMembership.identity.businessIdentity == outputMembership.identity.businessIdentity)
        "Input and output state of membership suspension transaction should have same participants" using (inputMembership.participants.toSet() == outputMembership.participants.toSet())
        "Input membership owner shouldn't be required signer of membership suspension transaction" using (inputMembership.identity.cordaIdentity.owningKey !in command.value.requiredSigners)
    }

    /**
     * Contract verification check specific to [Commands.Revoke] command. Each contract extending [MembershipContract] can override this
     * method to implement their own custom create command verification logic.
     *
     * @param tx Ledger transaction over which contract performs verification.
     * @param command Command with parties data about membership revocation command.
     * @param inputMembership Input [GroupState] of the transaction.
     */
    open fun verifyRevoke(
            tx: LedgerTransaction,
            command: CommandWithParties<Commands>,
            inputMembership: MembershipState
    ) = requireThat {
        "Membership revocation transaction shouldn't contain any outputs" using (tx.outputs.isEmpty())
        "Input membership owner shouldn't be required signer of membership revocation transaction" using (inputMembership.identity.cordaIdentity.owningKey !in command.value.requiredSigners)
    }

    /**
     * Contract verification check specific to [Commands.ModifyRoles] command. Each contract extending [MembershipContract] can override
     * this method to implement their own custom create command verification logic.
     *
     * @param tx Ledger transaction over which contract performs verification.
     * @param command Command with parties data about membership role modification command.
     * @param inputMembership Input [GroupState] of the transaction.
     * @param outputMembership Output [GroupState] of the transaction.
     */
    open fun verifyModifyRoles(
            tx: LedgerTransaction,
            command: CommandWithParties<Commands>,
            inputMembership: MembershipState,
            outputMembership: MembershipState
    ) = requireThat {
        "Input and output state of membership roles modification transaction should have same status" using (inputMembership.status == outputMembership.status)
        "Membership roles modification transaction can only be performed on active or suspended state" using (inputMembership.isActive() || inputMembership.isSuspended())
        "Input and output state of membership roles modification transaction should have different set of roles" using (inputMembership.roles != outputMembership.roles)
        "Input and output state of membership roles modification transaction should have same business identity" using (inputMembership.identity.businessIdentity == outputMembership.identity.businessIdentity)
        "Input and output state of membership roles modification transaction should have same participants" using (inputMembership.participants.toSet() == outputMembership.participants.toSet())
        (command.value as Commands.ModifyRoles).apply {
            "Input membership owner shouldn't be required signer of membership roles modification transaction (with an exception of Business Network creation)" using (bnCreation || inputMembership.identity.cordaIdentity.owningKey !in requiredSigners)
        }
    }

    /**
     * Contract verification check specific to [Commands.ModifyBusinessIdentity] command. Each contract extending [MembershipContract] can
     * override this method to implement their own custom create command verification logic.
     *
     * @param tx Ledger transaction over which contract performs verification.
     * @param command Command with parties data about membership business identity modification command.
     * @param inputMembership Input [GroupState] of the transaction.
     * @param outputMembership Output [GroupState] of the transaction.
     */
    open fun verifyModifyBusinessIdentity(
            tx: LedgerTransaction,
            command: CommandWithParties<Commands>,
            inputMembership: MembershipState,
            outputMembership: MembershipState
    ) = requireThat {
        "Input and output state of membership business identity modification transaction should have same status" using (inputMembership.status == outputMembership.status)
        "Membership business identity modification transaction can only be performed on active or suspended state" using (inputMembership.isActive() || inputMembership.isSuspended())
        "Input and output state of membership business identity modification transaction should have same roles" using (inputMembership.roles == outputMembership.roles)
        "Input and output state of membership business identity modification transaction should have different business identity" using (inputMembership.identity.businessIdentity != outputMembership.identity.businessIdentity)
        "Input and output state of membership business identity modification transaction should have same participants" using (inputMembership.participants.toSet() == outputMembership.participants.toSet())
        (command.value as Commands.ModifyBusinessIdentity).apply {
            val selfModification = initiator == inputMembership.identity.cordaIdentity
            val memberIsSigner = inputMembership.identity.cordaIdentity.owningKey in requiredSigners
            "Input membership owner should be required signer of membership business identity modification transaction if it initiated it" using (!selfModification || memberIsSigner)
            "Input membership owner shouldn't be required signer of membership business identity modification transaction if it didn't initiate it" using (selfModification || !memberIsSigner)
        }
    }

    /**
     * Contract verification check specific to [Commands.ModifyParticipants] command. Each contract extending [MembershipContract] can
     * override this method to implement their own custom create command verification logic.
     *
     * @param tx Ledger transaction over which contract performs verification.
     * @param command Command with parties data about membership participants modification command.
     * @param inputMembership Input [GroupState] of the transaction.
     * @param outputMembership Output [GroupState] of the transaction.
     */
    open fun verifyModifyParticipants(
            tx: LedgerTransaction,
            command: CommandWithParties<Commands>,
            inputMembership: MembershipState,
            outputMembership: MembershipState
    ) = requireThat {
        "Input and output state of membership participants modification transaction should have same status" using (inputMembership.status == outputMembership.status)
        "Membership participants modification transaction can only be performed on active or suspended state" using (inputMembership.isActive() || inputMembership.isSuspended())
        "Input and output state of membership participants modification transaction should have same roles" using (inputMembership.roles == outputMembership.roles)
        "Input and output state of membership participants modification transaction should have same business identity" using (inputMembership.identity.businessIdentity == outputMembership.identity.businessIdentity)
    }
}
