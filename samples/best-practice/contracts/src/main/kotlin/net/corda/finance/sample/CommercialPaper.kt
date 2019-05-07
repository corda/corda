package net.corda.finance.sample

import net.corda.finance.contracts.ICommercialPaperState
import net.corda.core.contracts.*
import net.corda.core.crypto.NullKeys.NULL_PARTY
import net.corda.core.crypto.toStringShort
import net.corda.core.identity.AbstractParty
import net.corda.core.internal.Emoji
import net.corda.core.internal.castIfPossible
import net.corda.core.internal.sumByLong
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.contracts.utils.sumCash
import net.corda.finance.schemas.CommercialPaperSchemaV1
import java.time.Instant
import java.util.*

/**
 * The original demo version of CP has a bug/security issue. Basically, multiple CPs with the same `faceValue` can be redeemed in the same transaction
 * but only enough Cash payed to cover for 1 of them.
 * This is caused by the ambiguity of which Cash pays for which CP.
 *
 * This is a more general shortcoming if the verification is based only on the business values of the states. ( when using `groupStates` )
 *
 * This is a modified copy of the finance CommercialPaper to illustrate how to structure a contract to unambiguously identify individual transitions inside a Transaction.
 *
 * The main difference is that it adds metadata to Commands to identify individual transitions, and does not rely on "grouping" based on state values.
 *
 * It is up to the contract developer to decide which approach is the best fit.
 * The advantage of this approach is that it is a more natural way of reasoning for some problems.
 *
 * The functional difference is that in this version one can add multiple CP Commands to the same transaction. Also one can redeem or issue multiple states at the same time.
 */
class CommercialPaper : Contract {

    /**
     * Nothing to see here. Same as the original version.
     *
     * No need to use @BelongsToContract as the state is defined inside the Contract.
     */
    data class State(
            val issuance: PartyAndReference,
            override val owner: AbstractParty,
            val faceValue: Amount<Issued<Currency>>,
            val maturityDate: Instant
    ) : OwnableState, QueryableState, ICommercialPaperState {
        override val participants = listOf(owner)

        @Deprecated("")
        override fun withNewOwner(newOwner: AbstractParty) = CommandAndState(Commands.Move(-1, -1), copy(owner = newOwner))

        fun withoutOwner() = copy(owner = NULL_PARTY)
        override fun toString() = "${Emoji.newspaper}CommercialPaper(of $faceValue redeemable on $maturityDate by '$issuance', owned by $owner)"

        // Although kotlin is smart enough not to need these, as we are using the ICommercialPaperState, we need to declare them explicitly for use later,
        override fun withOwner(newOwner: AbstractParty): ICommercialPaperState = copy(owner = newOwner)

        override fun withFaceValue(newFaceValue: Amount<Issued<Currency>>): ICommercialPaperState = copy(faceValue = newFaceValue)
        override fun withMaturityDate(newMaturityDate: Instant): ICommercialPaperState = copy(maturityDate = newMaturityDate)

        /** Object Relational Mapping support. */
        override fun supportedSchemas(): Iterable<MappedSchema> = listOf(CommercialPaperSchemaV1)
        /** Additional used schemas would be added here (eg. CommercialPaperV2, ...) */

        /** Object Relational Mapping support. */
        override fun generateMappedObject(schema: MappedSchema): PersistentState {
            return when (schema) {
                is CommercialPaperSchemaV1 -> CommercialPaperSchemaV1.PersistentCommercialPaperState(
                        issuancePartyHash = this.issuance.party.owningKey.toStringShort(),
                        issuanceRef = this.issuance.reference.bytes,
                        ownerHash = this.owner.owningKey.toStringShort(),
                        maturity = this.maturityDate,
                        faceValue = this.faceValue.quantity,
                        currency = this.faceValue.token.product.currencyCode,
                        faceValueIssuerPartyHash = this.faceValue.token.issuer.party.owningKey.toStringShort(),
                        faceValueIssuerRef = this.faceValue.token.issuer.reference.bytes
                )
                /** Additional schema mappings would be added here (eg. CommercialPaperV2, ...) */
                else -> throw IllegalArgumentException("Unrecognised schema $schema")
            }
        }

        /** @suppress */
        infix fun `owned by`(owner: AbstractParty) = copy(owner = owner)
    }

