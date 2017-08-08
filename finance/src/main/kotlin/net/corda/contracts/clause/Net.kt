package net.corda.contracts.clause

import net.corda.core.internal.VisibleForTesting
import net.corda.contracts.NetCommand
import net.corda.contracts.NetType
import net.corda.contracts.asset.Obligation
import net.corda.contracts.asset.extractAmountsDue
import net.corda.contracts.asset.sumAmountsDue
import net.corda.core.contracts.*
import net.corda.core.contracts.clauses.Clause
import net.corda.core.identity.AbstractParty
import net.corda.core.transactions.LedgerTransaction
import java.security.PublicKey

/**
 * Common interface for the state subsets used when determining nettability of two or more states. Exposes the
 * underlying issued thing.
 */
interface NetState<P : Any> {
    val template: Obligation.Terms<P>
}

/**
 * Subset of state, containing the elements which must match for two obligation transactions to be nettable.
 * If two obligation state objects produce equal bilateral net states, they are considered safe to net directly.
 * Bilateral states are used in close-out netting.
 */
data class BilateralNetState<P : Any>(
        val partyKeys: Set<AbstractParty>,
        override val template: Obligation.Terms<P>
) : NetState<P>

/**
 * Subset of state, containing the elements which must match for two or more obligation transactions to be candidates
 * for netting (this does not include the checks to enforce that everyone's amounts received are the same at the end,
 * which is handled under the verify() function).
 * In comparison to [BilateralNetState], this doesn't include the parties' keys, as ensuring balances match on
 * input and output is handled elsewhere.
 * Used in cases where all parties (or their proxies) are signing, such as central clearing.
 */
data class MultilateralNetState<P : Any>(
        override val template: Obligation.Terms<P>
) : NetState<P>

/**
 * Clause for netting contract states. Currently only supports obligation contract.
 */
// TODO: Make this usable for any nettable contract states
open class NetClause<C : CommandData, P : Any> : Clause<ContractState, C, Unit>() {
    override val requiredCommands: Set<Class<out CommandData>> = setOf(Obligation.Commands.Net::class.java)

    @Suppress("ConvertLambdaToReference")
    override fun verify(tx: LedgerTransaction,
                        inputs: List<ContractState>,
                        outputs: List<ContractState>,
                        commands: List<AuthenticatedObject<C>>,
                        groupingKey: Unit?): Set<C> {
        val matchedCommands: List<AuthenticatedObject<C>> = commands.filter { it.value is NetCommand }
        val command = matchedCommands.requireSingleCommand<Obligation.Commands.Net>()
        val groups = when (command.value.type) {
            NetType.CLOSE_OUT -> tx.groupStates { it: Obligation.State<P> -> it.bilateralNetState }
            NetType.PAYMENT -> tx.groupStates { it: Obligation.State<P> -> it.multilateralNetState }
        }
        for ((groupInputs, groupOutputs, key) in groups) {
            verifyNetCommand(groupInputs, groupOutputs, command, key)
        }
        return matchedCommands.map { it.value }.toSet()
    }

    /**
     * Verify a netting command. This handles both close-out and payment netting.
     */
    @VisibleForTesting
    fun verifyNetCommand(inputs: List<Obligation.State<P>>,
                         outputs: List<Obligation.State<P>>,
                         command: AuthenticatedObject<NetCommand>,
                         netState: NetState<P>) {
        val template = netState.template
        // Create two maps of balances from obligors to beneficiaries, one for input states, the other for output states.
        val inputBalances = extractAmountsDue(template, inputs)
        val outputBalances = extractAmountsDue(template, outputs)

        // Sum the columns of the matrices. This will yield the net amount payable to/from each party to/from all other participants.
        // The two summaries must match, reflecting that the amounts owed match on both input and output.
        requireThat {
            "all input states use the same template" using (inputs.all { it.template == template })
            "all output states use the same template" using (outputs.all { it.template == template })
            "amounts owed on input and output must match" using (sumAmountsDue(inputBalances) == sumAmountsDue
            (outputBalances))
        }

        // TODO: Handle proxies nominated by parties, i.e. a central clearing service
        val involvedParties: Set<PublicKey> = inputs.map { it.beneficiary.owningKey }.union(inputs.map { it.obligor.owningKey }).toSet()
        when (command.value.type) {
        // For close-out netting, allow any involved party to sign
            NetType.CLOSE_OUT -> require(command.signers.intersect(involvedParties).isNotEmpty()) { "any involved party has signed" }
        // Require signatures from all parties (this constraint can be changed for other contracts, and is used as a
        // placeholder while exact requirements are established), or fail the transaction.
            NetType.PAYMENT -> require(command.signers.containsAll(involvedParties)) { "all involved parties have signed" }
        }
    }
}
