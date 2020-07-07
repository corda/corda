package net.corda.bn.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.bn.states.MembershipState
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
    protected fun authorise(networkId: String, databaseService: DatabaseService, authorisationMethod: (MembershipState) -> Boolean): StateAndRef<MembershipState> {
        if (!databaseService.businessNetworkExists(networkId)) {
            throw BusinessNetworkNotFoundException("Business Network with $networkId doesn't exist")
        }
        return databaseService.getMembership(networkId, ourIdentity)?.apply {
            if (!state.data.isActive()) {
                throw IllegalMembershipStatusException("Membership owned by $ourIdentity is not active")
            }
            if (!authorisationMethod(state.data)) {
                throw MembershipAuthorisationException("$ourIdentity is not authorised to run $this")
            }
        } ?: throw MembershipNotFoundException("$ourIdentity is not member of a business network")
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
     * Helper methods used to send membership states' transactions to [destinationSessions].
     *
     * @param memberships Collection of all memberships to be sent.
     * @param observerSessions Sessions of all observers who get finalised transaction.
     * @param destinationSessions Session to which [memberships] will be sent.
     */
    @Suspendable
    protected fun sendMemberships(
            memberships: Collection<StateAndRef<MembershipState>>,
            observerSessions: List<FlowSession>,
            destinationSessions: List<FlowSession>
    ) {
        val membershipsTransactions = memberships.map {
            serviceHub.validatedTransactions.getTransaction(it.ref.txhash)
                    ?: throw FlowException("Transaction for membership with ${it.state.data.linearId} ID doesn't exist")
        }
        observerSessions.forEach { it.send(if (it in destinationSessions) membershipsTransactions.size else 0) }
        destinationSessions.forEach { session ->
            membershipsTransactions.forEach { stx ->
                subFlow(SendTransactionFlow(session, stx))
            }
        }
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

    /**
     * Takes collection of [MembershipState]s and modifies their participants to be all the participants of groups it is part of.
     *
     * @param networkId ID of the Business Network.
     * @param memberships Collection of memberships which participants are modified.
     * @param signers Parties that need to sign participant modification transaction.
     * @param databaseService Service used to query vault for groups.
     * @param notary Identity of the notary to be used for transactions notarisation.
     */
    @Suspendable
    protected fun syncMembershipsParticipants(
            networkId: String,
            memberships: Collection<StateAndRef<MembershipState>>,
            signers: List<Party>,
            databaseService: DatabaseService,
            notary: Party?
    ) {
        val allGroups = databaseService.getAllBusinessNetworkGroups(networkId).map { it.state.data }
        memberships.forEach { membership ->
            val newParticipants = allGroups.filter {
                membership.state.data.identity.cordaIdentity in it.participants
            }.flatMap {
                it.participants
            }.toSet()

            subFlow(ModifyParticipantsFlow(membership, newParticipants.toList(), signers, notary))
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

/**
 * Exception thrown by any [MembershipManagementFlow] whenever Business Network group with provided [GroupState.linearId] doesn't exist.
 */
class BusinessNetworkGroupNotFoundException(message: String) : FlowException(message)

/**
 * Exception thrown by any [MembershipManagementFlow] whenever member remains without participation in any Business Network Group.
 */
class MembershipMissingGroupParticipationException(message: String) : FlowException(message)

/**
 * [MembershipManagementFlow] version of [IllegalArgumentException]
 */
class IllegalFlowArgumentException(message: String) : FlowException(message)

/**
 * Exception thrown by any [MembershipManagementFlow] whenever group ends up in illegal state.
 */
class IllegalBusinessNetworkGroupStateException(message: String) : FlowException(message)