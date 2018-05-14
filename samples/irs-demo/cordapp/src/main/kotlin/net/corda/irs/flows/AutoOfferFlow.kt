/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.irs.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.identity.excludeHostNode
import net.corda.core.identity.groupAbstractPartyByWellKnownParty
import net.corda.core.node.StatesToRecord
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ProgressTracker
import net.corda.finance.contracts.DealState
import net.corda.finance.flows.TwoPartyDealFlow
import net.corda.finance.flows.TwoPartyDealFlow.Acceptor
import net.corda.finance.flows.TwoPartyDealFlow.AutoOffer
import net.corda.finance.flows.TwoPartyDealFlow.Instigator

/**
 * This whole class is really part of a demo just to initiate the agreement of a deal with a simple
 * API call from a single party without bi-directional access to the database of offers etc.
 *
 * In the "real world", we'd probably have the offers sitting in the platform prior to the agreement step
 * or the flow would have to reach out to external systems (or users) to verify the deals.
 */
object AutoOfferFlow {
    @InitiatingFlow
    @StartableByRPC
    class Requester(val dealToBeOffered: DealState) : FlowLogic<SignedTransaction>() {
        companion object {
            object RECEIVED : ProgressTracker.Step("Received API call")
            object DEALING : ProgressTracker.Step("Starting the deal flow") {
                override fun childProgressTracker(): ProgressTracker = TwoPartyDealFlow.Primary.tracker()
            }

            // We vend a progress tracker that already knows there's going to be a TwoPartyTradingFlow involved at some
            // point: by setting up the tracker in advance, the user can see what's coming in more detail, instead of being
            // surprised when it appears as a new set of tasks below the current one.
            fun tracker() = ProgressTracker(RECEIVED, DEALING)
        }

        override val progressTracker = tracker()

        init {
            progressTracker.currentStep = RECEIVED
        }

        @Suspendable
        override fun call(): SignedTransaction {
            require(serviceHub.networkMapCache.notaryIdentities.isNotEmpty()) { "No notary nodes registered" }
            val notary = serviceHub.networkMapCache.notaryIdentities.first() // TODO We should pass the notary as a parameter to the flow, not leave it to random choice.
            // need to pick which ever party is not us
            val otherParty = excludeHostNode(serviceHub, groupAbstractPartyByWellKnownParty(serviceHub, dealToBeOffered.participants)).keys.single()
            progressTracker.currentStep = DEALING
            val session = initiateFlow(otherParty)
            val instigator = Instigator(
                    session,
                    AutoOffer(notary, dealToBeOffered),
                    progressTracker.getChildProgressTracker(DEALING)!!
            )
            return subFlow(instigator)
        }
    }

    // DOCSTART 1
    @InitiatedBy(Requester::class)
    class AutoOfferAcceptor(otherSideSession: FlowSession) : Acceptor(otherSideSession) {
        @Suspendable
        override fun call(): SignedTransaction {
            val finalTx = super.call()
            // Our transaction is now committed to the ledger, so report it to our regulator. We use a custom flow
            // that wraps SendTransactionFlow to allow the receiver to customise how ReceiveTransactionFlow is run,
            // and because in a real life app you'd probably have more complex logic here e.g. describing why the report
            // was filed, checking that the reportee is a regulated entity and not some random node from the wrong
            // country and so on.
            val regulator = serviceHub.identityService.partiesFromName("Regulator", true).single()
            subFlow(ReportToRegulatorFlow(regulator, finalTx))
            return finalTx
        }
    }

    @InitiatingFlow
    class ReportToRegulatorFlow(private val regulator: Party, private val finalTx: SignedTransaction) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            val session = initiateFlow(regulator)
            subFlow(SendTransactionFlow(session, finalTx))
        }
    }

    @InitiatedBy(ReportToRegulatorFlow::class)
    class ReceiveRegulatoryReportFlow(private val otherSideSession: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            // Start the matching side of SendTransactionFlow above, but tell it to record all visible states even
            // though they (as far as the node can tell) are nothing to do with us.
            subFlow(ReceiveTransactionFlow(otherSideSession, true, StatesToRecord.ALL_VISIBLE))
        }
    }
    // DOCEND 1
}
