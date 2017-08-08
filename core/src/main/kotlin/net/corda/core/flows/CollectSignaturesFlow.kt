package net.corda.core.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.crypto.TransactionSignature
import net.corda.core.crypto.isFulfilledBy
import net.corda.core.crypto.toBase58String
import net.corda.core.identity.AnonymousPartyAndPath
import net.corda.core.identity.Party
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.node.ServiceHub
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.WireTransaction
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap
import java.security.PublicKey

/**
 * The [CollectSignaturesFlow] is used to automate the collection of counter-party signatures for a given transaction.
 *
 * You would typically use this flow after you have built a transaction with the TransactionBuilder and signed it with
 * your key pair. If there are additional signatures to collect then they can be collected using this flow. Signatures
 * are collected based upon the [WireTransaction.requiredSigningKeys] property which contains the union of all the PublicKeys
 * listed in the transaction's commands as well as a notary's public key, if required. This flow returns a
 * [SignedTransaction] which can then be passed to the [FinalityFlow] for notarisation. The other side of this flow is
 * the [SignTransactionFlow].
 *
 * **WARNING**: This flow ONLY works with [ServiceHub.legalIdentityKey]s and WILL break if used with randomly generated
 * keys by the [ServiceHub.keyManagementService].
 *
 * Usage:
 *
 * - Call the [CollectSignaturesFlow] flow as a [subFlow] and pass it a [SignedTransaction] which has at least been
 *   signed by the transaction creator (and possibly an oracle, if required)
 * - The flow expects that the calling node has signed the provided transaction, if not the flow will fail
 * - The flow will also fail if:
 *   1. The provided transaction is invalid
 *   2. Any of the required signing parties cannot be found in the [ServiceHub.networkMapCache] of the initiator
 *   3. If the wrong key has been used by a counterparty to sign the transaction
 *   4. The counterparty rejects the provided transaction
 * - The flow will return a [SignedTransaction] with all the counter-party signatures (but not the notary's!)
 * - If the provided transaction has already been signed by all counter-parties then this flow simply returns the
 *   provided transaction without contacting any counter-parties
 * - Call the [FinalityFlow] with the return value of this flow
 *
 * Example - issuing a multi-lateral agreement which requires N signatures:
 *
 *     val builder = TransactionBuilder(notaryRef)
 *     val issueCommand = Command(Agreement.Commands.Issue(), state.participants)
 *
 *     builder.withItems(state, issueCommand)
 *     builder.toWireTransaction().toLedgerTransaction(serviceHub).verify()
 *
 *     // Transaction creator signs transaction.
 *     val ptx = builder.signWith(serviceHub.legalIdentityKey).toSignedTransaction(false)
 *
 *     // Call to CollectSignaturesFlow.
 *     // The returned signed transaction will have all signatures appended apart from the notary's.
 *     val stx = subFlow(CollectSignaturesFlow(ptx))
 *
 * @param partiallySignedTx Transaction to collect the remaining signatures for
 * @param outputIdentities mapping from well known identities to anonymised identities used in the transaction outputs.
 * @param myInputKeys set of keys in the transaction which are owned by this node. This includes keys used on commands, not
 * just in the output states.
 */
