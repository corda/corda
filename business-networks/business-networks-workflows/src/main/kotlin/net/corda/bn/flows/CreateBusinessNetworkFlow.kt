package net.corda.bn.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.bn.contracts.MembershipContract
import net.corda.bn.contracts.GroupContract
import net.corda.bn.states.BNIdentity
import net.corda.bn.states.BNORole
import net.corda.bn.states.GroupState
import net.corda.bn.states.MembershipIdentity
import net.corda.bn.states.MembershipState
import net.corda.bn.states.MembershipStatus
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

/**
 * Self issues [MembershipState] for the flow initiator creating new Business Network as consequence. Every node in Compatibility Zone can
 * initiate this flow. Also creates initial Business Network group in form of [GroupState].
 *
 * @property networkId Custom ID to be given to the new Business Network. If not specified, randomly selected one will be used.
 * @property businessIdentity Custom business identity to be given to membership.
 * @property groupId Custom ID to be given to the initial Business Network group. If not specified, randomly selected one will be used.
 * @property groupName Optional name to be given to Business Network group.
 * @property notary Identity of the notary to be used for transactions notarisation. If not specified, first one from the whitelist will be used.
 *
 * @throws DuplicateBusinessNetworkException If Business Network with [networkId] ID already exists.
 * @throws DuplicateBusinessNetworkGroupException If Business Network Group with [groupId] ID already exists.
 */
@InitiatingFlow
@StartableByRPC
class CreateBusinessNetworkFlow(
        private val networkId: UniqueIdentifier = UniqueIdentifier(),
        private val businessIdentity: BNIdentity? = null,
        private val groupId: UniqueIdentifier = UniqueIdentifier(),
        private val groupName: String? = null,
        private val notary: Party? = null
) : FlowLogic<SignedTransaction>() {

    /**
     * Issues pending membership (with new unique Business Network ID) on initiator's ledger.
     *
     * @param databaseService Service used to query vault for memberships.
     *
     * @return Signed membership issuance transaction.
     *
     * @throws DuplicateBusinessNetworkException If Business Network with [networkId] ID already exists.
     */
    @Suspendable
    private fun createMembershipRequest(databaseService: DatabaseService): SignedTransaction {
        // check if business network with networkId already exists
        if (databaseService.businessNetworkExists(networkId.toString())) {
            throw DuplicateBusinessNetworkException(networkId)
        }

        val membership = MembershipState(
                identity = MembershipIdentity(ourIdentity, businessIdentity),
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

    /**
     * Activates initiator's pending membership.
     *
     * @param membership State and ref pair of pending membership to be activated.
     *
     * @return Signed membership activation transaction.
     */
    @Suspendable
    private fun activateMembership(membership: StateAndRef<MembershipState>): SignedTransaction {
        val builder = TransactionBuilder(notary ?: serviceHub.networkMapCache.notaryIdentities.first())
                .addInputState(membership)
                .addOutputState(membership.state.data.copy(status = MembershipStatus.ACTIVE, modified = serviceHub.clock.instant()))
                .addCommand(MembershipContract.Commands.Activate(listOf(ourIdentity.owningKey), true), ourIdentity.owningKey)
        builder.verify(serviceHub)

        val stx = serviceHub.signInitialTransaction(builder)
        return subFlow(FinalityFlow(stx, emptyList()))
    }

    /**
     * Assigns [BNORole] to initiator's active membership.
     *
     * @param membership State and ref pair of pending membership to be authorised.
     *
     * @return Signed membership role modification transaction.
     */
    @Suspendable
    private fun authoriseMembership(membership: StateAndRef<MembershipState>): SignedTransaction {
        val builder = TransactionBuilder(notary ?: serviceHub.networkMapCache.notaryIdentities.first())
                .addInputState(membership)
                .addOutputState(membership.state.data.copy(roles = setOf(BNORole()), modified = serviceHub.clock.instant()))
                .addCommand(MembershipContract.Commands.ModifyRoles(listOf(ourIdentity.owningKey), ourIdentity), ourIdentity.owningKey)
        builder.verify(serviceHub)

        val stx = serviceHub.signInitialTransaction(builder)
        return subFlow(FinalityFlow(stx, emptyList()))
    }

    /**
     * Issues initial Business Network Group on initiator's ledger.
     *
     * @param databaseService Service used to query vault for memberships.
     *
     * @return Signed group issuance transaction.
     */
    @Suspendable
    private fun createBusinessNetworkGroup(databaseService: DatabaseService): SignedTransaction {
        // check if business network group with groupId already exists
        if (databaseService.businessNetworkGroupExists(groupId)) {
            throw DuplicateBusinessNetworkGroupException(groupId)
        }

        val group = GroupState(networkId = networkId.toString(), name = groupName, linearId = groupId, participants = listOf(ourIdentity))
        val builder = TransactionBuilder(notary ?: serviceHub.networkMapCache.notaryIdentities.first())
                .addOutputState(group)
                .addCommand(GroupContract.Commands.Create(listOf(ourIdentity.owningKey)), ourIdentity.owningKey)
        builder.verify(serviceHub)

        val stx = serviceHub.signInitialTransaction(builder)
        return subFlow(FinalityFlow(stx, emptyList()))
    }

    @Suspendable
    override fun call(): SignedTransaction {
        val databaseService = serviceHub.cordaService(DatabaseService::class.java)
        // first issue membership with PENDING status
        val pendingMembership = createMembershipRequest(databaseService).tx.outRefsOfType(MembershipState::class.java).single()
        // after that activate the membership
        val activeMembership = activateMembership(pendingMembership).tx.outRefsOfType(MembershipState::class.java).single()
        // give all administrative permissions to the membership
        return authoriseMembership(activeMembership).apply {
            // in the end create initial business network group
            createBusinessNetworkGroup(databaseService)
        }
    }
}