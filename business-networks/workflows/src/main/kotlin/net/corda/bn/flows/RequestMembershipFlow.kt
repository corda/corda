package net.corda.bn.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.bn.contracts.MembershipContract
import net.corda.bn.states.MembershipState
import net.corda.bn.states.MembershipStatus
import net.corda.core.flows.CollectSignaturesFlow
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.ReceiveFinalityFlow
import net.corda.core.flows.SignTransactionFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.unwrap

/**
 * This flow is initiated by new potential member who requests membership activation from authorised Business Network member. Issues
 * new pending [MembershipState] on potential member and all authorised members' ledgers.
 *
 * @property authorisedParty Identity of authorised member from whom the membership activation is requested.
 * @property networkId ID of the Business Network that potential new member wants to join.
 */
@InitiatingFlow
@StartableByRPC
class RequestMembershipFlow(private val authorisedParty: Party, private val networkId: String) : FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {
        // check whether the initiator is already member of given Business Network
        val databaseService = serviceHub.cordaService(DatabaseService::class.java)
        if (databaseService.getMembership(networkId, ourIdentity) != null) {
            throw FlowException("Initiator is already a member of Business Network with $networkId ID")
        }

        // send request to authorised member
        val authorisedPartySession = initiateFlow(authorisedParty)
        authorisedPartySession.send(networkId)

        // sign transaction
        val signResponder = object : SignTransactionFlow(authorisedPartySession) {
            override fun checkTransaction(stx: SignedTransaction) {
                val command = stx.tx.commands.single()
                if (command.value !is MembershipContract.Commands.Request) {
                    throw FlowException("Only Request command is allowed")
                }

                val membershipState = stx.tx.outputs.single().data as MembershipState
                if (ourIdentity != membershipState.identity) {
                    throw IllegalArgumentException("Membership identity does not match the one of the initiator")
                }

                stx.toLedgerTransaction(serviceHub, false).verify()
            }
        }
        val stx = subFlow(signResponder)

        // receive finality flow
        return subFlow(ReceiveFinalityFlow(authorisedPartySession, stx.id))
    }
}

@InitiatingFlow
@InitiatedBy(RequestMembershipFlow::class)
class RequestMembershipFlowResponder(private val session: FlowSession) : MembershipManagementFlow<Unit>() {

    @Suspendable
    override fun call() {
        // receive network ID
        val networkId = session.receive<String>().unwrap { it }

        // check whether party is authorised to activate membership
        val databaseService = serviceHub.cordaService(DatabaseService::class.java)
        val auth = BNUtils.loadBNMemberAuth()
        authorise(networkId, databaseService) { auth.canActivateMembership(it) }

        // fetch observers
        val authorisedMemberships = databaseService.getMembersAuthorisedToModifyMembership(networkId, auth)
        val observers = (authorisedMemberships.map { it.state.data.identity } - ourIdentity).toSet()

        // build transaction
        val counterparty = session.counterparty
        val membershipState = MembershipState(
                identity = counterparty,
                networkId = networkId,
                status = MembershipStatus.PENDING,
                participants = (observers + ourIdentity + counterparty).toList()
        )
        val builder = TransactionBuilder(serviceHub.networkMapCache.notaryIdentities.first())
                .addOutputState(membershipState)
                .addCommand(MembershipContract.Commands.Request(), listOf(ourIdentity.owningKey, counterparty.owningKey))
        builder.verify(serviceHub)

        // sign transaction
        val selfSignedTransaction = serviceHub.signInitialTransaction(builder)
        val allSignedTransaction = subFlow(CollectSignaturesFlow(selfSignedTransaction, listOf(session)))

        // finalise transaction
        val observerSessions = observers.map { initiateFlow(it) }.toSet()
        subFlow(FinalityFlow(allSignedTransaction, observerSessions + session))
    }
}

@InitiatedBy(RequestMembershipFlowResponder::class)
class RequestMembershipObserverFlow(private val session: FlowSession) : FlowLogic<Unit>() {

    @Suspendable
    override fun call() {
        subFlow(ReceiveFinalityFlow(session))
    }
}