// TODO: AbstractStateReplacementFlow needs updating to use this flow.
class CollectSignaturesFlow @JvmOverloads constructor (val partiallySignedTx: SignedTransaction,
                            val outputIdentities: Map<Party, AnonymousPartyAndPath>,
                            val myInputKeys: Iterable<PublicKey>,
                            override val progressTracker: ProgressTracker = CollectSignaturesFlow.tracker()) : FlowLogic<SignedTransaction>() {
    @JvmOverloads constructor (partiallySignedTx: SignedTransaction, myInputKeys: Iterable<PublicKey>, progressTracker: ProgressTracker = CollectSignaturesFlow.tracker()) : this(partiallySignedTx, emptyMap(), myInputKeys, progressTracker)
    companion object {
        object COLLECTING : ProgressTracker.Step("Collecting signatures from counterparties.")
        object VERIFYING : ProgressTracker.Step("Verifying collected signatures.")

        fun tracker() = ProgressTracker(COLLECTING, VERIFYING)

        // TODO: Make the progress tracker adapt to the number of counter-parties to collect from.
    }

    @Suspendable override fun call(): SignedTransaction {
        val myInputIdentities: List<PartyAndCertificate> = myInputKeys.map { serviceHub.identityService.certificateFromKey(it) }.requireNoNulls()
        val myOutputIdentity = outputIdentities[serviceHub.myInfo.legalIdentity]?.party ?: serviceHub.myInfo.legalIdentity
        val myKeys = (myInputKeys + myOutputIdentity.owningKey).toSet()

        // Check the signatures which have already been provided and that the transaction is valid.
        // Usually just the Initiator and possibly an oracle would have signed at this point.
        val signed = partiallySignedTx.sigs.map { it.by }
        val notSigned = partiallySignedTx.tx.requiredSigningKeys - signed

        // One of the signatures collected so far MUST be from the initiator of this flow.
        require(partiallySignedTx.sigs.any { it.by in myKeys }) {
            "The Initiator of CollectSignaturesFlow must have signed the transaction."
        }

        // The signatures must be valid and the transaction must be valid.
        partiallySignedTx.verifySignaturesExcept(*notSigned.toTypedArray())
        partiallySignedTx.tx.toLedgerTransaction(serviceHub).verify()

        // Determine who still needs to sign.
        progressTracker.currentStep = COLLECTING
        val notaryKey = partiallySignedTx.tx.notary?.owningKey
        // If present, we need to exclude the notary's PublicKey as the notary signature is collected separately with
        // the FinalityFlow.
        val unsigned = if (notaryKey != null) notSigned - notaryKey else notSigned

        // If the unsigned counter-parties list is empty then we don't need to collect any more signatures here.
        if (unsigned.isEmpty()) return partiallySignedTx

        // Collect signatures from all counter-parties and append them to the partially signed transaction.
        val counterpartySignatures = keysToParties(unsigned).map { collectSignature(it, myInputIdentities) }
        val stx = partiallySignedTx + counterpartySignatures

        // Verify all but the notary's signature if the transaction requires a notary, otherwise verify all signatures.
        progressTracker.currentStep = VERIFYING
        if (notaryKey != null) stx.verifySignaturesExcept(notaryKey) else stx.verifyRequiredSignatures()

        return stx
    }

    /**
     * Lookup the [Party] object for each [PublicKey] using the [ServiceHub.networkMapCache].
     */
    @Suspendable private fun keysToParties(keys: Collection<PublicKey>): List<Party> = keys.map {
        // TODO: Revisit when IdentityService supports resolution of a (possibly random) public key to a legal identity key.
        val partyNode = serviceHub.networkMapCache.getNodeByLegalIdentityKey(it)
                ?: throw IllegalStateException("Party ${it.toBase58String()} not found on the network.")
        partyNode.legalIdentity
    }

    // DOCSTART 1
    /**
     * Get and check the required signature.
     */
    @Suspendable private fun collectSignature(counterparty: Party, myIdentities: List<PartyAndCertificate>): TransactionSignature {
        // SendTransactionFlow allows otherParty to access our data to resolve the transaction.
        send(counterparty, myIdentities)
        subFlow(SendTransactionFlow(counterparty, partiallySignedTx))
        return receive<TransactionSignature>(counterparty).unwrap {
            require(counterparty.owningKey.isFulfilledBy(it.by)) { "Not signed by the required Party." }
            it
        }
    }
    // DOCEND 1
}

/**
 * The [SignTransactionFlow] should be called in response to the [CollectSignaturesFlow]. It automates the signing of
 * a transaction providing the transaction:
 *
 * 1. Should actually be signed by the [Party] invoking this flow
 * 2. Is valid as per the contracts referenced in the transaction
 * 3. Has been, at least, signed by the counter-party which created it
 * 4. Conforms to custom checking provided in the [checkTransaction] method of the [SignTransactionFlow]
 *
 * Usage:
 *
 * - Subclass [SignTransactionFlow] - this can be done inside an existing flow (as shown below)
 * - Override the [checkTransaction] method to add some custom verification logic
 * - Call the flow via [FlowLogic.subFlow]
 * - The flow returns the fully signed transaction once it has been committed to the ledger
 *
 * Example - checking and signing a transaction involving a [net.corda.core.contracts.DummyContract], see
 * CollectSignaturesFlowTests.kt for further examples:
 *
 *     class Responder(val otherParty: Party): FlowLogic<SignedTransaction>() {
 *          @Suspendable override fun call(): SignedTransaction {
 *              // [SignTransactionFlow] sub-classed as a singleton object.
 *              val flow = object : SignTransactionFlow(otherParty) {
 *                  @Suspendable override fun checkTransaction(stx: SignedTransaction) = requireThat {
 *                      val tx = stx.tx
 *                      val magicNumberState = tx.outputs.single().data as DummyContract.MultiOwnerState
 *                      "Must be 1337 or greater" using (magicNumberState.magicNumber >= 1337)
 *                  }
 *              }
 *
 *              // Invoke the subFlow, in response to the counterparty calling [CollectSignaturesFlow].
 *              val stx = subFlow(flow)
 *
 *              return waitForLedgerCommit(stx.id)
 *          }
 *      }
 *
 * @param otherParty The counter-party which is providing you a transaction to sign.
 */
