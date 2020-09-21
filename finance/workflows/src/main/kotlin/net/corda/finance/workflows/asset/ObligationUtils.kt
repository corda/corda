package net.corda.finance.workflows.asset

import net.corda.core.contracts.*
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.NonEmptySet
import net.corda.finance.contracts.NetType
import net.corda.finance.contracts.asset.Obligation
import net.corda.finance.contracts.asset.OnLedgerAsset
import net.corda.finance.contracts.asset.extractAmountsDue
import net.corda.finance.contracts.asset.netAmountsDue
import net.corda.finance.contracts.utils.sumObligations
import java.security.PublicKey
import java.time.Instant
import java.util.*

object ObligationUtils {
    /**
     * Puts together an issuance transaction for the specified currency obligation amount that starts out being owned by
     * the given pubkey.
     *
     * @param tx transaction builder to add states and commands to.
     * @param obligor the party who is expected to pay some currency amount to fulfil the obligation (also the owner of
     * the obligation).
     * @param amount currency amount the obligor is expected to pay.
     * @param dueBefore the date on which the obligation is due. The default time tolerance is used (currently this is
     * 30 seconds).
     * @param beneficiary the party the obligor is expected to pay.
     * @param notary the notary for this transaction's outputs.
     */
    @JvmStatic
    fun generateCashIssue(tx: TransactionBuilder,
                          obligor: AbstractParty,
                          acceptableContract: SecureHash,
                          amount: Amount<Issued<Currency>>,
                          dueBefore: Instant,
                          beneficiary: AbstractParty,
                          notary: Party) {
        val issuanceDef = Obligation.Terms(NonEmptySet.of(acceptableContract), NonEmptySet.of(amount.token), dueBefore)
        OnLedgerAsset.generateIssue(
                tx,
                TransactionState(
                        Obligation.State(Obligation.Lifecycle.NORMAL, obligor, issuanceDef, amount.quantity, beneficiary),
                        Obligation.PROGRAM_ID,
                        notary
                ),
                Obligation.Commands.Issue()
        )
    }

    /**
     * Puts together an issuance transaction for the specified amount that starts out being owned by the given pubkey.
     *
     * @param tx transaction builder to add states and commands to.
     * @param obligor the party who is expected to pay some amount to fulfil the obligation.
     * @param issuanceDef the terms of the obligation, including which contracts and underlying assets are acceptable
     * forms of payment.
     * @param pennies the quantity of the asset (in the smallest normal unit of measurement) owed.
     * @param beneficiary the party the obligor is expected to pay.
     * @param notary the notary for this transaction's outputs.
     */
    fun <P : Any> generateIssue(tx: TransactionBuilder,
                                obligor: AbstractParty,
                                issuanceDef: Obligation.Terms<P>,
                                pennies: Long,
                                beneficiary: AbstractParty,
                                notary: Party): Set<PublicKey> {
        return OnLedgerAsset.generateIssue(
                tx,
                TransactionState(
                        Obligation.State(Obligation.Lifecycle.NORMAL, obligor, issuanceDef, pennies, beneficiary),
                        Obligation.PROGRAM_ID,
                        notary
                ),
                Obligation.Commands.Issue()
        )
    }

    /**
     * Generate a transaction performing close-out netting of two or more states.
     *
     * @param signer the party which will sign the transaction. Must be one of the obligor or beneficiary.
     * @param inputs two or more states, which must be compatible for bilateral netting (same issuance definitions,
     * and same parties involved).
     */
    @JvmStatic
    fun <P : Any> generateCloseOutNetting(tx: TransactionBuilder, signer: AbstractParty, vararg inputs: StateAndRef<Obligation.State<P>>) {
        val states = inputs.map { it.state.data }
        val netState = states.firstOrNull()?.bilateralNetState

        requireThat {
            "at least two states are provided" using (states.size >= 2)
            "all states are in the normal lifecycle state " using (states.all { it.lifecycle == Obligation.Lifecycle.NORMAL })
            "all states must be bilateral nettable" using (states.all { it.bilateralNetState == netState })
            "signer is in the state parties" using (signer in netState!!.partyKeys)
        }

        tx.withItems(*inputs)
        val out = states.reduce(Obligation.State<P>::net)
        if (out.quantity > 0L) {
            tx.addOutputState(out, Obligation.PROGRAM_ID)
        }
        tx.addCommand(Obligation.Commands.Net(NetType.PAYMENT), signer.owningKey)
    }

