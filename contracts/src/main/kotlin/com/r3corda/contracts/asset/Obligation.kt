package com.r3corda.contracts.asset

import com.google.common.annotations.VisibleForTesting
import com.r3corda.contracts.asset.Obligation.Lifecycle.NORMAL
import com.r3corda.core.contracts.*
import com.r3corda.core.crypto.NullPublicKey
import com.r3corda.core.crypto.Party
import com.r3corda.core.crypto.SecureHash
import com.r3corda.core.crypto.toStringShort
import com.r3corda.core.random63BitValue
import com.r3corda.core.testing.MINI_CORP
import com.r3corda.core.testing.TEST_TX_TIME
import com.r3corda.core.utilities.Emoji
import com.r3corda.core.utilities.NonEmptySet
import com.r3corda.core.utilities.nonEmptySetOf
import java.security.PublicKey
import java.time.Duration
import java.time.Instant
import java.util.*

// Just a fake program identifier for now. In a real system it could be, for instance, the hash of the program bytecode.
val OBLIGATION_PROGRAM_ID = Obligation<Currency>()

/**
 * An obligation contract commits the obligor to delivering a specified amount of a fungible asset (for example the
 * [Cash] contract) at a specified future point in time. Settlement transactions may split and merge contracts across
 * multiple input and output states. The goal of this design is to handle amounts owed, and these contracts are expected
 * to be netted/merged, with settlement only for any remainder amount.
 *
 * @param P the product the obligation is for payment of.
 */
class Obligation<P> : Contract {

    /**
     * TODO:
     * 1) hash should be of the contents, not the URI
     * 2) allow the content to be specified at time of instance creation?
     *
     * Motivation: it's the difference between a state object referencing a programRef, which references a
     * legalContractReference and a state object which directly references both.  The latter allows the legal wording
     * to evolve without requiring code changes. But creates a risk that users create objects governed by a program
     * that is inconsistent with the legal contract
     */
    override val legalContractReference: SecureHash = SecureHash.sha256("https://www.big-book-of-banking-law.example.gov/cash-settlement.html")

    /**
     * Represents where in its lifecycle a contract state is, which in turn controls the commands that can be applied
     * to the state. Most states will not leave the [NORMAL] lifecycle. Note that settled (as an end lifecycle) is
     * represented by absence of the state on transaction output.
     */
    enum class Lifecycle {
        /** Default lifecycle state for a contract, in which it can be settled normally */
        NORMAL,
        /**
         * Indicates the contract has not been settled by its due date. Once in the defaulted state,
         * it can only be reverted to [NORMAL] state by the beneficiary.
         */
        DEFAULTED
    }

    /**
     * Common interface for the state subsets used when determining nettability of two or more states. Exposes the
     * underlying issued thing.
     */
    interface NetState<P> {
        val template: StateTemplate<P>
    }

    /**
     * Subset of state, containing the elements which must match for two obligation transactions to be nettable.
     * If two obligation state objects produce equal bilateral net states, they are considered safe to net directly.
     * Bilateral states are used in close-out netting.
     */
    data class BilateralNetState<P>(
            val partyKeys: Set<PublicKey>,
            override val template: StateTemplate<P>
    ) : NetState<P>

    /**
     * Subset of state, containing the elements which must match for two or more obligation transactions to be candidates
     * for netting (this does not include the checks to enforce that everyone's amounts received are the same at the end,
     * which is handled under the verify() function).
     * In comparison to [BilateralNetState], this doesn't include the parties' keys, as ensuring balances match on
     * input and output is handled elsewhere.
     * Used in cases where all parties (or their proxies) are signing, such as central clearing.
     */
    data class MultilateralNetState<P>(
        override val template: StateTemplate<P>
    ) : NetState<P>

    /**
     * Subset of state, containing the elements specified when issuing a new settlement contract.
     *
     * @param P the product the obligation is for payment of.
     */
    data class StateTemplate<P>(
            /** The hash of the asset contract we're willing to accept in payment for this debt. */
            val acceptableContracts: NonEmptySet<SecureHash>,
            /** The parties whose assets we are willing to accept in payment for this debt. */
            val acceptableIssuedProducts: NonEmptySet<Issued<P>>,

            /** When the contract must be settled by. */
            val dueBefore: Instant,
            val timeTolerance: Duration = Duration.ofSeconds(30)
    ) {
        val product: P
            get() = acceptableIssuedProducts.map { it.product }.toSet().single()
    }

    /**
     * Subset of state, containing the elements specified when issuing a new settlement contract.
     * TODO: This needs to be something common to contracts that we can be obliged to pay, and moved
     * out into core accordingly.
     *
     * @param P the product the obligation is for payment of.
     */
    data class IssuanceDefinition<P>(
            val obligor: Party,
            val template: StateTemplate<P>
    )

