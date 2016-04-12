package protocols

import co.paralleluniverse.fibers.Suspendable
import core.Party
import core.TimestampCommand
import core.WireTransaction
import core.crypto.DigitalSignature
import core.messaging.SingleMessageRecipient
import core.node.NodeInfo
import core.node.services.NotaryError
import core.node.services.NotaryException
import core.node.services.NotaryService
import core.protocols.ProtocolLogic
import core.random63BitValue
import core.serialization.SerializedBytes
import core.utilities.ProgressTracker
import core.utilities.UntrustworthyData
import java.security.PublicKey

/**
 * A protocol to be used for obtaining a signature from a [NotaryService] ascertaining the transaction
 * timestamp is correct and none of its inputs have been used in another completed transaction
 *
 * @throws NotaryException in case the any of the inputs to the transaction have been consumed
 *                         by another transaction or the timestamp is invalid
 */
class NotaryProtocol(private val wtx: WireTransaction,
                     override val progressTracker: ProgressTracker = NotaryProtocol.tracker()) : ProtocolLogic<DigitalSignature.LegallyIdentifiable>() {
    companion object {
        val TOPIC = "platform.notary.request"

        object REQUESTING : ProgressTracker.Step("Requesting signature by Notary service")

        object VALIDATING : ProgressTracker.Step("Validating response from Notary service")

        fun tracker() = ProgressTracker(REQUESTING, VALIDATING)
    }

    lateinit var notaryNode: NodeInfo

    @Suspendable
    override fun call(): DigitalSignature.LegallyIdentifiable {
        progressTracker.currentStep = REQUESTING
        notaryNode = findNotaryNode()

        val sessionID = random63BitValue()
        val request = SignRequest(wtx.serialized, serviceHub.storageService.myLegalIdentity, serviceHub.networkService.myAddress, sessionID)
        val response = sendAndReceive<NotaryService.Result>(TOPIC, notaryNode.address, 0, sessionID, request)

        val notaryResult = validateResponse(response)
        return notaryResult.sig ?: throw NotaryException(notaryResult.error!!)
    }

    private fun validateResponse(response: UntrustworthyData<NotaryService.Result>): NotaryService.Result {
        progressTracker.currentStep = VALIDATING

        response.validate {
            if (it.sig != null) validateSignature(it.sig, wtx.serialized)
            else if (it.error is NotaryError.Conflict) it.error.conflict.verified()
            else if (it.error !is NotaryError)
                throw IllegalStateException("Received invalid result from Notary service '${notaryNode.identity}'")
            return it
        }
    }

    private fun validateSignature(sig: DigitalSignature.LegallyIdentifiable, data: SerializedBytes<WireTransaction>) {
        check(sig.signer == notaryNode.identity) { "Notary result not signed by the correct service" }
        sig.verifyWithECDSA(data)
    }

    private fun findNotaryNode(): NodeInfo {
        var maybeNotaryKey: PublicKey? = null

        val timestampCommand = wtx.commands.singleOrNull { it.value is TimestampCommand }
        if (timestampCommand != null) maybeNotaryKey = timestampCommand.signers.first()

        for (stateRef in wtx.inputs) {
            val inputNotaryKey = serviceHub.loadState(stateRef).notary.owningKey
            if (maybeNotaryKey != null)
                check(maybeNotaryKey == inputNotaryKey) { "Input states and timestamp must have the same Notary" }
            else maybeNotaryKey = inputNotaryKey
        }

        val notaryKey = maybeNotaryKey ?: throw IllegalStateException("Transaction does not specify a Notary")
        val notaryNode = serviceHub.networkMapCache.getNodeByPublicKey(notaryKey)
        return notaryNode ?: throw IllegalStateException("No Notary node can be found with the specified public key")
    }

    /** TODO: The caller must authenticate instead of just specifying its identity */
    class SignRequest(val txBits: SerializedBytes<WireTransaction>,
                      val callerIdentity: Party,
                      replyTo: SingleMessageRecipient,
                      sessionID: Long) : AbstractRequestMessage(replyTo, sessionID)
}