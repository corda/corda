package com.r3corda.contracts.clause

import com.r3corda.core.contracts.*
import com.r3corda.core.contracts.clauses.GroupClause
import com.r3corda.core.contracts.clauses.MatchBehaviour

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
abstract class AbstractIssue<in S: ContractState, T: Any>(
        val sum: List<S>.() -> Amount<Issued<T>>,
        val sumOrZero: List<S>.(token: Issued<T>) -> Amount<Issued<T>>
) : GroupClause<S, Issued<T>> {
    override val ifMatched = MatchBehaviour.END
    override val ifNotMatched = MatchBehaviour.CONTINUE

    override fun verify(tx: TransactionForContract,
                        inputs: List<S>,
                        outputs: List<S>,
                        commands: Collection<AuthenticatedObject<CommandData>>,
                        token: Issued<T>): Set<CommandData> {
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
        val issuer = token.issuer.party
        val inputAmount = inputs.sumOrZero(token)
        val outputAmount = outputs.sum()
        requireThat {
            "the issue command has a nonce" by (issueCommand.value.nonce != 0L)
            // TODO: This doesn't work with the trader demo, so use the underlying key instead
            // "output states are issued by a command signer" by (issuer in issueCommand.signingParties)
            "output states are issued by a command signer" by (issuer.owningKey in issueCommand.signers)
            "output values sum to more than the inputs" by (outputAmount > inputAmount)
        }

        return setOf(issueCommand.value)
    }
}