abstract class SignTransactionFlow(val otherParty: Party,
                                   override val progressTracker: ProgressTracker = SignTransactionFlow.tracker()) : FlowLogic<SignedTransaction>() {

    companion object {
        object RECEIVING : ProgressTracker.Step("Receiving transaction proposal for signing.")
        object VERIFYING : ProgressTracker.Step("Verifying transaction proposal.")
        object SIGNING : ProgressTracker.Step("Signing transaction proposal.")

        fun tracker() = ProgressTracker(RECEIVING, VERIFYING, SIGNING)
    }

    @Suspendable override fun call(): SignedTransaction {
        progressTracker.currentStep = RECEIVING
        // Receive any additional identities required for verifying the proposed transaction.
        val additionalIdentities = receive<List<PartyAndCertificate>>(otherParty).unwrap { it }
        // Receive transaction and resolve dependencies, check sufficient signatures is disabled as we don't have all signatures.
        val stx = subFlow(ReceiveTransactionFlow(otherParty, checkSufficientSignatures = false))
        progressTracker.currentStep = VERIFYING
        // Register the additional identities
        additionalIdentities.forEach { serviceHub.identityService.registerIdentity(it) }
        // Check that the Responder actually needs to sign.
        checkMySignatureRequired(stx)
        // Check the signatures which have already been provided. Usually the Initiators and possibly an Oracle's.
        checkSignatures(stx)
        stx.tx.toLedgerTransaction(serviceHub).verify()
        // Perform some custom verification over the transaction.
        try {
            checkTransaction(stx)
        } catch(e: Exception) {
            if (e is IllegalStateException || e is IllegalArgumentException || e is AssertionError)
                throw FlowException(e)
            else
                throw e
        }
        // Sign and send back our signature to the Initiator.
        progressTracker.currentStep = SIGNING
        val mySignature = serviceHub.createSignature(stx)
        send(otherParty, mySignature)

        // Return the fully signed transaction once it has been committed.
        return waitForLedgerCommit(stx.id)
    }

    @Suspendable private fun checkSignatures(stx: SignedTransaction) {
        val signingIdentities = stx.sigs.map { serviceHub.identityService.partyFromKey(it.by) }.filterNotNull()
        require(otherParty in signingIdentities) {
            "The Initiator of CollectSignaturesFlow must have signed the transaction. Found ${signingIdentities}"
        }
        val signed = stx.sigs.map { it.by }
        val allSigners = stx.tx.requiredSigningKeys
        val notSigned = allSigners - signed
        stx.verifySignaturesExcept(*notSigned.toTypedArray())
    }

    /**
     * The [checkTransaction] method allows the caller of this flow to provide some additional checks over the proposed
     * transaction received from the counter-party. For example:
     *
     * - Ensuring that the transaction you are receiving is the transaction you *EXPECT* to receive. I.e. is has the
     *   expected type and number of inputs and outputs
     * - Checking that the properties of the outputs are as you would expect. Linking into any reference data sources
     *   might be appropriate here
     * - Checking that the transaction is not incorrectly spending (perhaps maliciously) one of your asset states, as
     *   potentially the transaction creator has access to some of your state references
     *
     * **WARNING**: If appropriate checks, such as the ones listed above, are not defined then it is likely that your
     * node will sign any transaction if it conforms to the contract code in the transaction's referenced contracts.
     *
     * [IllegalArgumentException], [IllegalStateException] and [AssertionError] will be caught and rethrown as flow
     * exceptions i.e. the other side will be given information about what exact check failed.
     *
     * @param stx a partially signed transaction received from your counter-party.
     * @throws FlowException if the proposed transaction fails the checks.
     */
    @Suspendable abstract protected fun checkTransaction(stx: SignedTransaction)

    @Suspendable private fun checkMySignatureRequired(stx: SignedTransaction) {
        // TODO: Revisit when key management is properly fleshed out.
        val myKey = serviceHub.myInfo.legalIdentity.owningKey
        require(myKey in stx.tx.requiredSigningKeys) {
            "Party is not a participant for any of the input states of transaction ${stx.id}"
        }
    }
}
