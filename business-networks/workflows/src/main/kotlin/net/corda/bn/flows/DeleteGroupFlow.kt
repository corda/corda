package net.corda.bn.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.bn.contracts.GroupContract
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

/**
 * This flow is initiated by any member authorised to modify Business Network Groups. Queries for group with [groupId] linear ID and marks
 * it historic. Transaction is signed by all active members authorised to modify membership and stored on ledgers of all group's
 * participants.
 *
 * Membership states' participants are synced accordingly.
 *
 * @property groupId ID of group to be deleted.
 * @property notary Identity of the notary to be used for transactions notarisation. If not specified, first one from the whitelist will be used.
 */
@InitiatingFlow
@StartableByRPC
class DeleteGroupFlow(private val groupId: UniqueIdentifier, private val notary: Party? = null) : MembershipManagementFlow<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {
        // fetch group state with groupId linear ID
        val databaseService = serviceHub.cordaService(DatabaseService::class.java)
        val group = databaseService.getBusinessNetworkGroup(groupId)
                ?: throw BusinessNetworkGroupNotFoundException("Business Network group with $groupId linear ID doesn't exist")

        // check whether party is authorised to initiate flow
        val networkId = group.state.data.networkId
        authorise(networkId, databaseService) { it.canModifyGroups() }

        // check whether any member is not participant of any group
        val oldParticipantsMemberships = group.state.data.participants.map {
            databaseService.getMembership(networkId, it)
                    ?: throw MembershipNotFoundException("Cannot find membership with $it linear ID")
        }
        val membersWithoutGroup = oldParticipantsMemberships.filter { membership ->
            membership.state.data.identity.cordaIdentity !in (databaseService.getAllBusinessNetworkGroups(networkId) - group).flatMap { it.state.data.participants }
        }
        if (membersWithoutGroup.isNotEmpty()) {
            throw MembershipMissingGroupParticipationException("Illegal group deletion: $membersWithoutGroup would remain without any group participation.")
        }

        // fetch signers
        val authorisedMemberships = databaseService.getMembersAuthorisedToModifyMembership(networkId)
        val signers = authorisedMemberships.filter { it.state.data.isActive() }.map { it.state.data.identity.cordaIdentity }

        // building group exit transaction since deleted group state must be marked historic on all participants's vaults.
        val requiredSigners = signers.map { it.owningKey }
        val builder = TransactionBuilder(notary ?: serviceHub.networkMapCache.notaryIdentities.first())
                .addInputState(group)
                .addCommand(GroupContract.Commands.Exit(requiredSigners), requiredSigners)
        builder.verify(serviceHub)

        // collect signatures and finalise transaction
        val observers = group.state.data.participants - ourIdentity
        val observerSessions = observers.map { initiateFlow(it) }
        val finalisedTransaction = collectSignaturesAndFinaliseTransaction(builder, observerSessions, signers)

        // sync memberships' participants according to removed participants of the groups member is part of
        syncMembershipsParticipants(networkId, oldParticipantsMemberships, signers, databaseService, notary)

        return finalisedTransaction
    }
}

@InitiatedBy(DeleteGroupFlow::class)
class DeleteGroupResponderFlow(private val session: FlowSession) : MembershipManagementFlow<Unit>() {

    @Suspendable
    override fun call() {
        signAndReceiveFinalisedTransaction(session) {
            if (it.value !is GroupContract.Commands.Exit) {
                throw FlowException("Only Exit command is allowed")
            }
        }
    }
}