    /**
     * A state representing the obligation of one party (obligor) to deliver a specified number of
     * units of an underlying asset (described as issuanceDef.acceptableIssuedProducts) to the beneficiary
     * no later than the specified time.
     *
     * @param P the product the obligation is for payment of.
     */
    data class State<P>(
            var lifecycle: Lifecycle = Lifecycle.NORMAL,
            /** Where the debt originates from (obligor) */
            val obligor: Party,
            val template: StateTemplate<P>,
            val quantity: Long,
            /** The public key of the entity the contract pays to */
            val beneficiary: PublicKey
    ) : FungibleAssetState<P, IssuanceDefinition<P>>, BilateralNettableState<State<P>> {
        val amount: Amount<P>
            get() = Amount(quantity, template.product)
        val aggregateState: IssuanceDefinition<P>
            get() = issuanceDef
        override val productAmount: Amount<P>
            get() = amount
        override val contract = OBLIGATION_PROGRAM_ID
        val acceptableContracts: NonEmptySet<SecureHash>
            get() = template.acceptableContracts
        val acceptableIssuanceDefinitions: NonEmptySet<*>
            get() = template.acceptableIssuedProducts
        val dueBefore: Instant
            get() = template.dueBefore
        override val issuanceDef: IssuanceDefinition<P>
            get() = IssuanceDefinition(obligor, template)
        override val participants: List<PublicKey>
            get() = listOf(obligor.owningKey, beneficiary)
        override val owner: PublicKey
            get() = beneficiary

        override fun move(newAmount: Amount<P>, newOwner: PublicKey): State<P>
                = copy(quantity = newAmount.quantity, beneficiary = newOwner)

        override fun toString() = when (lifecycle) {
            Lifecycle.NORMAL -> "${Emoji.bagOfCash}Debt($amount due $dueBefore to ${beneficiary.toStringShort()})"
            Lifecycle.DEFAULTED -> "${Emoji.bagOfCash}Debt($amount unpaid by $dueBefore to ${beneficiary.toStringShort()})"
        }

        override val bilateralNetState: BilateralNetState<P>
            get() {
                check(lifecycle == Lifecycle.NORMAL)
                return BilateralNetState(setOf(obligor.owningKey, beneficiary), template)
            }
        val multilateralNetState: MultilateralNetState<P>
            get() {
                check(lifecycle == Lifecycle.NORMAL)
                return MultilateralNetState(template)
            }

        override fun net(other: State<P>): State<P> {
            val netA = bilateralNetState
            val netB = other.bilateralNetState
            require(netA == netB) { "net substates of the two state objects must be identical" }

            if (obligor.owningKey == other.obligor.owningKey) {
                // Both sides are from the same obligor to beneficiary
                return copy(quantity = quantity + other.quantity)
            } else {
                // Issuer and beneficiary are backwards
                return copy(quantity = quantity - other.quantity)
            }
        }

        override fun withNewOwner(newOwner: PublicKey) = Pair(Commands.Move(issuanceDef), copy(beneficiary = newOwner))
    }

    /** Interface for commands that apply to states grouped by issuance definition */
    interface IssuanceCommands<P> : CommandData {
        val aggregateState: IssuanceDefinition<P>
    }

    // Just for grouping
    interface Commands : CommandData {
        /**
         * Net two or more obligation states together in a close-out netting style. Limited to bilateral netting
         * as only the beneficiary (not the obligor) needs to sign.
         */
        data class Net(val type: NetType) : Commands

        /**
         * A command stating that a debt has been moved, optionally to fulfil another contract.
         *
         * @param contractHash the contract this move is for the attention of. Only that contract's verify function
         * should take the moved states into account when considering whether it is valid. Typically this will be
         * null.
         */
        data class Move<P>(override val aggregateState: IssuanceDefinition<P>,
                           override val contractHash: SecureHash? = null) : Commands, IssuanceCommands<P>, MoveCommand

        /**
         * Allows new obligation states to be issued into existence: the nonce ("number used once") ensures the
         * transaction has a unique ID even when there are no inputs.
         */
        data class Issue<P>(override val aggregateState: IssuanceDefinition<P>,
                            val nonce: Long = random63BitValue()) : Commands, IssuanceCommands<P>

        /**
         * A command stating that the obligor is settling some or all of the amount owed by transferring a suitable
         * state object to the beneficiary. If this reduces the balance to zero, the state object is destroyed.
         * @see [MoveCommand]
         */
        data class Settle<P>(override val aggregateState: IssuanceDefinition<P>,
                             val amount: Amount<P>) : Commands, IssuanceCommands<P>