    /**
     * Each command will uniquely identify a transition.
     * Based on the type of command, it will contain all the necessary information to reconstruct the transition.
     *
     * This can also contain attachment index(es), reference state index(es), other states or transitions that are related.
     *
     * Obviously, once all transitions have been identified the logic must check that there's no free floating states, or that the same state is part of 2 transitions.
     */
    sealed class Commands : CommandWithMetadata {
        data class Move(val input: Int, val output: Int) : Commands() {
            override val inputs: List<Int> get() = listOf(input)
            override val outputs: List<Int> get() = listOf(output)
        }

        data class Redeem(override val inputs: List<Int>, val paymentOutputs: List<Int>) : Commands() {
            override val outputs: List<Int> get() = emptyList()
        }

        data class Issue(override val outputs: List<Int>) : Commands() {
            override val inputs: List<Int> get() = emptyList()
        }
    }

    /**
     * Utilities to build unambiguous transactions.
     * They add metadata to the commands that will be used by the verification logic to build transitions.
     */
    fun TransactionBuilder.issue(outputs: List<State>): TransactionBuilder = this.apply {
        val startIdx = this.outputStates().size - 1
        outputs.forEach { addOutputState(it) }
        // TODO fail early if the outputs have different owners
        addCommand(Commands.Issue(outputs.mapIndexed { idx, _ -> startIdx + idx }), outputs.first().owner.owningKey)
    }

    fun TransactionBuilder.move(input: StateAndRef<State>, output: State): TransactionBuilder = this.apply {
        addInputState(input)
        addOutputState(output)
        addCommand(Commands.Move(inputStates().size - 1, outputStates().size - 1), output.owner.owningKey)
    }

    fun TransactionBuilder.redeem(inputs: List<StateAndRef<State>>, paymentStates: List<Int>): TransactionBuilder = this.apply {
        val startIdx = this.inputStates().size - 1
        inputs.forEach { addInputState(it) }
        // TODO fail early if the inputs have different owners
        addCommand(Commands.Redeem(inputs.mapIndexed { idx, _ -> startIdx + idx }, paymentStates), inputs.first().state.data.owner.owningKey)
    }


    // The 3 business rules for the 3 types of possible transitions.
    data class MoveTransition(val command: Command<Commands.Move>, val input: StateAndRef<State>, val output: State) : Transition {
        override fun check(tx: LedgerTransaction) {
            requireThat {
                "the transaction is signed by the owner of the CP" using (input.state.data.owner.owningKey in command.signers)
                "the CP state is propagated" using (input.state.data.withoutOwner() == output.withoutOwner())
            }
        }
    }

    data class IssueTransition(val command: Command<Commands.Issue>, val outputs: List<State>) : Transition {
        override fun check(tx: LedgerTransaction) {
            val time = tx.timeWindow?.untilTime ?: throw IllegalArgumentException("Issuances have a time-window")
            requireThat {
                "at least one state issued" using (outputs.isNotEmpty())
                // Don't allow people to issue commercial paper under other entities identities.
                "output states are issued by a command signer" using
                        (outputs.all { it.issuance.party.owningKey in command.signers })
                "output values sum to more than the inputs" using (outputs.sumByLong { it.faceValue.quantity } > 0)
                outputs.forEach {
                    "the maturity date is not in the past" using (time < it.maturityDate)
                }
            }
        }
    }

    /**
     * Multiple papers can be redeemed in one transition as long as they have the same owner.
     *
     * [payments] - list of cash states payed to the owner.
     *
     * Note: There is a later cross-transition overlap check to make sure that these cash states are only used to pay for this transition.
     */
    data class RedeemTransition(val command: Command<Commands.Redeem>, val inputs: List<StateAndRef<State>>, val payments: List<Cash.State>) : Transition {
        override fun check(tx: LedgerTransaction) {
            val time = tx.timeWindow?.fromTime ?: throw IllegalArgumentException("Redemptions must have a time-window")

            // all inputs have the same owner
            val cpOwner = inputs.map { it.state.data.owner }.toSet().single()
            val newCashOwner = payments.map { it.owner }.toSet().single()

            // Redemption of the paper requires movement of on-ledger cash.
            val cashReceived = payments.sumCash()

            val totalCPFaceValue = inputs.drop(1)
                    .fold(inputs.first().state.data.faceValue) { sum, input -> sum + input.state.data.faceValue }

            requireThat {
                inputs.forEach { input ->
                    "the paper must have matured" using (time >= input.state.data.maturityDate)
                }
                "the received amount equals the total face value" using (cashReceived == totalCPFaceValue)
                "the transaction is signed by the owner of the CP" using (cpOwner.owningKey in command.signers)
                "the cash is transferred to the owner of the CP" using (cpOwner == newCashOwner)
            }
        }
    }

