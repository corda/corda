package net.corda.bn.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.bn.contracts.GroupContract
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

/**
 * This flow is initiated by any member authorised to modify Business Network Groups. Queries for groups with [groupId] linear ID and
 * modifies their [GroupState.name] and [GroupState.participants] fields. Transaction is signed by all active members authorised to modify
 * membership and stored on ledgers of all group's participants.
 *
 * Memberships of new group participants are exchanged in the end and participants of their membership states are synced accordingly.
 *
 * @property groupId ID of group to be modified.
 * @property name New name of modified group.
 * @property participants New participants of modified group.
 * @property notary Identity of the notary to be used for transactions notarisation. If not specified, first one from the whitelist will be used.
 */
@StartableByRPC
class ModifyGroupFlow(
        private val groupId: UniqueIdentifier,
        private val name: String? = null,
        private val participants: Set<UniqueIdentifier>? = null,
        private val notary: Party? = null
) : FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {
        return subFlow(ModifyGroupInternalFlow(groupId, name, participants, true, notary))
    }
}

@InitiatingFlow
class ModifyGroupInternalFlow(
        private val groupId: UniqueIdentifier,
        private val name: String? = null,
        private val participants: Set<UniqueIdentifier>? = null,
        private val syncMembershipsParticipants: Boolean = true,
        private val notary: Party? = null
) : MembershipManagementFlow<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {
        // validate flow arguments
        if (name == null && participants == null) {
            throw IllegalFlowArgumentException("One of the name or participants arguments must be specified")
        }

        // fetch group state with groupId linear ID
        val databaseService = serviceHub.cordaService(DatabaseService::class.java)
        val group = databaseService.getBusinessNetworkGroup(groupId)
                ?: throw BusinessNetworkGroupNotFoundException("Business Network group with $groupId linear ID doesn't exist")

        // check whether party is authorised to initiate flow
        val networkId = group.state.data.networkId
        authorise(networkId, databaseService) { it.canModifyGroups() }

        // get all new participants' memberships from provided membership ids
        val participantsMemberships = participants?.map {
            databaseService.getMembership(it)
                    ?: throw MembershipNotFoundException("Cannot find membership with $it linear ID")
        }

        // get all new participants' identities from provided memberships
        val participantsIdentities = participantsMemberships?.map {
            if (it.state.data.isPending()) {
                throw IllegalMembershipStatusException("$it can't be participant of Business Network groups since it has pending status")
            }

            it.state.data.identity.cordaIdentity
        }

        // check if initiator is one of group participants
        if (participantsIdentities != null && !participantsIdentities.contains(ourIdentity)) {
            throw IllegalBusinessNetworkGroupStateException("Initiator must be participant of modified Business Network Group.")
        }

        // check whether any member is not participant of any group
        val outputGroup = group.state.data.let { groupState ->
            groupState.copy(
                    name = name ?: groupState.name,
                    participants = participantsIdentities ?: groupState.participants,
                    modified = serviceHub.clock.instant()
            )
        }
        val oldParticipantsMemberships = (group.state.data.participants - outputGroup.participants).map {
            databaseService.getMembership(networkId, it)
                    ?: throw MembershipNotFoundException("Cannot find membership with $it linear ID")
        }.toSet()
        val allGroups = databaseService.getAllBusinessNetworkGroups(networkId)
        val membersWithoutGroup = oldParticipantsMemberships.filter { membership ->
            membership.state.data.identity.cordaIdentity !in (allGroups - group).flatMap { it.state.data.participants }
        }
        if (syncMembershipsParticipants && membersWithoutGroup.isNotEmpty()) {
            throw MembershipMissingGroupParticipationException("Illegal group modification: $membersWithoutGroup would remain without any group participation.")
        }

        // fetch signers
        val authorisedMemberships = databaseService.getMembersAuthorisedToModifyMembership(networkId)
        val signers = authorisedMemberships.filter { it.state.data.isActive() }.map { it.state.data.identity.cordaIdentity }

        // building transaction
        val requiredSigners = signers.map { it.owningKey }
        val builder = TransactionBuilder(notary ?: serviceHub.networkMapCache.notaryIdentities.first())
                .addInputState(group)
                .addOutputState(outputGroup)
                .addCommand(GroupContract.Commands.Modify(requiredSigners), requiredSigners)
        builder.verify(serviceHub)

        // collect signatures and finalise transaction
        val observers = group.state.data.participants.toSet() + outputGroup.participants - ourIdentity
        val observerSessions = observers.map { initiateFlow(it) }
        val finalisedTransaction = collectSignaturesAndFinaliseTransaction(builder, observerSessions, signers)

        // sync memberships between all group members
        participantsMemberships?.also { membershipsToSend ->
            sendMemberships(membershipsToSend, observerSessions, observerSessions.filter { it.counterparty in outputGroup.participants }.toHashSet())
        }

        // sync participants of all relevant membership states
        if (syncMembershipsParticipants) {
            syncMembershipsParticipants(
                    networkId,
                    oldParticipantsMemberships + (participantsMemberships ?: emptyList()),
                    signers,
                    databaseService,
                    notary
            )
        }

        return finalisedTransaction
    }
}

@InitiatedBy(ModifyGroupInternalFlow::class)
class ModifyGroupResponderFlow(private val session: FlowSession) : MembershipManagementFlow<Unit>() {

    @Suspendable
    override fun call() {
        signAndReceiveFinalisedTransaction(session) {
            if (it.value !is GroupContract.Commands.Modify) {
                throw FlowException("Only Modify command is allowed")
            }
        }
        receiveMemberships(session)
    }
}