        /**
         * A command stating that the beneficiary is moving the contract into the defaulted state as it has not been settled
         * by the due date, or resetting a defaulted contract back to the issued state.
         */
        data class SetLifecycle<P>(override val aggregateState: IssuanceDefinition<P>,
                                   val lifecycle: Lifecycle) : Commands, IssuanceCommands<P> {
            val inverse: Lifecycle
                get() = when (lifecycle) {
                    Lifecycle.NORMAL -> Lifecycle.DEFAULTED
                    Lifecycle.DEFAULTED -> Lifecycle.NORMAL
                }
        }

        /**
         * A command stating that the debt is being released by the beneficiary. Normally would indicate
         * either settlement outside of the ledger, or that the obligor is unable to pay.
         */
        data class Exit<P>(override val aggregateState: IssuanceDefinition<P>,
                           val amount: Amount<P>) : Commands, IssuanceCommands<P>
    }

    /** This is the function EVERYONE runs */
    override fun verify(tx: TransactionForContract) {
        val commands = tx.commands.select<Commands>()

        // Net commands are special, and cross issuance definitions, so handle them first
        val netCommands = commands.select<Commands.Net>()
        if (netCommands.isNotEmpty()) {
            val netCommand = netCommands.single()
            val groups = when (netCommand.value.type) {
                NetType.CLOSE_OUT -> tx.groupStates { it: State<P> -> it.bilateralNetState }
                NetType.PAYMENT -> tx.groupStates { it: State<P> -> it.multilateralNetState }
            }
            for ((inputs, outputs, key) in groups) {
                verifyNetCommand(inputs, outputs, netCommand, key)
            }
        } else {
            val commandGroups = tx.groupCommands<IssuanceCommands<P>, IssuanceDefinition<P>> { it.value.aggregateState }
            // Each group is a set of input/output states with distinct issuance definitions. These types
            // of settlement are not fungible and must be kept separated for bookkeeping purposes.
            val groups = tx.groupStates() { it: State<P> -> it.aggregateState }

            for ((inputs, outputs, key) in groups) {
                // Either inputs or outputs could be empty.
                val obligor = key.obligor

                requireThat {
                    "there are no zero sized outputs" by outputs.none { it.amount.quantity == 0L }
                }

                verifyCommandGroup(tx, commandGroups[key] ?: emptyList(), inputs, outputs, obligor, key)
            }
        }
    }

    private fun verifyCommandGroup(tx: TransactionForContract,
                                   commands: List<AuthenticatedObject<IssuanceCommands<P>>>,
                                   inputs: List<State<P>>,
                                   outputs: List<State<P>>,
                                   obligor: Party,
                                   key: IssuanceDefinition<P>) {
        // We've already pre-grouped by product amongst other fields, and verified above that every state specifies
        // at least one acceptable issuance definition, so we can just use the first issuance definition to
        // determine product
        val issued = key.template.acceptableIssuedProducts.first()

        // Issue, default, net and settle commands are all single commands (there's only ever one of them, and
        // they exclude all other commands).
        val issueCommand = commands.select<Commands.Issue<P>>().firstOrNull()
        val setLifecycleCommand = commands.select<Commands.SetLifecycle<P>>().firstOrNull()
        val settleCommand = commands.select<Commands.Settle<P>>().firstOrNull()

        if (commands.size != 1) {
            // Only commands can be move/exit
            require(commands.map { it.value }.all { it is Commands.Move || it is Commands.Exit })
            { "only move/exit commands can be present along with other obligation commands" }
        }

        // Issue, default and net commands are special, and do not follow normal input/output summing rules, so
        // deal with them first
        if (setLifecycleCommand != null) {
            verifySetLifecycleCommand(inputs, outputs, tx, setLifecycleCommand)
        } else {
            // Only the default command processes inputs/outputs that are not in the normal state
            // TODO: Need to be able to exit defaulted amounts
            requireThat {
                "all inputs are in the normal state " by inputs.all { it.lifecycle == Lifecycle.NORMAL }
                "all outputs are in the normal state " by outputs.all { it.lifecycle == Lifecycle.NORMAL }
            }
            if (issueCommand != null) {
                verifyIssueCommand(inputs, outputs, issueCommand, issued, obligor)
            } else if (settleCommand != null) {
                // Perhaps through an abundance of caution, settlement is enforced as its own command.
                // This could perhaps be merged into verifyBalanceChange() later, however doing so introduces a lot
                // of scope for making it more opaque what's going on in a transaction and whether it's as expected
                // by all parties.
                verifySettleCommand(inputs, outputs, tx, settleCommand, issued, obligor, key)
            } else {
                verifyBalanceChange(inputs, outputs, commands, issued.product, obligor)
            }
        }
    }

