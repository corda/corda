package net.corda.core.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.crypto.TransactionSignature
import net.corda.core.crypto.isFulfilledBy
import net.corda.core.identity.Party
import net.corda.core.identity.groupPublicKeysByWellKnownParty
import net.corda.core.node.ServiceHub
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.WireTransaction
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap
import java.security.PublicKey

/**
 * The [CollectSignaturesFlow] is used to automate the collection of counterparty signatures for a given transaction.
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
 * - The flow will return a [SignedTransaction] with all the counterparty signatures (but not the notary's!)
 * - If the provided transaction has already been signed by all counterparties then this flow simply returns the
 *   provided transaction without contacting any counterparties
 * - Call the [FinalityFlow] with the return value of this flow
 *
 * Example - issuing a multi-lateral agreement which requires N signatures:
 *
 *     val builder = TransactionBuilder(notaryRef)
 *     val issueCommand = Command(Agreement.Commands.Issue(), state.participants)
 *
 *     builder.withItems(state, issueCommand)
 *     builder.toWireTransaction2().toLedgerTransaction(serviceHub).verify()
 *
 *     // Transaction creator signs transaction.
 *     val ptx = serviceHub.signInitialTransaction(builder)
 *
 *     // Call to CollectSignaturesFlow.
 *     // The returned signed transaction will have all signatures appended apart from the notary's.
 *     val stx = subFlow(CollectSignaturesFlow(ptx))
 *
 * @param partiallySignedTx Transaction to collect the remaining signatures for
 * @param sessionsToCollectFrom A session for every party we need to collect a signature from.  Must be an exact match.
 * @param myOptionalKeys set of keys in the transaction which are owned by this node. This includes keys used on commands, not
 * just in the states. If null, the default well known identity of the node is used.
 */
// TODO: AbstractStateReplacementFlow needs updating to use this flow.
class CollectSignaturesFlow @JvmOverloads constructor(val partiallySignedTx: SignedTransaction,
                                                      val sessionsToCollectFrom: Collection<FlowSession>,
                                                      val myOptionalKeys: Iterable<PublicKey>?,
                                                      override val progressTracker: ProgressTracker = CollectSignaturesFlow.tracker()) : FlowLogic<SignedTransaction>() {
    @JvmOverloads constructor(partiallySignedTx: SignedTransaction, sessionsToCollectFrom: Collection<FlowSession>, progressTracker: ProgressTracker = CollectSignaturesFlow.tracker()) : this(partiallySignedTx, sessionsToCollectFrom, null, progressTracker)

    companion object {
        object COLLECTING : ProgressTracker.Step("Collecting signatures from counterparties.")
        object VERIFYING : ProgressTracker.Step("Verifying collected signatures.")

        @JvmStatic
        fun tracker() = ProgressTracker(COLLECTING, VERIFYING)

        // TODO: Make the progress tracker adapt to the number of counterparties to collect from.

    }

    @Suspendable
    override fun call(): SignedTransaction {
        // Check the signatures which have already been provided and that the transaction is valid.
        // Usually just the Initiator and possibly an oracle would have signed at this point.
        val myKeys: Iterable<PublicKey> = myOptionalKeys ?: listOf(ourIdentity.owningKey)
        val signed = partiallySignedTx.sigs.map { it.by }
        val notSigned = partiallySignedTx.tx.requiredSigningKeys - signed

        // One of the signatures collected so far MUST be from the initiator of this flow.
        require(partiallySignedTx.sigs.any { it.by in myKeys }) {
            "The Initiator of CollectSignaturesFlow must have signed the transaction."
        }

        // The signatures must be valid and the transaction must be valid.
        partiallySignedTx.verifySignaturesExcept(notSigned)
        partiallySignedTx.tx.toLedgerTransaction(serviceHub).verify()

        // Determine who still needs to sign.
        progressTracker.currentStep = COLLECTING
        val notaryKey = partiallySignedTx.tx.notary?.owningKey
        // If present, we need to exclude the notary's PublicKey as the notary signature is collected separately with
        // the FinalityFlow.
        val unsigned = if (notaryKey != null) notSigned - notaryKey else notSigned

        // If the unsigned counterparties list is empty then we don't need to collect any more signatures here.
        if (unsigned.isEmpty()) return partiallySignedTx

        val partyToKeysMap = groupPublicKeysByWellKnownParty(serviceHub, unsigned)
        // Check that we have a session for all parties.  No more, no less.
        require(sessionsToCollectFrom.map { it.counterparty }.toSet() == partyToKeysMap.keys) {
            "The Initiator of CollectSignaturesFlow must pass in exactly the sessions required to sign the transaction."
        }
        // Collect signatures from all counterparties and append them to the partially signed transaction.
        val counterpartySignatures = sessionsToCollectFrom.flatMap { session ->
            subFlow(CollectSignatureFlow(partiallySignedTx, session, partyToKeysMap[session.counterparty]!!))
        }
        val stx = partiallySignedTx + counterpartySignatures

        // Verify all but the notary's signature if the transaction requires a notary, otherwise verify all signatures.
        progressTracker.currentStep = VERIFYING
        if (notaryKey != null) stx.verifySignaturesExcept(notaryKey) else stx.verifyRequiredSignatures()

        return stx
    }
}

