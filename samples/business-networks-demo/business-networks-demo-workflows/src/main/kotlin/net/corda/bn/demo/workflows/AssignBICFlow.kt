package net.corda.bn.demo.workflows

import co.paralleluniverse.fibers.Suspendable
import net.corda.bn.demo.contracts.BankIdentity
import net.corda.bn.flows.IllegalFlowArgumentException
import net.corda.bn.flows.ModifyBusinessIdentityFlow
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction

/**
 * This flow assigns [BankIdentity] business identity to membership with [membershipId]. It is meant to be conveniently used from node shell
 * instead of [ModifyBusinessIdentityFlow].
 *
 * @property membershipId ID of the modified membership.
 * @property bic Business Identifier Code to be assigned to initiator.
 * @property notary Identity of the notary to be used for transactions notarisation. If not specified, first one from the whitelist will be used.
 */
@StartableByRPC
class AssignBICFlow(private val membershipId: UniqueIdentifier, private val bic: String, private val notary: Party? = null) : FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {
        val bankIdentity = BankIdentity(bic).apply {
            if (!isValid()) {
                throw IllegalFlowArgumentException("$bic in not a valid BIC")
            }
        }
        return subFlow(ModifyBusinessIdentityFlow(membershipId, bankIdentity, notary))
    }
}