    @JvmStatic
    fun <P : Any> generatePaymentNetting(tx: TransactionBuilder,
                                         issued: Issued<Obligation.Terms<P>>,
                                         notary: Party,
                                         vararg inputs: StateAndRef<Obligation.State<P>>) {
        val states = inputs.map { it.state.data }
        requireThat {
            "all states are in the normal lifecycle state " using (states.all { it.lifecycle == Obligation.Lifecycle.NORMAL })
        }

        tx.withItems(*inputs)

        val groups = states.groupBy { it.multilateralNetState }
        val partyLookup = HashMap<PublicKey, AbstractParty>()
        val signers = states.map { it.beneficiary }.union(states.map { it.obligor }).toSet()

        // Create a lookup table of the party that each public key represents.
        states.map { it.obligor }.forEach { partyLookup[it.owningKey] = it }

        // Suppress compiler warning as 'groupStates' is an unused variable when destructuring 'groups'.
        @Suppress("UNUSED_VARIABLE")
        for ((netState, groupStates) in groups) {
            // Extract the net balances
            val netBalances = netAmountsDue(extractAmountsDue(issued.product, states.asIterable()))

            netBalances
                    // Convert the balances into obligation state objects
                    .map { entry ->
                        Obligation.State(Obligation.Lifecycle.NORMAL, entry.key.first,
                                netState.template, entry.value.quantity, entry.key.second)
                    }
                    // Add the new states to the TX
                    .forEach { tx.addOutputState(it, Obligation.PROGRAM_ID, notary) }
            tx.addCommand(Obligation.Commands.Net(NetType.PAYMENT), signers.map { it.owningKey })
        }
    }

    /**
     * Generate a transaction changing the lifecycle of one or more state objects.
     *
     * @param statesAndRefs a list of state objects, which MUST all have the same issuance definition. This avoids
     * potential complications arising from different deadlines applying to different states.
     */
    @JvmStatic
    fun <P : Any> generateSetLifecycle(tx: TransactionBuilder,
                             statesAndRefs: List<StateAndRef<Obligation.State<P>>>,
                             lifecycle: Obligation.Lifecycle,
                             notary: Party) {
        val states = statesAndRefs.map { it.state.data }
        val issuanceDef = getTermsOrThrow(states)
        val existingLifecycle = when (lifecycle) {
            Obligation.Lifecycle.DEFAULTED -> Obligation.Lifecycle.NORMAL
            Obligation.Lifecycle.NORMAL -> Obligation.Lifecycle.DEFAULTED
        }
        require(states.all { it.lifecycle == existingLifecycle }) { "initial lifecycle must be $existingLifecycle for all input states" }

        // Produce a new set of states
        val groups = statesAndRefs.groupBy { it.state.data.amount.token }
        for ((_, stateAndRefs) in groups) {
            val partiesUsed = ArrayList<AbstractParty>()
            stateAndRefs.forEach { stateAndRef ->
                val outState = stateAndRef.state.data.copy(lifecycle = lifecycle)
                tx.addInputState(stateAndRef)
                tx.addOutputState(outState, Obligation.PROGRAM_ID, notary)
                partiesUsed.add(stateAndRef.state.data.beneficiary)
            }
            tx.addCommand(Obligation.Commands.SetLifecycle(lifecycle), partiesUsed.map { it.owningKey }.distinct())
        }
        tx.setTimeWindow(issuanceDef.dueBefore, issuanceDef.timeTolerance)
    }

    /** Get the common issuance definition for one or more states, or throw an IllegalArgumentException. */
    private fun <P : Any> getTermsOrThrow(states: Iterable<Obligation.State<P>>) = states.map { it.template }.distinct().single()

