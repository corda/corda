package protocols

import co.paralleluniverse.fibers.Suspendable
import com.r3corda.core.contracts.*
import com.r3corda.core.crypto.DigitalSignature
import com.r3corda.core.crypto.Party
import com.r3corda.core.crypto.signWithECDSA
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
                        val sessionIdForSend: Long,
                        val sessionIdForReceive: Long)

    class Handshake(val payload: Proposal,
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

            val signatures = mutableListOf<DigitalSignature.WithKey>()

            val myKey = serviceHub.storageService.myLegalIdentity.owningKey
            val me = listOf(myKey)

            if (participants == me) {
                signatures.add(getNotarySignature(stx.tx))
            } else {
                val participantSessions = collectSignatures(participants - me, signatures, stx)
                signatures.add(getNotarySignature(stx.tx))

                participantSessions.forEach { send(TOPIC_CHANGE, it.first.address, it.second, signatures) }
            }

            val finalTx = stx + signatures
            serviceHub.recordTransactions(listOf(finalTx))
            return finalTx.tx.outRef(0)
        }

        private fun assembleTx(): Pair<SignedTransaction, List<PublicKey>> {
            val state = originalState.state
            val newState = state.withNewNotary(newNotary)
            val participants = state.participants
            val cmd = Command(ChangeNotary(), participants)
            val tx = TransactionBuilder().withItems(originalState.ref, newState, cmd)
            tx.signWith(serviceHub.storageService.myLegalIdentityKey)

            val stx = tx.toSignedTransaction(false)
            return Pair(stx, participants)
        }

        @Suspendable
        private fun collectSignatures(participants: List<PublicKey>, signatures: MutableCollection<DigitalSignature.WithKey>,
                                      stx: SignedTransaction): MutableList<Pair<NodeInfo, Long>> {
            val participantSessions = mutableListOf<Pair<NodeInfo, Long>>()

            participants.forEach {
                val participantNode = serviceHub.networkMapCache.getNodeByPublicKey(it) ?:
                        throw IllegalStateException("Participant $it to state $originalState not found on the network")
                val sessionIdForSend = random63BitValue()
                val participantSignature = getParticipantSignature(participantNode, stx, sessionIdForSend)
                signatures.add(participantSignature)

                participantSessions.add(participantNode to sessionIdForSend)
            }

            return participantSessions
        }

        @Suspendable
        private fun getParticipantSignature(node: NodeInfo, stx: SignedTransaction, sessionIdForSend: Long): DigitalSignature.WithKey {
            val sessionIdForReceive = random63BitValue()
            val proposal = Proposal(originalState.ref, newNotary, sessionIdForSend, sessionIdForReceive)

            val handshake = Handshake(proposal, serviceHub.networkService.myAddress, sessionIdForReceive)
            val protocolInitiated = sendAndReceive<Boolean>(TOPIC_INITIATE, node.address, 0, sessionIdForReceive, handshake).validate { it }
            if (!protocolInitiated) throw Refused(node.identity, originalState)

            val response = sendAndReceive<DigitalSignature.WithKey>(TOPIC_CHANGE, node.address, sessionIdForSend, sessionIdForReceive, stx)
            val participantSignature = response.validate {
                check(it.by == node.identity.owningKey) { "Not signed by the required participant" }
                it.verifyWithECDSA(stx.txBits)
                it
            }
            return participantSignature
        }

        @Suspendable
        private fun getNotarySignature(wtx: WireTransaction): DigitalSignature.LegallyIdentifiable {
            progressTracker.currentStep = NOTARY
            return subProtocol(NotaryProtocol(wtx))
        }
    }

    class Acceptor(val otherSide: SingleMessageRecipient,
                   val sessionIdForSend: Long,
                   val sessionIdForReceive: Long,
                   override val progressTracker: ProgressTracker = tracker()) : ProtocolLogic<Unit>() {

        companion object {
            object VERIFYING : ProgressTracker.Step("Verifying Notary change proposal")

            object SIGNING : ProgressTracker.Step("Signing Notary change transaction")

            fun tracker() = ProgressTracker(VERIFYING, SIGNING)
        }

        @Suspendable
        override fun call() {
            progressTracker.currentStep = VERIFYING

            val proposedTx = receive<SignedTransaction>(TOPIC_CHANGE, sessionIdForReceive).validate { validateTx(it) }

            progressTracker.currentStep = SIGNING

            val mySignature = sign(proposedTx)
            val swapSignatures = sendAndReceive<List<DigitalSignature.WithKey>>(TOPIC_CHANGE, otherSide, sessionIdForSend, sessionIdForReceive, mySignature)

            val allSignatures = swapSignatures.validate {
                it.forEach { it.verifyWithECDSA(proposedTx.txBits) }
                it
            }

            val finalTx = proposedTx + allSignatures
            serviceHub.recordTransactions(listOf(finalTx))
        }

        @Suspendable
        private fun validateTx(stx: SignedTransaction): SignedTransaction {
            checkDependenciesValid(stx)
            checkContractValid(stx)
            checkCommand(stx.tx)
            return stx
        }

        private fun checkCommand(tx: WireTransaction) {
            val command = tx.commands.single { it.value is ChangeNotary }
            val myKey = serviceHub.storageService.myLegalIdentityKey.public
            val myIdentity = serviceHub.storageService.myLegalIdentity
            val state = tx.inputs.first()
            require(command.signers.contains(myKey)) { "Party $myIdentity is not a participant for the state: $state" }
        }

        @Suspendable
        private fun checkDependenciesValid(stx: SignedTransaction) {
            val dependencyTxIDs = stx.tx.inputs.map { it.txhash }.toSet()
            subProtocol(ResolveTransactionsProtocol(dependencyTxIDs, otherSide))
        }

        private fun checkContractValid(stx: SignedTransaction) {
            val ltx = stx.tx.toLedgerTransaction(serviceHub.identityService, serviceHub.storageService.attachments)
            serviceHub.verifyTransaction(ltx)
        }

        private fun sign(stx: SignedTransaction): DigitalSignature.WithKey {
            val myKeyPair = serviceHub.storageService.myLegalIdentityKey
            return myKeyPair.signWithECDSA(stx.txBits)
        }
    }

    /** Thrown when a participant refuses to change the notary of the state */
    class Refused(val identity: Party, val originalState: StateAndRef<*>) : Exception() {
        override fun toString() = "A participant $identity refused to change the notary of state $originalState"
    }
}