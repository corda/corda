package demos.protocols

import co.paralleluniverse.fibers.Suspendable
import co.paralleluniverse.strands.Strand
import contracts.DealState
import contracts.InterestRateSwap
import core.StateAndRef
import core.node.Node
import core.node.NodeInfo
import core.node.services.linearHeadsOfType
import core.protocols.ProtocolLogic
import core.random63BitValue
import core.serialization.deserialize
import core.testing.MockNetworkMapCache
import core.utilities.ANSIProgressRenderer
import core.utilities.ProgressTracker
import demos.DemoClock
import protocols.TwoPartyDealProtocol
import java.time.LocalDate

/**
 * This is a very temporary, demo-oriented way of initiating processing of temporal events and is not
 * intended as the way things will necessarily be done longer term
 */
object UpdateBusinessDayProtocol {

    val TOPIC = "businessday.topic"

    class Updater(val date: LocalDate, val sessionID: Long,
                  override val progressTracker: ProgressTracker = Updater.tracker()) : ProtocolLogic<Boolean>() {

        companion object {
            object FETCHING : ProgressTracker.Step("Fetching deals")
            object ITERATING_DEALS : ProgressTracker.Step("Interating over deals")
            object ITERATING_FIXINGS : ProgressTracker.Step("Iterating over fixings")
            object FIXING : ProgressTracker.Step("Fixing deal")

            fun tracker() = ProgressTracker(FETCHING, ITERATING_DEALS, ITERATING_FIXINGS, FIXING)
        }

        @Suspendable
        override fun call(): Boolean {
            // Get deals
            progressTracker.currentStep = FETCHING
            val dealStateRefs = serviceHub.walletService.linearHeadsOfType<DealState>()
            val otherPartyToDeals = dealStateRefs.values.groupBy { otherParty(it.state) }

            // TODO we need to process these in parallel to stop there being an ordering problem across more than two nodes
            val sortedParties = otherPartyToDeals.keys.sortedBy { it.identity.name }
            for (party in sortedParties) {
                val sortedDeals = otherPartyToDeals[party]!!.sortedBy { it.state.ref }
                for (deal in sortedDeals) {
                    progressTracker.currentStep = ITERATING_DEALS
                    processDeal(party, deal, date, sessionID)
                }
            }
            return false
        }

        // This assumes we definitely have one key or the other
        fun otherParty(deal: DealState): NodeInfo {
            val ourKeys = serviceHub.keyManagementService.keys.keys
            return serviceHub.networkMapCache.nodeForPartyName(deal.parties.single { !ourKeys.contains(it.owningKey) }.name)!!
        }

        // TODO we should make this more object oriented when we can ask a state for it's contract
        @Suspendable
        fun processDeal(party: NodeInfo, deal: StateAndRef<DealState>, date: LocalDate, sessionID: Long) {
            val s = deal.state
            when (s) {
                is InterestRateSwap.State -> processInterestRateSwap(party, StateAndRef(s, deal.ref), date, sessionID)
            }
        }

        // TODO and this would move to the InterestRateSwap and cope with permutations of Fixed/Floating and Floating/Floating etc
        @Suspendable
        fun processInterestRateSwap(party: NodeInfo, deal: StateAndRef<InterestRateSwap.State>, date: LocalDate, sessionID: Long) {
            var dealStateAndRef: StateAndRef<InterestRateSwap.State>? = deal
            var nextFixingDate = deal.state.calculation.nextFixingDate()
            while (nextFixingDate != null && !nextFixingDate.isAfter(date)) {
                progressTracker.currentStep = ITERATING_FIXINGS
                /*
                 * Note that this choice of fixed versus floating leg is simply to assign roles in
                 * the fixing protocol and doesn't infer roles or responsibilities in a business sense.
                 * One of the parties needs to take the lead in the coordination and this is a reliable deterministic way
                 * to do it.
                 */
                if (party.identity.name == deal.state.fixedLeg.fixedRatePayer.name) {
                    dealStateAndRef = nextFixingFloatingLeg(dealStateAndRef!!, party, sessionID)
                } else {
                    dealStateAndRef = nextFixingFixedLeg(dealStateAndRef!!, party, sessionID)
                }
                nextFixingDate = dealStateAndRef?.state?.calculation?.nextFixingDate()
            }
        }

        @Suspendable
        private fun nextFixingFloatingLeg(dealStateAndRef: StateAndRef<InterestRateSwap.State>, party: NodeInfo, sessionID: Long): StateAndRef<InterestRateSwap.State>? {
            progressTracker.childrenFor[FIXING] = TwoPartyDealProtocol.Primary.tracker()
            progressTracker.currentStep = FIXING

            val participant = TwoPartyDealProtocol.Floater(party.address, sessionID, serviceHub.networkMapCache.timestampingNodes[0], dealStateAndRef, serviceHub.keyManagementService.freshKey(), sessionID, progressTracker.childrenFor[FIXING]!!)
            Strand.sleep(100)
            val result = subProtocol(participant)
            return result.tx.outRef(0)
        }

        @Suspendable
        private fun nextFixingFixedLeg(dealStateAndRef: StateAndRef<InterestRateSwap.State>, party: NodeInfo, sessionID: Long): StateAndRef<InterestRateSwap.State>? {
            progressTracker.childrenFor[FIXING] = TwoPartyDealProtocol.Secondary.tracker()
            progressTracker.currentStep = FIXING

            val participant = TwoPartyDealProtocol.Fixer(party.address, serviceHub.networkMapCache.timestampingNodes[0].identity, dealStateAndRef, sessionID, progressTracker.childrenFor[FIXING]!!)
            val result = subProtocol(participant)
            return result.tx.outRef(0)
        }
    }