// DOCSTART 1
/**
 * Get and check the required signature.
 *
 * @param partiallySignedTx the transaction to sign.
 * @param session the [FlowSession] to connect to to get the signature.
 * @param signingKeys the list of keys the party should use to sign the transaction.
 */
@Suspendable
class CollectSignatureFlow(val partiallySignedTx: SignedTransaction, val session: FlowSession, val signingKeys: List<PublicKey>) : FlowLogic<List<TransactionSignature>>() {
    constructor(partiallySignedTx: SignedTransaction, session: FlowSession, vararg signingKeys: PublicKey) :
            this(partiallySignedTx, session, listOf(*signingKeys))

    @Suspendable
    override fun call(): List<TransactionSignature> {
        // SendTransactionFlow allows counterparty to access our data to resolve the transaction.
        subFlow(SendTransactionFlow(session, partiallySignedTx))
        // Send the key we expect the counterparty to sign with - this is important where they may have several
        // keys to sign with, as it makes it faster for them to identify the key to sign with, and more straight forward
        // for us to check we have the expected signature returned.
        session.send(signingKeys)
        return session.receive<List<TransactionSignature>>().unwrap { signatures ->
            require(signatures.size == signingKeys.size) { "Need signature for each signing key" }
            signatures.forEachIndexed { index, signature ->
                require(signingKeys[index].isFulfilledBy(signature.by)) { "Not signed by the required signing key." }
            }
            signatures
        }
    }
}
// DOCEND 1

/**
 * The [SignTransactionFlow] should be called in response to the [CollectSignaturesFlow]. It automates the signing of
 * a transaction providing the transaction:
 *
 * 1. Should actually be signed by the [Party] invoking this flow
 * 2. Is valid as per the contracts referenced in the transaction
 * 3. Has been, at least, signed by the counterparty which created it
 * 4. Conforms to custom checking provided in the [checkTransaction] method of the [SignTransactionFlow]
 *
 * Usage:
 *
 * - Subclass [SignTransactionFlow] - this can be done inside an existing flow (as shown below)
 * - Override the [checkTransaction] method to add some custom verification logic
 * - Call the flow via [FlowLogic.subFlow]
 * - The flow returns the transaction signed with the additional signature.
 *
 * Example - checking and signing a transaction involving a [net.corda.core.contracts.DummyContract], see
 * CollectSignaturesFlowTests.kt for further examples:
 *
 *     class Responder(val otherPartySession: FlowSession): FlowLogic<SignedTransaction>() {
 *          @Suspendable override fun call(): SignedTransaction {
 *              // [SignTransactionFlow] sub-classed as a singleton object.
 *              val flow = object : SignTransactionFlow(otherPartySession) {
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
 * @param otherSideSession The session which is providing you a transaction to sign.
 */