    /**
     * Splits a transaction into individual transitions.
     * This reconstructs the transitions only from the Command meta-data.
     * If the command does not hold valid information for the transition, it will fail at this step.
     */
    private fun LedgerTransaction.extractTransitions(commands: List<Command<Commands>>) = commands.map { cmd ->
        val command = cmd.value
        when (command) {
            is Commands.Move -> MoveTransition(cmd as Command<Commands.Move>, this.inputs[command.input] as StateAndRef<State>, this.outputStates[command.output] as State)
            is Commands.Issue -> IssueTransition(cmd as Command<Commands.Issue>, command.outputs.map { this.outputStates[it] as State })
            is Commands.Redeem -> RedeemTransition(cmd as Command<Commands.Redeem>, command.inputs.map { this.inputs[it] as StateAndRef<State> }, command.paymentOutputs.map { this.outputStates[it] as Cash.State })
        }
    }

    /**
     * This
     */
    override fun verify(tx: LedgerTransaction) {
        val cpCommands = tx.commandsOfType<Commands>()

        // 1. Identify the transitions and verify them independently.
        val transitions = tx.extractTransitions(cpCommands)
        for (transition in transitions) {
            transition.check(tx)
        }

        // 2. Make sure there's no conflicting transitions. (This is just an example)
        requireThat {
            "cannot issue and redeem CP in the same transaction" using (transitions.any { it is RedeemTransition } && transitions.any { it is IssueTransition })
        }

        // 3. Ensure there are no free floating CP states and that the same state is not used in multiple transitions.
        tx.checkNoFreeFloatingStates<State, Commands>(cpCommands.map { it.value })

        // 4. Additional cross transition overlap check.
        val payments = tx.commandsOfType<Commands.Redeem>().flatMap { it.value.paymentOutputs }
        requireThat {
            "didn't pay multiple CPs with the same money" using (payments.noDuplicates())
        }
    }
}

/**
 * Will be used to verify transitions independently.
 */
interface Transition {
    fun check(tx: LedgerTransaction)
}

interface CommandWithMetadata : CommandData {
    val inputs: List<Int>
    val outputs: List<Int>
}

inline fun <reified S : ContractState, C : CommandWithMetadata> LedgerTransaction.checkNoFreeFloatingStates(commands: List<C>) {
    val allInputs = this.inputIdxsOfType<S>()
    val allOutputs = this.outputIdxsOfType<S>()

    val allReferredInputs = commands.flatMap { it.inputs }
    val allReferredOutputs = commands.flatMap { it.outputs }

    requireThat {
        "input states not referenced by any command" using (allReferredInputs.sorted() == allInputs.sorted())
        "output states not referenced by any command" using (allReferredOutputs.sorted() == allOutputs.sorted())
        "input state referred by multiple transitions" using (allReferredInputs.noDuplicates())
        "output state referred by multiple transitions" using (allReferredOutputs.noDuplicates())
    }
}

// Utilities.
fun <T : ContractState> LedgerTransaction.inputIdxsOfType(clazz: Class<T>): List<Int> = inputs.mapIndexedNotNull { idx, it ->
    clazz.castIfPossible(it.state.data)?.let { idx }
}

inline fun <reified T : ContractState> LedgerTransaction.inputIdxsOfType(): List<Int> = inputIdxsOfType(T::class.java)

fun <T : ContractState> LedgerTransaction.outputIdxsOfType(clazz: Class<T>): List<Int> = outputs.mapIndexedNotNull { idx, it ->
    clazz.castIfPossible(it.data)?.let { idx }
}

inline fun <reified T : ContractState> LedgerTransaction.outputIdxsOfType(): List<Int> = outputIdxsOfType(T::class.java)

fun Collection<*>.noDuplicates() = this.toSet().size == this.size