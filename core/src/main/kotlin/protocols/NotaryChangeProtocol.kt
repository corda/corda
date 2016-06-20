package protocols

import co.paralleluniverse.fibers.Suspendable
import com.r3corda.core.contracts.*
import com.r3corda.core.crypto.DigitalSignature
import com.r3corda.core.crypto.Party
import com.r3corda.core.crypto.signWithECDSA
import com.r3corda.core.messaging.Ack
import com.r3corda.core.messaging.SingleMessageRecipient
import com.r3corda.core.node.NodeInfo
import com.r3corda.core.protocols.ProtocolLogic
import com.r3corda.core.random63BitValue
import com.r3corda.core.utilities.ProgressTracker
import com.r3corda.protocols.AbstractRequestMessage
import com.r3corda.protocols.NotaryProtocol
import com.r3corda.protocols.ResolveTransactionsProtocol
import java.security.PublicKey

/**
 * A protocol to be used for changing a state's Notary. This is required since all input states to a transaction
 * must point to the same notary.
 *
 * The [Instigator] assembles the transaction for notary replacement and sends out change proposals to all participants
 * ([Acceptor]) of that state. If participants agree to the proposed change, they each sign the transaction.
 * Finally, [Instigator] sends the transaction containing all signatures back to each participant so they can record it and
 * use the new updated state for future transactions.
 */
object NotaryChangeProtocol {
    val TOPIC_INITIATE = "platform.notary.change.initiate"
    val TOPIC_CHANGE = "platform.notary.change.execute"

    data class Proposal(val stateRef: StateRef,
                        val newNotary: Party,
                        val stx: SignedTransaction)

    class Handshake(val sessionIdForSend: Long,
                    replyTo: SingleMessageRecipient,
                    replySessionId: Long) : AbstractRequestMessage(replyTo, replySessionId)

    class Instigator<T : ContractState>(val originalState: StateAndRef<T>,
                                        val newNotary: Party,
                                        override val progressTracker: ProgressTracker = tracker()) : ProtocolLogic<StateAndRef<T>>() {
        companion object {

            object SIGNING : ProgressTracker.Step("Requesting signatures from other parties")

            object NOTARY : ProgressTracker.Step("Requesting current Notary signature")

            fun tracker() = ProgressTracker(SIGNING, NOTARY)
        }

        @Suspendable
        override fun call(): StateAndRef<T> {
            val (stx, participants) = assembleTx()

            progressTracker.currentStep = SIGNING

            val myKey = serviceHub.storageService.myLegalIdentity.owningKey
            val me = listOf(myKey)

            val signatures = if (participants == me) {
                listOf(getNotarySignature(stx))
            } else {
                collectSignatures(participants - me, stx)
            }

            val finalTx = stx + signatures
            serviceHub.recordTransactions(listOf(finalTx))
            return finalTx.tx.outRef(0)
        }

        private fun assembleTx(): Pair<SignedTransaction, List<PublicKey>> {
            val state = originalState.state
            val newState = state.withNewNotary(newNotary)
            val participants = state.data.participants
            val tx = TransactionType.NotaryChange.Builder().withItems(originalState, newState)
            tx.signWith(serviceHub.storageService.myLegalIdentityKey)

            val stx = tx.toSignedTransaction(false)
            return Pair(stx, participants)
        }

        @Suspendable
        private fun collectSignatures(participants: List<PublicKey>, stx: SignedTransaction): List<DigitalSignature.WithKey> {
            val sessions = mutableMapOf<NodeInfo, Long>()

            val participantSignatures = participants.map {
                val participantNode = serviceHub.networkMapCache.getNodeByPublicKey(it) ?:
                        throw IllegalStateException("Participant $it to state $originalState not found on the network")
                val sessionIdForSend = random63BitValue()
                sessions[participantNode] = sessionIdForSend

                getParticipantSignature(participantNode, stx, sessionIdForSend)
            }

            val allSignatures = participantSignatures + getNotarySignature(stx)
            sessions.forEach { send(TOPIC_CHANGE, it.key.address, it.value, allSignatures) }

            return allSignatures
        }

        @Suspendable
        private fun getParticipantSignature(node: NodeInfo, stx: SignedTransaction, sessionIdForSend: Long): DigitalSignature.WithKey {
            val sessionIdForReceive = random63BitValue()
            val proposal = Proposal(originalState.ref, newNotary, stx)

            val handshake = Handshake(sessionIdForSend, serviceHub.networkService.myAddress, sessionIdForReceive)
            sendAndReceive<Ack>(TOPIC_INITIATE, node.address, 0, sessionIdForReceive, handshake)

            val response = sendAndReceive<Result>(TOPIC_CHANGE, node.address, sessionIdForSend, sessionIdForReceive, proposal)
            val participantSignature = response.validate {
                if (it.sig == null) throw NotaryChangeException(it.error!!)
                else {
                    check(it.sig.by == node.identity.owningKey) { "Not signed by the required participant" }
                    it.sig.verifyWithECDSA(stx.txBits)
                    it.sig
                }
            }

            return participantSignature
        }

        @Suspendable
        private fun getNotarySignature(stx: SignedTransaction): DigitalSignature.LegallyIdentifiable {
            progressTracker.currentStep = NOTARY
            return subProtocol(NotaryProtocol.Client(stx))
        }
    }