abstract class SignTransactionFlow @JvmOverloads constructor(val otherSideSession: FlowSession,
                                   override val progressTracker: ProgressTracker = SignTransactionFlow.tracker()) : FlowLogic<SignedTransaction>() {

    companion object {
        object RECEIVING : ProgressTracker.Step("Receiving transaction proposal for signing.")
        object VERIFYING : ProgressTracker.Step("Verifying transaction proposal.")
        object SIGNING : ProgressTracker.Step("Signing transaction proposal.")

        @JvmStatic
        fun tracker() = ProgressTracker(RECEIVING, VERIFYING, SIGNING)
    }

    @Suspendable
    override fun call(): SignedTransaction {
        progressTracker.currentStep = RECEIVING
        // Receive transaction and resolve dependencies, check sufficient signatures is disabled as we don't have all signatures.
        val stx = subFlow(ReceiveTransactionFlow(otherSideSession, checkSufficientSignatures = false))
        // Receive the signing key that the party requesting the signature expects us to sign with. Having this provided
        // means we only have to check we own that one key, rather than matching all keys in the transaction against all
        // keys we own.
        val signingKeys = otherSideSession.receive<List<PublicKey>>().unwrap { keys ->
            // TODO: We should have a faster way of verifying we own a single key
            serviceHub.keyManagementService.filterMyKeys(keys)
        }
        progressTracker.currentStep = VERIFYING
        // Check that the Responder actually needs to sign.
        checkMySignaturesRequired(stx, signingKeys)
        // Check the signatures which have already been provided. Usually the Initiators and possibly an Oracle's.
        checkSignatures(stx)
        stx.tx.toLedgerTransaction(serviceHub).verify()
        // Perform some custom verification over the transaction.
        try {
            checkTransaction(stx)
        } catch (e: Exception) {
            if (e is IllegalStateException || e is IllegalArgumentException || e is AssertionError)
                throw FlowException(e)
            else
                throw e
        }
        // Sign and send back our signature to the Initiator.
        progressTracker.currentStep = SIGNING
        val mySignatures = signingKeys.map { key ->
            serviceHub.createSignature(stx, key)
        }
        otherSideSession.send(mySignatures)

        // Return the additionally signed transaction.
        return stx + mySignatures
    }

    @Suspendable
    private fun checkSignatures(stx: SignedTransaction) {
        // We set `ignoreUnrecognisedParties` to `true` in `groupPublicKeysByWellKnownParty`. This is because we don't 
        // need to recognise all keys, but just the initiator's.
        val signingWellKnownIdentities = groupPublicKeysByWellKnownParty(serviceHub, stx.sigs.map(TransactionSignature::by), true)
        require(otherSideSession.counterparty in signingWellKnownIdentities) {
            "The Initiator of CollectSignaturesFlow must have signed the transaction. Found $signingWellKnownIdentities, expected $otherSideSession"
        }
        val signed = stx.sigs.map { it.by }
        val allSigners = stx.tx.requiredSigningKeys
        val notSigned = allSigners - signed
        stx.verifySignaturesExcept(notSigned)
    }

    /**
     * The [checkTransaction] method allows the caller of this flow to provide some additional checks over the proposed
     * transaction received from the counterparty. For example:
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
     * @param stx a partially signed transaction received from your counterparty.
     * @throws FlowException if the proposed transaction fails the checks.
     */
    @Suspendable
    @Throws(FlowException::class)
    protected abstract fun checkTransaction(stx: SignedTransaction)

    @Suspendable
    private fun checkMySignaturesRequired(stx: SignedTransaction, signingKeys: Iterable<PublicKey>) {
        require(signingKeys.all { it in stx.tx.requiredSigningKeys }) {
            "A signature was requested for a key that isn't part of the required signing keys for transaction ${stx.id}"
        }
    }
}