    /**
     * Verify simple lifecycle changes for settlement contracts, handling exit and move commands.
     *
     * @param commands a list of commands filtered to those matching issuance definition for the provided inputs and
     * outputs.
     */
    private fun verifyBalanceChange(inputs: List<State<P>>,
                                    outputs: List<State<P>>,
                                    commands: List<AuthenticatedObject<IssuanceCommands<P>>>,
                                    product: P,
                                    obligor: Party) {
        // Sum up how much settlement owed there is in the inputs, and the difference in outputs. The difference should
        // be matched by exit commands representing the extracted amount.

        val inputAmount = inputs.sumObligationsOrNull<P>() ?: throw IllegalArgumentException("there is at least one obligation input for this group")
        val outputAmount = outputs.sumObligationsOrZero(product)

        val exitCommands = commands.select<Commands.Exit<P>>()
        val requiredExitSignatures = HashSet<PublicKey>()
        val amountExitingLedger: Amount<P> = if (exitCommands.isNotEmpty()) {
            require(exitCommands.size == 1) { "There can only be one exit command" }
            val exitCommand = exitCommands.single()
            // If we want to remove debt from the ledger, that must be signed for by the beneficiary. For now we require exit
            // commands to be signed by all input beneficiarys, unlocking the full input amount, rather than trying to detangle
            // exactly who exited what.
            requiredExitSignatures.addAll(inputs.map { it.beneficiary })
            exitCommand.value.amount
        } else {
            Amount(0, product)
        }

        requireThat {
            "there are no zero sized inputs" by inputs.none { it.amount.quantity == 0L }
            "at obligor ${obligor.name} the amounts balance" by
                    (inputAmount == outputAmount + amountExitingLedger)
        }

        verifyMoveCommand<Commands.Move<P>>(inputs, commands)
    }

    /**
     * A default command mutates inputs and produces identical outputs, except that the lifecycle changes.
     */
    @VisibleForTesting
    protected fun verifySetLifecycleCommand(inputs: List<State<P>>,
                                            outputs: List<State<P>>,
                                            tx: TransactionForContract,
                                            setLifecycleCommand: AuthenticatedObject<Commands.SetLifecycle<P>>) {
        // Default must not change anything except lifecycle, so number of inputs and outputs must match
        // exactly.
        require(inputs.size == outputs.size) { "Number of inputs and outputs must match" }

        // If we have an default command, perform special processing: issued contracts can only be defaulted
        // after the due date, and default/reset can only be done by the beneficiary
        val expectedInputLifecycle: Lifecycle = setLifecycleCommand.value.inverse
        val expectedOutputLifecycle: Lifecycle = setLifecycleCommand.value.lifecycle

        // Check that we're past the deadline for ALL involved inputs, and that the output states correspond 1:1
        for ((stateIdx, input) in inputs.withIndex()) {
            val actualOutput = outputs[stateIdx]
            val deadline = input.dueBefore
            val timestamp: TimestampCommand? = tx.timestamp
            val expectedOutput: State<P> = input.copy(lifecycle = expectedOutputLifecycle)

            requireThat {
                "there is a timestamp from the authority" by (timestamp != null)
                "the due date has passed" by (timestamp!!.after?.isAfter(deadline) ?: false)
                "input state lifecycle is correct" by (input.lifecycle == expectedInputLifecycle)
                "output state corresponds exactly to input state, with lifecycle changed" by (expectedOutput == actualOutput)
            }
        }
        val owningPubKeys = inputs.map { it.beneficiary }.toSet()
        val keysThatSigned = setLifecycleCommand.signers.toSet()
        requireThat {
            "the owning keys are the same as the signing keys" by keysThatSigned.containsAll(owningPubKeys)
        }
    }

    @VisibleForTesting
    protected fun verifyIssueCommand(inputs: List<State<P>>,
                                     outputs: List<State<P>>,
                                     issueCommand: AuthenticatedObject<Commands.Issue<P>>,
                                     issued: Issued<P>,
                                     obligor: Party) {
        // If we have an issue command, perform special processing: the group is must have no inputs,
        // and that signatures are present for all obligors.

        val inputAmount: Amount<P> = inputs.sumObligationsOrZero(issued.product)
        val outputAmount: Amount<P> = outputs.sumObligations<P>()
        requireThat {
            "the issue command has a nonce" by (issueCommand.value.nonce != 0L)
            "output states are issued by a command signer" by (obligor in issueCommand.signingParties)
            "output values sum to more than the inputs" by (outputAmount > inputAmount)
            "valid settlement issuance definition is not this issuance definition" by inputs.none { it.issuanceDef in it.acceptableIssuanceDefinitions }
        }
    }

