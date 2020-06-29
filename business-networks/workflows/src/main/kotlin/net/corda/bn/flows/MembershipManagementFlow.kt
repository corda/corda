package net.corda.bn.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.bn.flows.extensions.BNMemberAuth
import net.corda.bn.states.MembershipState
import net.corda.bn.states.MembershipStatus
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.CollectSignaturesFlow
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.ReceiveFinalityFlow
import net.corda.core.flows.ReceiveTransactionFlow
import net.corda.core.flows.SendTransactionFlow
import net.corda.core.flows.SignTransactionFlow
import net.corda.core.identity.Party
import net.corda.core.node.StatesToRecord
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.unwrap

/**
 * This abstract class is extended by any flow which will use common membership management helper methods.
 */
abstract class MembershipManagementFlow<T> : FlowLogic<T>() {

    /**
     * Performs authorisation checks of the flow initiator using provided authorisation methods.
     *
     * @param networkId ID of the Business Network in which we perform authorisation.
     * @param databaseService Service used to query vault for memberships.
     * @param authorisationMethod Method which does actual authorisation check over membership.
     */
    @Suppress("ThrowsCount")
    @Suspendable
    protected fun authorise(networkId: String, databaseService: DatabaseService, authorisationMethod: (MembershipState) -> Boolean) {
        if (!databaseService.businessNetworkExists(networkId)) {
            throw BusinessNetworkNotFoundException("Business Network with $networkId doesn't exist")
        }
        val ourMembership = databaseService.getMembership(networkId, ourIdentity)?.state?.data
                ?: throw MembershipNotFoundException("$ourIdentity is not member of a business network")
        if (!ourMembership.isActive()) {
            throw IllegalMembershipStatusException("Membership owned by $ourIdentity is not active")
        }
        if (!authorisationMethod(ourMembership)) {
            throw MembershipAuthorisationException("$ourIdentity is not authorised to activate membership")
        }
    }

    /**
     * Takes set of observers and collects signatures of the transaction from the specified subset of signers. In the end finalises
     * finalises transaction over all observers and returns it.
     *
     * @param builder Transaction builder for the transaction to be signed and finalised.
     * @param observerSessions Sessions that will receive finalised transaction.
     * @param signers Parties from the [observerSessions] which need to sign the transaction.
     *
     * @return Finalised all signed transaction.
     */
    @Suspendable
    protected fun collectSignaturesAndFinaliseTransaction(
            builder: TransactionBuilder,
            observerSessions: List<FlowSession>,
            signers: List<Party>
    ): SignedTransaction {
        // send info to observers whether they need to sign the transaction
        observerSessions.forEach { it.send(it.counterparty in signers) }

        val selfSignedTransaction = serviceHub.signInitialTransaction(builder)
        val signerSessions = observerSessions.filter { it.counterparty in signers }
        val allSignedTransaction = subFlow(CollectSignaturesFlow(selfSignedTransaction, signerSessions))

        return subFlow(FinalityFlow(allSignedTransaction, observerSessions, StatesToRecord.ALL_VISIBLE))
    }

    /**
     * This method needs to be called in the responder flow that is initiated by flow which calls [collectSignaturesAndFinaliseTransaction]
     * method. Provides signature over transaction with specified explicit command check and receives finalised transaction.
     */
    @Suspendable
    protected fun signAndReceiveFinalisedTransaction(session: FlowSession, commandCheck: (Command<*>) -> Unit) {
        val isSigner = session.receive<Boolean>().unwrap { it }
        val stx = if (isSigner) {
            val signResponder = object : SignTransactionFlow(session) {
                override fun checkTransaction(stx: SignedTransaction) {
                    val command = stx.tx.commands.single()
                    commandCheck(command)

                    stx.toLedgerTransaction(serviceHub, false).verify()
                }
            }
            subFlow(signResponder)
        } else null

        subFlow(ReceiveFinalityFlow(session, stx?.id, StatesToRecord.ALL_VISIBLE))
    }

    /**
     * Sends memberships of all members authorised to modify memberships to [activatedMembership] member. It also sends all non revoked
     * memberships to [activatedMembership] member if it is authorised to modify memberships.
     *
     * @param networkId ID of the Business Network from which we query for memberships to be sent.
     * @param activatedMembership Just activated member to which mentioned memberships are sent.
     * @param authorisedMemberships Set of memberships of all members authorised to modify memberships.
     * @param observerSessions Sessions of all observers who get finalised transaction.
     * @param auth Object containing authorisation methods.
     * @param databaseService Service used to query vault for memberships.
     */
    @Suppress("LongParameterList")
    @Suspendable
    protected fun onboardMembershipSync(
            networkId: String,
            activatedMembership: MembershipState,
            authorisedMemberships: Set<StateAndRef<MembershipState>>,
            observerSessions: List<FlowSession>,
            auth: BNMemberAuth,
            databaseService: DatabaseService
    ) {
        val activatedMemberSession = observerSessions.single { it.counterparty == activatedMembership.identity }
        val pendingAndSuspendedMemberships =
                if (auth.run { canActivateMembership(activatedMembership) || canSuspendMembership(activatedMembership) || canRevokeMembership(activatedMembership) }) {
                    databaseService.getAllMembershipsWithStatus(networkId, MembershipStatus.PENDING, MembershipStatus.ACTIVE, MembershipStatus.SUSPENDED)
                } else emptyList()
        sendMemberships(authorisedMemberships + pendingAndSuspendedMemberships, observerSessions, activatedMemberSession)
    }

    /**
     * Helper methods used to send membership states' transactions to [destinationSession].
     *
     * @param memberships Collection of all memberships to be sent.
     * @param observerSessions Sessions of all observers who get finalised transaction.
     * @param destinationSession Session to which [memberships] will be sent.
     */
    @Suspendable
    private fun sendMemberships(
            memberships: Collection<StateAndRef<MembershipState>>,
            observerSessions: List<FlowSession>,
            destinationSession: FlowSession
    ) {
        val membershipsTransactions = memberships.map {
            serviceHub.validatedTransactions.getTransaction(it.ref.txhash)
                    ?: throw FlowException("Transaction for membership with ${it.state.data.linearId} ID doesn't exist")
        }
        observerSessions.forEach { it.send(if (it.counterparty == destinationSession.counterparty) membershipsTransactions.size else 0) }
        membershipsTransactions.forEach { subFlow(SendTransactionFlow(destinationSession, it)) }
    }

    /**
     * This method needs to be called in the responder flow that is initiated by flow which calls [sendMemberships] method. Receives all of
     * the transactions sent via the [sendMemberships] method.
     */
    @Suspendable
    protected fun receiveMemberships(session: FlowSession) {
        val txNumber = session.receive<Int>().unwrap { it }
        repeat(txNumber) {
            subFlow(ReceiveTransactionFlow(session, true, StatesToRecord.ALL_VISIBLE))
        }
    }
}

/**
 * Exception thrown by any [MembershipManagementFlow] whenever Business Network with provided [MembershipState.networkId] doesn't exist.
 */
class BusinessNetworkNotFoundException(message: String) : FlowException(message)

/**
 * Exception thrown by any [MembershipManagementFlow] whenever provided parties membership doesn't exist.
 */
class MembershipNotFoundException(message: String) : FlowException(message)

/**
 * Exception thrown by any [MembershipManagementFlow] whenever provided member's state is not appropriate for the context.
 */
class IllegalMembershipStatusException(message: String) : FlowException(message)

/**
 * Exception thrown by any [MembershipManagementFlow] whenever membership fails role based authorisation.
 */
class MembershipAuthorisationException(message: String) : FlowException(message)