    class Acceptor(val otherSide: SingleMessageRecipient,
                   val sessionIdForSend: Long,
                   val sessionIdForReceive: Long,
                   override val progressTracker: ProgressTracker = tracker()) : ProtocolLogic<Unit>() {

        companion object {
            object VERIFYING : ProgressTracker.Step("Verifying Notary change proposal")

            object APPROVING : ProgressTracker.Step("Notary change approved")

            object REJECTING : ProgressTracker.Step("Notary change rejected")

            fun tracker() = ProgressTracker(VERIFYING, APPROVING, REJECTING)
        }

        @Suspendable
        override fun call() {
            progressTracker.currentStep = VERIFYING
            val proposal = receive<Proposal>(TOPIC_CHANGE, sessionIdForReceive).validate { it }

            try {
                verifyProposal(proposal)
                verifyTx(proposal.stx)
            } catch(e: Exception) {
                // TODO: catch only specific exceptions. However, there are numerous validation exceptions
                //       that might occur (tx validation/resolution, invalid proposal). Need to rethink how
                //       we manage exceptions and maybe introduce some platform exception hierarchy
                val myIdentity = serviceHub.storageService.myLegalIdentity
                val state = proposal.stateRef
                val reason = NotaryChangeRefused(myIdentity, state, e.message)

                reject(reason)
                return
            }

            approve(proposal.stx)
        }

        @Suspendable
        private fun approve(stx: SignedTransaction) {
            progressTracker.currentStep = APPROVING

            val mySignature = sign(stx)
            val response = Result.noError(mySignature)
            val swapSignatures = sendAndReceive<List<DigitalSignature.WithKey>>(TOPIC_CHANGE, otherSide, sessionIdForSend, sessionIdForReceive, response)

            val allSignatures = swapSignatures.validate { signatures ->
                signatures.forEach { it.verifyWithECDSA(stx.txBits) }
                signatures
            }

            val finalTx = stx + allSignatures
            finalTx.verify()
            serviceHub.recordTransactions(listOf(finalTx))
        }

        @Suspendable
        private fun reject(e: NotaryChangeRefused) {
            progressTracker.currentStep = REJECTING
            val response = Result.withError(e)
            send(TOPIC_CHANGE, otherSide, sessionIdForSend, response)
        }

        /**
         * Check the notary change proposal.
         *
         * For example, if the proposed new notary has the same behaviour (e.g. both are non-validating)
         * and is also in a geographically convenient location we can just automatically approve the change.
         * TODO: In more difficult cases this should call for human attention to manually verify and approve the proposal
         */
        @Suspendable
        private fun verifyProposal(proposal: NotaryChangeProtocol.Proposal) {
            val newNotary = proposal.newNotary
            val isNotary = serviceHub.networkMapCache.notaryNodes.any { it.identity == newNotary }
            require(isNotary) { "The proposed node $newNotary does not run a Notary service " }

            val state = proposal.stateRef
            val proposedTx = proposal.stx.tx
            require(proposedTx.inputs.contains(state)) { "The proposed state $state is not in the proposed transaction inputs" }

            // An example requirement
            val blacklist = listOf("Evil Notary")
            require(!blacklist.contains(newNotary.name)) { "The proposed new notary $newNotary is not trusted by the party" }
        }

        @Suspendable
        private fun verifyTx(stx: SignedTransaction) {
            checkMySignatureRequired(stx.tx)
            checkDependenciesValid(stx)
            checkValid(stx)
        }

        private fun checkMySignatureRequired(tx: WireTransaction) {
            // TODO: use keys from the keyManagementService instead
            val myKey = serviceHub.storageService.myLegalIdentity.owningKey
            require(tx.signers.contains(myKey)) { "Party is not a participant for any of the input states of transaction ${tx.id}" }
        }

        @Suspendable
        private fun checkDependenciesValid(stx: SignedTransaction) {
            val dependencyTxIDs = stx.tx.inputs.map { it.txhash }.toSet()
            subProtocol(ResolveTransactionsProtocol(dependencyTxIDs, otherSide))
        }

        private fun checkValid(stx: SignedTransaction) {
            val ltx = stx.tx.toLedgerTransaction(serviceHub.identityService, serviceHub.storageService.attachments)
            serviceHub.verifyTransaction(ltx)
        }

        private fun sign(stx: SignedTransaction): DigitalSignature.WithKey {
            val myKeyPair = serviceHub.storageService.myLegalIdentityKey
            return myKeyPair.signWithECDSA(stx.txBits)
        }
    }

    // TODO: similar classes occur in other places (NotaryProtocol), need to consolidate
    data class Result private constructor(val sig: DigitalSignature.WithKey?, val error: NotaryChangeRefused?) {
        companion object {
            fun withError(error: NotaryChangeRefused) = Result(null, error)
            fun noError(sig: DigitalSignature.WithKey) = Result(sig, null)
        }
    }
}

/** Thrown when a participant refuses to change the notary of the state */
class NotaryChangeRefused(val identity: Party, val state: StateRef, val cause: String?) {
    override fun toString() = "A participant $identity refused to change the notary of state $state"
}

class NotaryChangeException(val error: NotaryChangeRefused) : Exception() {
    override fun toString() = "${super.toString()}: Notary change failed - ${error.toString()}"
}