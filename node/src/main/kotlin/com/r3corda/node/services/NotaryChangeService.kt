package com.r3corda.node.services

import com.r3corda.core.messaging.MessagingService
import com.r3corda.core.messaging.SingleMessageRecipient
import com.r3corda.node.services.api.AbstractNodeService
import com.r3corda.node.services.statemachine.StateMachineManager
import protocols.NotaryChangeProtocol

/**
 * A service that monitors the network for requests for changing the notary of a state,
 * and immediately runs the [NotaryChangeProtocol] if the auto-accept criteria are met.
 */
class NotaryChangeService(net: MessagingService, val smm: StateMachineManager) : AbstractNodeService(net) {
    init {
        addMessageHandler(NotaryChangeProtocol.TOPIC_INITIATE,
                { req: NotaryChangeProtocol.Handshake -> handleChangeNotaryRequest(req) }
        )
    }

    private fun handleChangeNotaryRequest(req: NotaryChangeProtocol.Handshake): Boolean {
        val proposal = req.payload
        val autoAccept = checkProposal(proposal)

        if (autoAccept) {
            val protocol = NotaryChangeProtocol.Acceptor(
                    req.replyTo as SingleMessageRecipient,
                    proposal.sessionIdForReceive,
                    proposal.sessionIdForSend)
            smm.add(NotaryChangeProtocol.TOPIC_CHANGE, protocol)
        }
        return autoAccept
    }

    /**
     * Check the notary change for a state proposal and decide whether to allow the change and initiate the protocol
     * or deny the change.
     *
     * For example, if the proposed new notary has the same behaviour (e.g. both are non-validating)
     * and is also in a geographically convenient location we can just automatically approve the change.
     * TODO: In more difficult cases this should call for human attention to manually verify and approve the proposal
     */
    private fun checkProposal(proposal: NotaryChangeProtocol.Proposal): Boolean {
        val newNotary = proposal.newNotary
        val isNotary = smm.serviceHub.networkMapCache.notaryNodes.any { it.identity == newNotary }
        require(isNotary) { "The proposed node $newNotary does not run a Notary service " }

        // An example requirement
        val blacklist = listOf("Evil Notary")
        require(!blacklist.contains(newNotary.name))

        return true
    }
}