    /**
     * @param statesAndRefs a list of state objects, which MUST all have the same aggregate state. This is done as
     * only a single settlement command can be present in a transaction, to avoid potential problems with allocating
     * assets to different obligation issuances.
     * @param assetStatesAndRefs a list of fungible asset state objects, which MUST all be of the same issued product.
     * It is strongly encouraged that these all have the same beneficiary.
     * @param moveCommand the command used to move the asset state objects to their new owner.
     */
    @JvmStatic
    fun <P : Any> generateSettle(tx: TransactionBuilder,
                                 statesAndRefs: Iterable<StateAndRef<Obligation.State<P>>>,
                                 assetStatesAndRefs: Iterable<StateAndRef<FungibleAsset<P>>>,
                                 moveCommand: MoveCommand,
                                 notary: Party) {
        val states = statesAndRefs.map { it.state }
        val obligationIssuer = states.first().data.obligor
        val obligationOwner = states.first().data.beneficiary

        requireThat {
            "all fungible asset states use the same notary" using (assetStatesAndRefs.all { it.state.notary == notary })
            "all obligation states are in the normal state" using (statesAndRefs.all { it.state.data.lifecycle == Obligation.Lifecycle.NORMAL })
            "all obligation states use the same notary" using (statesAndRefs.all { it.state.notary == notary })
            "all obligation states have the same obligor" using (statesAndRefs.all { it.state.data.obligor == obligationIssuer })
            "all obligation states have the same beneficiary" using (statesAndRefs.all { it.state.data.beneficiary == obligationOwner })
        }

        // TODO: A much better (but more complex) solution would be to have two iterators, one for obligations,
        // one for the assets, and step through each in a semi-synced manner. For now however we just bundle all the states
        // on each side together

        val issuanceDef = getIssuanceDefinitionOrThrow(statesAndRefs.map { it.state.data })
        val template: Obligation.Terms<P> = issuanceDef.product
        val obligationTotal: Amount<P> = Amount(states.map { it.data }.sumObligations<P>().quantity, template.product)
        var obligationRemaining: Amount<P> = obligationTotal
        val assetSigners = HashSet<AbstractParty>()

        statesAndRefs.forEach { tx.addInputState(it) }

        // Move the assets to the new beneficiary
        assetStatesAndRefs.forEach { ref ->
            if (obligationRemaining.quantity > 0L) {
                tx.addInputState(ref)

                val assetState = ref.state.data
                val amount = Amount(assetState.amount.quantity, assetState.amount.token.product)
                obligationRemaining -= if (obligationRemaining >= amount) {
                    tx.addOutputState(assetState.withNewOwnerAndAmount(assetState.amount, obligationOwner), Obligation.PROGRAM_ID, notary)
                    amount
                } else {
                    val change = Amount(obligationRemaining.quantity, assetState.amount.token)
                    // Split the state in two, sending the change back to the previous beneficiary
                    tx.addOutputState(assetState.withNewOwnerAndAmount(change, obligationOwner), Obligation.PROGRAM_ID, notary)
                    tx.addOutputState(assetState.withNewOwnerAndAmount(assetState.amount - change, assetState.owner), Obligation.PROGRAM_ID, notary)
                    Amount(0L, obligationRemaining.token)
                }
                assetSigners.add(assetState.owner)
            }
        }

        // If we haven't cleared the full obligation, add the remainder as an output
        if (obligationRemaining.quantity > 0L) {
            tx.addOutputState(Obligation.State(Obligation.Lifecycle.NORMAL, obligationIssuer, template, obligationRemaining.quantity, obligationOwner), Obligation.PROGRAM_ID, notary)
        } else {
            // Destroy all of the states
        }

        // Add the asset move command and obligation settle
        tx.addCommand(moveCommand, assetSigners.map { it.owningKey })
        tx.addCommand(Obligation.Commands.Settle(Amount((obligationTotal - obligationRemaining).quantity, issuanceDef)), obligationIssuer.owningKey)
    }

    /** Get the common issuance definition for one or more states, or throw an IllegalArgumentException. */
    private fun <P : Any> getIssuanceDefinitionOrThrow(states: Iterable<Obligation.State<P>>): Issued<Obligation.Terms<P>> {
        return states.map { it.amount.token }.distinct().single()
    }

    /**
     * Generate an transaction exiting an obligation from the ledger.
     *
     * @param tx transaction builder to add states and commands to.
     * @param amountIssued the amount to be exited, represented as a quantity of issued currency.
     * @param assetStates the asset states to take funds from. No checks are done about ownership of these states, it is
     * the responsibility of the caller to check that they do not exit funds held by others.
     * @return the public keys which must sign the transaction for it to be valid.
     */
    @JvmStatic
    fun <P : Any> generateExit(tx: TransactionBuilder,
                               amountIssued: Amount<Issued<Obligation.Terms<P>>>,
                               assetStates: List<StateAndRef<Obligation.State<P>>>): Set<PublicKey> {
        val changeOwner = assetStates.map { it.state.data.owner }.toSet().firstOrNull() ?: throw InsufficientBalanceException(amountIssued)
        return OnLedgerAsset.generateExit(tx, amountIssued, assetStates, changeOwner,
                deriveState = { state, amount, owner -> state.copy(data = state.data.withNewOwnerAndAmount(amount, owner)) },
                generateMoveCommand = { Obligation.Commands.Move() },
                generateExitCommand = { amount -> Obligation.Commands.Exit(amount) }
        )
    }
}