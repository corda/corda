package net.corda.bn.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.bn.contracts.GroupContract
import net.corda.bn.states.GroupState
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
 * This flow is initiated by any member authorised to modify Business Network Groups. Issues new [GroupState] with initiator and
 * [additionalParticipants] as participants, custom [groupName] and [groupId]. Transaction is signed by all active members authorised to
 * modify memberships and stored on ledgers of all group's participants.
 *
 * Memberships of new group participants are exchanged in the end and participants of their membership states are synced accordingly.
 *
 * @property networkId ID of the Business Network that Business Network Group will relate to.
 * @property groupId Custom ID to be given to the issued Business Network Group. If not specified, randomly selected one will be used.
 * @property groupName Optional name to be given to the issued Business Network Group.
 * @property additionalParticipants Set of participants to be added to issued Business Network Group alongside initiator's identity.
 * @property notary Identity of the notary to be used for transactions notarisation. If not specified, first one from the whitelist will be used.
 *
 * @throws DuplicateBusinessNetworkGroupException If Business Network Group with [groupId] ID already exists.
 */
@InitiatingFlow
@StartableByRPC
class CreateGroupFlow(
        private val networkId: String,
        private val groupId: UniqueIdentifier = UniqueIdentifier(),
        private val groupName: String? = null,
        private val additionalParticipants: Set<UniqueIdentifier> = emptySet(),
        private val notary: Party? = null
) : MembershipManagementFlow<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {
        // check whether party is authorised to initiate flow
        val databaseService = serviceHub.cordaService(DatabaseService::class.java)
        val ourMembership = authorise(networkId, databaseService) { it.canModifyGroups() }

        // check whether group with groupId already exists
        if (databaseService.businessNetworkGroupExists(groupId)) {
            throw DuplicateBusinessNetworkGroupException("Business Network group with $groupId ID already exists")
        }

        // get all additional participants' memberships from provided membership ids
        val additionalParticipantsMemberships = additionalParticipants.map {
            databaseService.getMembership(it)
                    ?: throw MembershipNotFoundException("Cannot find membership with $it linear ID")
        }.toSet()

        // get all additional participants' identities from provided memberships
        val additionalParticipantsIdentities = additionalParticipantsMemberships.map {
            if (it.state.data.isPending()) {
                throw IllegalMembershipStatusException("$it can't be participant of Business Network groups since it has pending status")
            }

            it.state.data.identity.cordaIdentity
        }.toSet()

        // fetch signers
        val authorisedMemberships = databaseService.getMembersAuthorisedToModifyMembership(networkId)
        val signers = authorisedMemberships.filter { it.state.data.isActive() }.map { it.state.data.identity.cordaIdentity }

        // building transaction
        val group = GroupState(networkId = networkId, name = groupName, linearId = groupId, participants = (additionalParticipantsIdentities + ourIdentity).toList())
        val requiredSigners = signers.map { it.owningKey }
        val builder = TransactionBuilder(notary ?: serviceHub.networkMapCache.notaryIdentities.first())
                .addOutputState(group)
                .addCommand(GroupContract.Commands.Create(requiredSigners), requiredSigners)
        builder.verify(serviceHub)

        // collect signatures and finalise transaction
        val observers = additionalParticipantsIdentities - ourIdentity
        val observerSessions = observers.map { initiateFlow(it) }
        val finalisedTransaction = collectSignaturesAndFinaliseTransaction(builder, observerSessions, signers)

        // exchange memberships between new group participants
        sendMemberships(additionalParticipantsMemberships + ourMembership, observerSessions, observerSessions)

        // sync memberships' participants according to new participants of the groups member is part of
        syncMembershipsParticipants(networkId, additionalParticipantsMemberships.toList(), signers, databaseService, notary)

        return finalisedTransaction
    }
}

@InitiatedBy(CreateGroupFlow::class)
class CreateGroupResponderFlow(private val session: FlowSession) : MembershipManagementFlow<Unit>() {

    @Suspendable
    override fun call() {
        signAndReceiveFinalisedTransaction(session) {
            if (it.value !is GroupContract.Commands.Create) {
                throw FlowException("Only Create command is allowed")
            }
        }
        receiveMemberships(session)
    }
}