    /**
     * Verify a netting command. This handles both close-out and payment netting.
     */
    @VisibleForTesting
    protected fun verifyNetCommand(inputs: Iterable<State<P>>,
                                   outputs: Iterable<State<P>>,
                                   command: AuthenticatedObject<Commands.Net>,
                                   netState: NetState<P>) {
        // TODO: Can we merge this with the checks for aggregated commands?
        requireThat {
            "all inputs are in the normal state " by inputs.all { it.lifecycle == Lifecycle.NORMAL }
            "all outputs are in the normal state " by outputs.all { it.lifecycle == Lifecycle.NORMAL }
        }

        val template = netState.template
        val product = template.product
        // Create two maps of balances from obligors to beneficiaries, one for input states, the other for output states.
        val inputBalances = extractAmountsDue(product, inputs)
        val outputBalances = extractAmountsDue(product, outputs)

        // Sum the columns of the matrices. This will yield the net amount payable to/from each party to/from all other participants.
        // The two summaries must match, reflecting that the amounts owed match on both input and output.
        requireThat {
            "all input states use the same template" by (inputs.all { it.template == template })
            "all output states use the same template" by (outputs.all { it.template == template })
            "amounts owed on input and output must match" by (sumAmountsDue(inputBalances) == sumAmountsDue(outputBalances))
        }

        // TODO: Handle proxies nominated by parties, i.e. a central clearing service
        val involvedParties = inputs.map { it.beneficiary }.union(inputs.map { it.obligor.owningKey }).toSet()
        when (command.value.type) {
        // For close-out netting, allow any involved party to sign
            NetType.CLOSE_OUT -> require(command.signers.intersect(involvedParties).isNotEmpty()) { "any involved party has signed" }
        // Require signatures from all parties (this constraint can be changed for other contracts, and is used as a
        // placeholder while exact requirements are established), or fail the transaction.
            NetType.PAYMENT -> require(command.signers.containsAll(involvedParties)) { "all involved parties have signed" }
        }
    }

    /**
     * Verify settlement of state objects.
     */
    private fun verifySettleCommand(inputs: List<State<P>>,
                                    outputs: List<State<P>>,
                                    tx: TransactionForContract,
                                    command: AuthenticatedObject<Commands.Settle<P>>,
                                    issued: Issued<P>,
                                    obligor: Party,
                                    key: IssuanceDefinition<P>) {
        val template = key.template
        val inputAmount: Amount<P> = inputs.sumObligationsOrNull<P>() ?: throw IllegalArgumentException("there is at least one obligation input for this group")
        val outputAmount: Amount<P> = outputs.sumObligationsOrZero(issued.product)

        // Sum up all asset state objects that are moving and fulfil our requirements

        // The fungible asset contract verification handles ensuring there's inputs enough to cover the output states,
        // we only care about counting how much is output in this transaction. We then calculate the difference in
        // settlement amounts between the transaction inputs and outputs, and the two must match. No elimination is
        // done of amounts paid in by each beneficiary, as it's presumed the beneficiaries have enough sense to do that
        // themselves. Therefore if someone actually signed the following transaction (using cash just for an example):
        //
        // Inputs:
        //  £1m cash owned by B
        //  £1m owed from A to B
        // Outputs:
        //  £1m cash owned by B
        // Commands:
        //  Settle (signed by A)
        //  Move (signed by B)
        //
        // That would pass this check. Ensuring they do not is best addressed in the transaction generation stage.
        val assetStates = tx.outputs.filterIsInstance<FungibleAssetState<*, *>>()
        val acceptableAssetStates = assetStates
                // TODO: This filter is nonsense, because it just checks there is an asset contract loaded, we need to
                // verify the asset contract is the asset contract we expect.
                // Something like:
                //    attachments.mustHaveOneOf(key.acceptableAssetContract)
                .filter { it.contract.legalContractReference in template.acceptableContracts }
                // Restrict the states to those of the correct issuance definition (this normally
                // covers issued product and obligor, but is opaque to us)
                .filter { it.issuanceDef in template.acceptableIssuedProducts }
        // Catch that there's nothing useful here, so we can dump out a useful error
        requireThat {
            "there are fungible asset state outputs" by (assetStates.size > 0)
            "there are defined acceptable fungible asset states" by (acceptableAssetStates.size > 0)
        }

        val amountReceivedByOwner = acceptableAssetStates.groupBy { it.owner }
        // Note we really do want to search all commands, because we want move commands of other contracts, not just
        // this one.
        val moveCommands = tx.commands.select<MoveCommand>()
        var totalPenniesSettled = 0L
        val requiredSigners = inputs.map { it.obligor.owningKey }.toSet()

        for ((beneficiary, obligations) in inputs.groupBy { it.beneficiary }) {
            val settled = amountReceivedByOwner[beneficiary]?.sumFungibleOrNull<P>()
            if (settled != null) {
                val debt = obligations.sumObligationsOrZero(issued)
                require(settled.quantity <= debt.quantity) { "Payment of $settled must not exceed debt $debt" }
                totalPenniesSettled += settled.quantity
            }
        }

        // Insist that we can be the only contract consuming inputs, to ensure no other contract can think it's being
        // settled as well
        requireThat {
            "all move commands relate to this contract" by (moveCommands.map { it.value.contractHash }
                    .all { it == null || it == legalContractReference })
            "contract does not try to consume itself" by (moveCommands.map { it.value }.filterIsInstance<Commands.Move<P>>()
                    .none { it.aggregateState == key })
            "amounts paid must match recipients to settle" by inputs.map { it.beneficiary }.containsAll(amountReceivedByOwner.keys)
            "signatures are present from all obligors" by command.signers.containsAll(requiredSigners)
            "there are no zero sized inputs" by inputs.none { it.amount.quantity == 0L }
            "at obligor ${obligor.name} the obligations after settlement balance" by
                    (inputAmount == outputAmount + Amount(totalPenniesSettled, issued.product))
        }
    }

