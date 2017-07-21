package net.corda.contracts.clause

import net.corda.core.contracts.*
import net.corda.core.contracts.clauses.Clause
import net.corda.core.transactions.LedgerTransaction

/**
 * Standard issue clause for contracts that issue fungible assets.
 *
 * @param S the type of contract state which is being issued.
 * @param T the token underlying the issued state.
 * @param sum function to convert a list of states into an amount of the token. Must error if there are no states in
 * the list.
 * @param sumOrZero function to convert a list of states into an amount of the token, and returns zero if there are
 * no states in the list. Takes in an instance of the token definition for constructing the zero amount if needed.
 */
abstract class AbstractIssue<in S : ContractState, C : CommandData, T : Any>(
        val sum: List<S>.() -> Amount<Issued<T>>,
        val sumOrZero: List<S>.(token: Issued<T>) -> Amount<Issued<T>>
) : Clause<S, C, Issued<T>>() {
    override fun verify(tx: LedgerTransaction,
                        inputs: List<S>,
                        outputs: List<S>,
                        commands: List<AuthenticatedObject<C>>,
                        groupingKey: Issued<T>?): Set<C> {
        require(groupingKey != null)
        // TODO: Take in matched commands as a parameter
        val issueCommand = commands.requireSingleCommand<IssueCommand>()

        // If we have an issue command, perform special processing: the group is allowed to have no inputs,
        // and the output states must have a deposit reference owned by the signer.
        //
        // Whilst the transaction *may* have no inputs, it can have them, and in this case the outputs must
        // sum to more than the inputs. An issuance of zero size is not allowed.
        //
        // Note that this means literally anyone with access to the network can issue asset claims of arbitrary
        // amounts! It is up to the recipient to decide if the backing party is trustworthy or not, via some
        // external mechanism (such as locally defined rules on which parties are trustworthy).

        // The grouping already ensures that all outputs have the same deposit reference and token.
        val issuer = groupingKey!!.issuer.party
        val inputAmount = inputs.sumOrZero(groupingKey)
        val outputAmount = outputs.sum()
        requireThat {
            "the issue command has a nonce" using (issueCommand.value.nonce != 0L)
            // TODO: This doesn't work with the trader demo, so use the underlying key instead
            // "output states are issued by a command signer" by (issuer in issueCommand.signingParties)
            "output states are issued by a command signer" using (issuer.owningKey in issueCommand.signers)
            "output values sum to more than the inputs" using (outputAmount > inputAmount)
        }

        // This is safe because we've taken the command from a collection of C objects at the start
        @Suppress("UNCHECKED_CAST")
        return setOf(issueCommand.value as C)
    }
}
