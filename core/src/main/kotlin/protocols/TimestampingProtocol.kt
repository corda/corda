package protocols

import co.paralleluniverse.fibers.Suspendable
import core.WireTransaction
import core.crypto.DigitalSignature
import core.messaging.MessageRecipients
import core.node.NodeInfo
import core.protocols.ProtocolLogic
import core.random63BitValue
import core.serialization.SerializedBytes
import core.utilities.ProgressTracker

/**
 * The TimestampingProtocol class is the client code that talks to a [NodeTimestamperService] on some remote node. It is a
 * [ProtocolLogic], meaning it can either be a sub-protocol of some other protocol, or be driven independently.
 *
 * If you are not yourself authoring a protocol and want to timestamp something, the [TimestampingProtocol.Client] class
 * implements the [TimestamperService] interface, meaning it can be passed to [TransactionBuilder.timestamp] to timestamp
 * the built transaction. Please be aware that this will block, meaning it should not be used on a thread that is handling
 * a network message: use it only from spare application threads that don't have to respond to anything.
 */
class TimestampingProtocol(private val node: NodeInfo,
                           private val wtxBytes: SerializedBytes<WireTransaction>,
                           override val progressTracker: ProgressTracker = TimestampingProtocol.tracker()) : ProtocolLogic<DigitalSignature.LegallyIdentifiable>() {

    companion object {
        object REQUESTING : ProgressTracker.Step("Requesting signature by timestamping service")
        object VALIDATING : ProgressTracker.Step("Validating received signature from timestamping service")

        fun tracker() = ProgressTracker(REQUESTING, VALIDATING)

        val TOPIC = "platform.timestamping.request"
    }


    @Suspendable
    override fun call(): DigitalSignature.LegallyIdentifiable {
        progressTracker.currentStep = REQUESTING
        val sessionID = random63BitValue()
        val req = Request(wtxBytes, serviceHub.networkService.myAddress, sessionID)

        val maybeSignature = sendAndReceive<DigitalSignature.LegallyIdentifiable>(TOPIC, node.address, 0, sessionID, req)

        // Check that the timestamping authority gave us back a valid signature and didn't break somehow
        progressTracker.currentStep = VALIDATING
        maybeSignature.validate { sig ->
            sig.verifyWithECDSA(wtxBytes)
            return sig
        }
    }

    class Request(val tx: SerializedBytes<WireTransaction>, replyTo: MessageRecipients, sessionID: Long) : AbstractRequestMessage(replyTo, sessionID)
}