    /**
     * Generate a transaction performing close-out netting of two or more states.
     *
     * @param signer the party who will sign the transaction. Must be one of the obligor or beneficiary.
     * @param states two or more states, which must be compatible for bilateral netting (same issuance definitions,
     * and same parties involved).
     */
    fun generateCloseOutNetting(tx: TransactionBuilder,
                                signer: PublicKey,
                                vararg states: State<P>) {
        val netState = states.firstOrNull()?.bilateralNetState

        requireThat {
            "at least two states are provided" by (states.size >= 2)
            "all states are in the normal lifecycle state " by (states.all { it.lifecycle == Lifecycle.NORMAL })
            "all states must be bilateral nettable" by (states.all { it.bilateralNetState == netState })
            "signer is in the state parties" by (signer in netState!!.partyKeys)
        }

        val out = states.reduce { stateA, stateB -> stateA.net(stateB) }
        if (out.quantity > 0L)
            tx.addOutputState(out)
        tx.addCommand(Commands.Net(NetType.PAYMENT), signer)
    }

    /**
     * Puts together an issuance transaction for the specified amount that starts out being owned by the given pubkey.
     */
    fun generateIssue(tx: TransactionBuilder,
                      obligor: Party,
                      issuanceDef: StateTemplate<P>,
                      pennies: Long,
                      beneficiary: PublicKey,
                      notary: Party) {
        check(tx.inputStates().isEmpty())
        check(tx.outputStates().map { it.data }.sumObligationsOrNull<P>() == null)
        val aggregateState = IssuanceDefinition(obligor, issuanceDef)
        tx.addOutputState(State(Lifecycle.NORMAL, obligor, issuanceDef, pennies, beneficiary), notary)
        tx.addCommand(Commands.Issue(aggregateState), obligor.owningKey)
    }

    fun generatePaymentNetting(tx: TransactionBuilder,
                               issued: Issued<P>,
                               notary: Party,
                               vararg states: State<P>) {
        requireThat {
            "all states are in the normal lifecycle state " by (states.all { it.lifecycle == Lifecycle.NORMAL })
        }
        val groups = states.groupBy { it.multilateralNetState }
        val partyLookup = HashMap<PublicKey, Party>()
        val signers = states.map { it.beneficiary }.union(states.map { it.obligor.owningKey }).toSet()

        // Create a lookup table of the party that each public key represents.
        states.map { it.obligor }.forEach { partyLookup.put(it.owningKey, it) }

        for ((netState, groupStates) in groups) {
            // Extract the net balances
            val netBalances = netAmountsDue(extractAmountsDue(issued.product, states.asIterable()))

            netBalances
                    // Convert the balances into obligation state objects
                    .map { entry ->
                        State(Lifecycle.NORMAL, partyLookup[entry.key.first]!!,
                                netState.template, entry.value.quantity, entry.key.second)
                    }
                    // Add the new states to the TX
                    .forEach { tx.addOutputState(it, notary) }
            tx.addCommand(Commands.Net(NetType.PAYMENT), signers.toList())
        }

    }

    /**
     * Generate a transaction changing the lifecycle of one or more state objects.
     *
     * @param statesAndRefs a list of state objects, which MUST all have the same issuance definition. This avoids
     * potential complications arising from different deadlines applying to different states.
     */
    fun generateSetLifecycle(tx: TransactionBuilder,
                             statesAndRefs: List<StateAndRef<State<P>>>,
                             lifecycle: Lifecycle,
                             notary: Party) {
        val states = statesAndRefs.map { it.state.data }
        val issuanceDef = getTemplateOrThrow(states)
        val existingLifecycle = when (lifecycle) {
            Lifecycle.DEFAULTED -> Lifecycle.NORMAL
            Lifecycle.NORMAL -> Lifecycle.DEFAULTED
        }
        require(states.all { it.lifecycle == existingLifecycle }) { "initial lifecycle must be ${existingLifecycle} for all input states" }

        // Produce a new set of states
        val groups = statesAndRefs.groupBy { it.state.data.issuanceDef }
        for ((aggregateState, stateAndRefs) in groups) {
            val partiesUsed = ArrayList<PublicKey>()
            stateAndRefs.forEach { stateAndRef ->
                val outState = stateAndRef.state.data.copy(lifecycle = lifecycle)
                tx.addInputState(stateAndRef)
                tx.addOutputState(outState, notary)
                partiesUsed.add(stateAndRef.state.data.beneficiary)
            }
            tx.addCommand(Commands.SetLifecycle(aggregateState, lifecycle), partiesUsed.distinct())
        }
        tx.setTime(issuanceDef.dueBefore, notary, issuanceDef.timeTolerance)
    }

