package net.corda.irs.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.contracts.Fix
import net.corda.contracts.FixableDealState
import net.corda.core.contracts.*
import net.corda.core.crypto.toBase58String
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.SchedulableFlow
import net.corda.core.identity.Party
import net.corda.core.node.NodeInfo
import net.corda.core.node.services.ServiceType
import net.corda.core.seconds
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.transient
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.trace
import net.corda.flows.TwoPartyDealFlow
import java.math.BigDecimal
import java.security.PublicKey

object FixingFlow {
    /**
     * One side of the fixing flow for an interest rate swap, but could easily be generalised further.
     *
     * Do not infer too much from the name of the class.  This is just to indicate that it is the "side"
     * of the flow that is run by the party with the fixed leg of swap deal, which is the basis for deciding
     * who does what in the flow.
     */
    @InitiatedBy(FixingRoleDecider::class)
    class Fixer(override val otherParty: Party) : TwoPartyDealFlow.Secondary<FixingSession>() {

        private lateinit var txState: TransactionState<*>
        private lateinit var deal: FixableDealState

        override fun validateHandshake(handshake: TwoPartyDealFlow.Handshake<FixingSession>): TwoPartyDealFlow.Handshake<FixingSession> {
            logger.trace { "Got fixing request for: ${handshake.payload}" }

            txState = serviceHub.loadState(handshake.payload.ref)
            deal = txState.data as FixableDealState

            // validate the party that initiated is the one on the deal and that the recipient corresponds with it.
            // TODO: this is in no way secure and will be replaced by general session initiation logic in the future
            // Also check we are one of the parties
            require(deal.participants.count { it.owningKey == serviceHub.myInfo.legalIdentity.owningKey } == 1)

            return handshake
        }

        @Suspendable
        override fun assembleSharedTX(handshake: TwoPartyDealFlow.Handshake<FixingSession>): Pair<TransactionBuilder, List<PublicKey>> {
            @Suppress("UNCHECKED_CAST")
            val fixOf = deal.nextFixingOf()!!

            // TODO Do we need/want to substitute in new public keys for the Parties?
            val myOldParty = deal.participants.single { it.owningKey == serviceHub.myInfo.legalIdentity.owningKey }

            val newDeal = deal

            val ptx = TransactionType.General.Builder(txState.notary)

            val oracle = serviceHub.networkMapCache.getNodesWithService(handshake.payload.oracleType).first()
            val oracleParty = oracle.serviceIdentities(handshake.payload.oracleType).first()

            // DOCSTART 1
            val addFixing = object : RatesFixFlow(ptx, oracleParty, fixOf, BigDecimal.ZERO, BigDecimal.ONE) {
                @Suspendable
                override fun beforeSigning(fix: Fix) {
                    newDeal.generateFix(ptx, StateAndRef(txState, handshake.payload.ref), fix)

                    // We set the transaction's time-window: it may be that none of the contracts need this!
                    // But it can't hurt to have one.
                    ptx.setTimeWindow(serviceHub.clock.instant(), 30.seconds)
                }

                @Suspendable
                override fun filtering(elem: Any): Boolean {
                    return when (elem) {
                        is Command -> oracleParty.owningKey in elem.signers && elem.value is Fix
                        else -> false
                    }
                }
            }
            subFlow(addFixing)
            // DOCEND 1
            return Pair(ptx, arrayListOf(myOldParty.owningKey))
        }
    }

    /**
     * One side of the fixing flow for an interest rate swap, but could easily be generalised furher.
     *
     * As per the [Fixer], do not infer too much from this class name in terms of business roles.  This
     * is just the "side" of the flow run by the party with the floating leg as a way of deciding who
     * does what in the flow.
     */
    class Floater(override val otherParty: Party,
                  override val payload: FixingSession,
                  override val progressTracker: ProgressTracker = TwoPartyDealFlow.Primary.tracker()) : TwoPartyDealFlow.Primary() {

        @Suppress("UNCHECKED_CAST")
        internal val dealToFix: StateAndRef<FixableDealState> by transient {
            val state = serviceHub.loadState(payload.ref) as TransactionState<FixableDealState>
            StateAndRef(state, payload.ref)
        }

        override val myKey: PublicKey get() {
            dealToFix.state.data.participants.single { it.owningKey == serviceHub.myInfo.legalIdentity.owningKey }
            return serviceHub.legalIdentityKey
        }

        override val notaryNode: NodeInfo get() {
            return serviceHub.networkMapCache.notaryNodes.single { it.notaryIdentity == dealToFix.state.notary }
        }

        @Suspendable override fun checkProposal(stx: SignedTransaction) = requireThat {
            // Add some constraints here.
        }
    }


    /** Used to set up the session between [Floater] and [Fixer] */
    @CordaSerializable
    data class FixingSession(val ref: StateRef, val oracleType: ServiceType)

    /**
     * This flow looks at the deal and decides whether to be the Fixer or Floater role in agreeing a fixing.
     *
     * It is kicked off as an activity on both participant nodes by the scheduler when it's time for a fixing.  If the
     * Fixer role is chosen, then that will be initiated by the [FixingSession] message sent from the other party.
     */
    @InitiatingFlow
    @SchedulableFlow
    class FixingRoleDecider(val ref: StateRef, override val progressTracker: ProgressTracker) : FlowLogic<Unit>() {
        @Suppress("unused") // Used via reflection.
        constructor(ref: StateRef) : this(ref, tracker())

        companion object {
            class LOADING : ProgressTracker.Step("Loading state to decide fixing role")

            fun tracker() = ProgressTracker(LOADING())
        }

        @Suspendable
        override fun call(): Unit {
            progressTracker.nextStep()
            val dealToFix = serviceHub.loadState(ref)
            val fixableDeal = (dealToFix.data as FixableDealState)
            val parties = fixableDeal.participants.sortedBy { it.owningKey.toBase58String() }
            val myKey = serviceHub.myInfo.legalIdentity.owningKey
            if (parties[0].owningKey == myKey) {
                val fixing = FixingSession(ref, fixableDeal.oracleType)
                val counterparty = serviceHub.identityService.partyFromAnonymous(parties[1]) ?: throw IllegalStateException("Cannot resolve floater party")
                // Start the Floater which will then kick-off the Fixer
                subFlow(Floater(counterparty, fixing))
            }
        }
    }
}
