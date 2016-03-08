/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package protocols

import co.paralleluniverse.fibers.Suspendable
import core.Party
import core.WireTransaction
import core.crypto.DigitalSignature
import core.messaging.LegallyIdentifiableNode
import core.messaging.MessageRecipients
import core.messaging.StateMachineManager
import core.node.services.NodeTimestamperService
import core.node.services.TimestamperService
import core.protocols.ProtocolLogic
import core.random63BitValue
import core.serialization.SerializedBytes

/**
 * The TimestampingProtocol class is the client code that talks to a [NodeTimestamperService] on some remote node. It is a
 * [ProtocolLogic], meaning it can either be a sub-protocol of some other protocol, or be driven independently.
 *
 * If you are not yourself authoring a protocol and want to timestamp something, the [TimestampingProtocol.Client] class
 * implements the [TimestamperService] interface, meaning it can be passed to [TransactionBuilder.timestamp] to timestamp
 * the built transaction. Please be aware that this will block, meaning it should not be used on a thread that is handling
 * a network message: use it only from spare application threads that don't have to respond to anything.
 */
class TimestampingProtocol(private val node: LegallyIdentifiableNode,
                           private val wtxBytes: SerializedBytes<WireTransaction>) : ProtocolLogic<DigitalSignature.LegallyIdentifiable>() {

    class Client(private val stateMachineManager: StateMachineManager, private val node: LegallyIdentifiableNode) : TimestamperService {
        override val identity: Party = node.identity

        override fun timestamp(wtxBytes: SerializedBytes<WireTransaction>): DigitalSignature.LegallyIdentifiable {
            return stateMachineManager.add("platform.timestamping", TimestampingProtocol(node, wtxBytes)).get()
        }
    }


    @Suspendable
    override fun call(): DigitalSignature.LegallyIdentifiable {
        val sessionID = random63BitValue()
        val replyTopic = "${NodeTimestamperService.TIMESTAMPING_PROTOCOL_TOPIC}.$sessionID"
        val req = Request(wtxBytes, serviceHub.networkService.myAddress, replyTopic)

        val maybeSignature = sendAndReceive<DigitalSignature.LegallyIdentifiable>(
                NodeTimestamperService.TIMESTAMPING_PROTOCOL_TOPIC, node.address, 0, sessionID, req)

        // Check that the timestamping authority gave us back a valid signature and didn't break somehow
        maybeSignature.validate { sig ->
            sig.verifyWithECDSA(wtxBytes)
            return sig
        }
    }

    // TODO: Improve the messaging api to have a notion of sender+replyTo topic (optional?)
    data class Request(val tx: SerializedBytes<WireTransaction>, val replyTo: MessageRecipients, val replyToTopic: String)
}