    /**
     * @param statesAndRefs a list of state objects, which MUST all have the same aggregate state. This is done as
     * only a single settlement command can be present in a transaction, to avoid potential problems with allocating
     * assets to different obligation issuances.
     * @param assetStatesAndRefs a list of fungible asset state objects, which MUST all be of the same issued product.
     * It is strongly encouraged that these all have the same beneficiary.
     * @param moveCommand the command used to move the asset state objects to their new owner.
     */
    fun generateSettle(tx: TransactionBuilder,
                       statesAndRefs: Iterable<StateAndRef<State<P>>>,
                       assetStatesAndRefs: Iterable<StateAndRef<FungibleAssetState<P, *>>>,
                       moveCommand: MoveCommand,
                       notary: Party) {
        val states = statesAndRefs.map { it.state }
        val obligationIssuer = states.first().data.obligor
        val obligationOwner = states.first().data.beneficiary

        requireThat {
            "all fungible asset states use the same notary" by (assetStatesAndRefs.all { it.state.notary == notary })
            "all obligation states are in the normal state" by (statesAndRefs.all { it.state.data.lifecycle == Lifecycle.NORMAL })
            "all obligation states use the same notary" by (statesAndRefs.all { it.state.notary == notary })
            "all obligation states have the same obligor" by (statesAndRefs.all { it.state.data.obligor == obligationIssuer })
            "all obligation states have the same beneficiary" by (statesAndRefs.all { it.state.data.beneficiary == obligationOwner })
        }

        // TODO: A much better (but more complex) solution would be to have two iterators, one for obligations,
        // one for the assets, and step through each in a semi-synced manner. For now however we just bundle all the states
        // on each side together

        val issuanceDef = getIssuanceDefinitionOrThrow(statesAndRefs.map { it.state.data })
        val template = issuanceDef.template
        val obligationTotal: Amount<P> = states.map { it.data }.sumObligations<P>()
        var obligationRemaining: Amount<P> = obligationTotal
        val assetSigners = HashSet<PublicKey>()

        statesAndRefs.forEach { tx.addInputState(it) }

        // Move the assets to the new beneficiary
        assetStatesAndRefs.forEach {
            if (obligationRemaining.quantity > 0L) {
                val assetState = it.state
                tx.addInputState(it)
                if (obligationRemaining >= assetState.data.productAmount) {
                    tx.addOutputState(assetState.data.move(assetState.data.productAmount, obligationOwner), notary)
                    obligationRemaining -= assetState.data.productAmount
                } else {
                    // Split the state in two, sending the change back to the previous beneficiary
                    tx.addOutputState(assetState.data.move(obligationRemaining, obligationOwner), notary)
                    tx.addOutputState(assetState.data.move(assetState.data.productAmount - obligationRemaining, assetState.data.owner), notary)
                    obligationRemaining -= Amount(0L, obligationRemaining.token)
                }
                assetSigners.add(assetState.data.owner)
            }
        }

        // If we haven't cleared the full obligation, add the remainder as an output
        if (obligationRemaining.quantity > 0L) {
            tx.addOutputState(State(Lifecycle.NORMAL, obligationIssuer, template, obligationRemaining.quantity, obligationOwner), notary)
        } else {
            // Destroy all of the states
        }

        // Add the asset move command and obligation settle
        tx.addCommand(moveCommand, assetSigners.toList())
        tx.addCommand(Commands.Settle(issuanceDef, obligationTotal - obligationRemaining), obligationOwner)
    }

    /** Get the common issuance definition for one or more states, or throw an IllegalArgumentException. */
    private fun getIssuanceDefinitionOrThrow(states: Iterable<State<P>>): IssuanceDefinition<P> =
            states.map { it.issuanceDef }.distinct().single()

    /** Get the common issuance definition for one or more states, or throw an IllegalArgumentException. */
    private fun getTemplateOrThrow(states: Iterable<State<P>>): StateTemplate<P> =
            states.map { it.template }.distinct().single()
}


/**
 * Convert a list of settlement states into total from each obligor to a beneficiary.
 *
 * @return a map of obligor/beneficiary pairs to the balance due.
 */