    data class UpdateBusinessDayMessage(val date: LocalDate, val sessionID: Long)

    object Handler {

        fun register(node: Node) {
            node.net.addMessageHandler("${TOPIC}.0") { msg, registration ->
                // Just to validate we got the message
                val updateBusinessDayMessage = msg.data.deserialize<UpdateBusinessDayMessage>()
                if ((node.services.clock as DemoClock).updateDate(updateBusinessDayMessage.date)) {
                    val participant = Updater(updateBusinessDayMessage.date, updateBusinessDayMessage.sessionID)
                    ANSIProgressRenderer.progressTracker = participant.progressTracker
                    node.smm.add("update.business.day", participant)
                }
            }
        }
    }

    class Broadcast(val date: LocalDate,
                    override val progressTracker: ProgressTracker = Broadcast.tracker()) : ProtocolLogic<Boolean>() {

        companion object {
            object NOTIFYING : ProgressTracker.Step("Notifying peer")
            object LOCAL : ProgressTracker.Step("Updating locally")

            fun tracker() = ProgressTracker(NOTIFYING, LOCAL).apply {
                childrenFor[LOCAL] = Updater.tracker()
            }
        }

        @Suspendable
        override fun call(): Boolean {
            val message = UpdateBusinessDayMessage(date, random63BitValue())

            for (recipient in serviceHub.networkMapCache.partyNodes) {
                progressTracker.currentStep = NOTIFYING
                doNextRecipient(recipient, message)
            }
            if ((serviceHub.clock as DemoClock).updateDate(message.date)) {
                progressTracker.currentStep = LOCAL
                subProtocol(Updater(message.date, message.sessionID, progressTracker.childrenFor[LOCAL]!!))
            }
            return true
        }

        @Suspendable
        private fun doNextRecipient(recipient: NodeInfo, message: UpdateBusinessDayMessage) {
            if (recipient.address is MockNetworkMapCache.MockAddress) {
                // Ignore
            } else {
                // TODO: messaging ourselves seems to trigger a bug for the time being and we continuously receive messages
                if (recipient.identity != serviceHub.storageService.myLegalIdentity) {
                    send(TOPIC, recipient.address, 0, message)
                }
            }
        }
    }

}
