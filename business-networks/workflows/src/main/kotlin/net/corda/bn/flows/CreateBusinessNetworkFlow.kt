package net.corda.bn.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.bn.contracts.MembershipContract
import net.corda.bn.states.MembershipState
import net.corda.bn.states.MembershipStatus
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import java.util.*

/**
 * Self issues [MembershipState] for the flow initiator creating new Business Network as consequence. Every node in Compatibility Zone can
 * initiate this flow.
 *
 * @property networkId Custom ID to be given to the new Business Network. If not specified, randomly selected one will be used.
 * @property notary Identity of the notary to be used for transactions notarisation. If not specified, first one from the whitelist will be used.
 *
 * @throws DuplicateBusinessNetworkException If Business Network with [networkId] ID already exists.
 */
@InitiatingFlow
@StartableByRPC
class CreateBusinessNetworkFlow(private val networkId: UniqueIdentifier = UniqueIdentifier(), private val notary: Party? = null) : FlowLogic<SignedTransaction>() {

    @Suspendable
    private fun createMembershipRequest(): SignedTransaction {
        // check if business network with networkId already exists
        val databaseService = serviceHub.cordaService(DatabaseService::class.java)
        if (databaseService.getAllMembershipsWithStatus(networkId.toString(), MembershipStatus.PENDING, MembershipStatus.ACTIVE, MembershipStatus.SUSPENDED).isNotEmpty()) {
            throw DuplicateBusinessNetworkException("Business Network with $networkId ID already exists")
        }

        val membership = MembershipState(
                identity = ourIdentity,
                networkId = networkId.toString(),
                status = MembershipStatus.PENDING,
                participants = listOf(ourIdentity)
        )

        val builder = TransactionBuilder(notary ?: serviceHub.networkMapCache.notaryIdentities.first())
                .addOutputState(membership)
                .addCommand(MembershipContract.Commands.Request(listOf(ourIdentity.owningKey)), ourIdentity.owningKey)
        builder.verify(serviceHub)

        val stx = serviceHub.signInitialTransaction(builder)
        return subFlow(FinalityFlow(stx, emptyList()))
    }

    @Suspendable
    override fun call(): SignedTransaction {
        // first issue membership with PENDING status
        val membership = createMembershipRequest().tx.outRefsOfType(MembershipState::class.java).single()

        // now build transaction to activate pending self issued membership
        val builder = TransactionBuilder(notary ?: serviceHub.networkMapCache.notaryIdentities.first())
                .addInputState(membership)
                .addOutputState(membership.state.data.copy(status = MembershipStatus.ACTIVE, modified = serviceHub.clock.instant()))
                .addCommand(MembershipContract.Commands.Activate(listOf(ourIdentity.owningKey), true), ourIdentity.owningKey)
        builder.verify(serviceHub)

        val stx = serviceHub.signInitialTransaction(builder)
        return subFlow(FinalityFlow(stx, emptyList()))
    }
}

class DuplicateBusinessNetworkException(message: String) : FlowException(message)