fun <P> extractAmountsDue(product: P, states: Iterable<Obligation.State<P>>): Map<Pair<PublicKey, PublicKey>, Amount<P>> {
    val balances = HashMap<Pair<PublicKey, PublicKey>, Amount<P>>()

    states.forEach { state ->
        val key = Pair(state.obligor.owningKey, state.beneficiary)
        val balance = balances[key] ?: Amount(0L, product)
        balances[key] = balance + state.productAmount
    }

    return balances
}

/**
 * Net off the amounts due between parties.
 */
fun <P> netAmountsDue(balances: Map<Pair<PublicKey, PublicKey>, Amount<P>>): Map<Pair<PublicKey, PublicKey>, Amount<P>> {
    val nettedBalances = HashMap<Pair<PublicKey, PublicKey>, Amount<P>>()

    balances.forEach { balance ->
        val (obligor, beneficiary) = balance.key
        val oppositeKey = Pair(beneficiary, obligor)
        val opposite = (balances[oppositeKey] ?: Amount(0L, balance.value.token))
        // Drop zero balances
        if (balance.value > opposite) {
            nettedBalances[balance.key] = (balance.value - opposite)
        } else if (opposite > balance.value) {
            nettedBalances[oppositeKey] = (opposite - balance.value)
        }
    }

    return nettedBalances
}

/**
 * Calculate the total balance movement for each party in the transaction, based off a summary of balances between
 * each obligor and beneficiary.
 *
 * @param balances payments due, indexed by obligor and beneficiary. Zero balances are stripped from the map before being
 * returned.
 */
fun <P> sumAmountsDue(balances: Map<Pair<PublicKey, PublicKey>, Amount<P>>): Map<PublicKey, Long> {
    val sum = HashMap<PublicKey, Long>()

    // Fill the map with zeroes initially
    balances.keys.forEach {
        sum[it.first] = 0L
        sum[it.second] = 0L
    }

    for ((key, amount) in balances) {
        val (obligor, beneficiary) = key
        // Subtract it from the obligor
        sum[obligor] = sum[obligor]!! - amount.quantity
        // Add it to the beneficiary
        sum[beneficiary] = sum[beneficiary]!! + amount.quantity
    }

    // Strip zero balances
    val iterator = sum.iterator()
    while (iterator.hasNext()) {
        val amount = iterator.next().value
        if (amount == 0L) {
            iterator.remove()
        }
    }

    return sum
}

/** Sums the obligation states in the list, throwing an exception if there are none. All state objects in the list are presumed to be nettable. */
fun <P> Iterable<ContractState>.sumObligations(): Amount<P>
        = filterIsInstance<Obligation.State<P>>().map { it.amount }.sumOrThrow()

/** Sums the obligation states in the list, returning null if there are none. */
fun <P> Iterable<ContractState>.sumObligationsOrNull(): Amount<P>?
        = filterIsInstance<Obligation.State<P>>().filter { it.lifecycle == Obligation.Lifecycle.NORMAL }.map { it.amount }.sumOrNull()

/** Sums the obligation states in the list, returning zero of the given product if there are none. */
fun <P> Iterable<ContractState>.sumObligationsOrZero(product: P): Amount<P>
        = filterIsInstance<Obligation.State<P>>().filter { it.lifecycle == Obligation.Lifecycle.NORMAL }.map { it.amount }.sumOrZero(product)

infix fun <T> Obligation.State<T>.at(dueBefore: Instant) = copy(template = template.copy(dueBefore = dueBefore))
infix fun <T> Obligation.IssuanceDefinition<T>.at(dueBefore: Instant) = copy(template = template.copy(dueBefore = dueBefore))
infix fun <T> Obligation.State<T>.between(parties: Pair<Party, PublicKey>) = copy(obligor = parties.first, beneficiary = parties.second)
infix fun <T> Obligation.State<T>.`owned by`(owner: PublicKey) = copy(beneficiary = owner)
infix fun <T> Obligation.State<T>.`issued by`(party: Party) = copy(obligor = party)
// For Java users:
fun <T> Obligation.State<T>.ownedBy(owner: PublicKey) = copy(beneficiary = owner)
fun <T> Obligation.State<T>.issuedBy(party: Party) = copy(obligor = party)

val Issued<Currency>.OBLIGATION_DEF: Obligation.StateTemplate<Currency>
    get() = Obligation.StateTemplate(nonEmptySetOf(Cash().legalContractReference), nonEmptySetOf(this), TEST_TX_TIME)
val Amount<Issued<Currency>>.OBLIGATION: Obligation.State<Currency>
    get() = Obligation.State(Obligation.Lifecycle.NORMAL, MINI_CORP, token.OBLIGATION_DEF, quantity, NullPublicKey)
