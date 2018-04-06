package net.corda.irs.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.*
import net.corda.core.crypto.TransactionSignature
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.internal.uncheckedCast
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.*
import net.corda.finance.contracts.Fix
import net.corda.finance.contracts.FixableDealState
import net.corda.finance.flows.TwoPartyDealFlow
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
    class Fixer(override val otherSideSession: FlowSession) : TwoPartyDealFlow.Secondary<FixingSession>() {

        private lateinit var txState: TransactionState<*>
        private lateinit var deal: FixableDealState

        override fun validateHandshake(handshake: TwoPartyDealFlow.Handshake<FixingSession>): TwoPartyDealFlow.Handshake<FixingSession> {
            logger.trace { "Got fixing request for: ${handshake.payload}" }

            txState = serviceHub.loadState(handshake.payload.ref)
            deal = txState.data as FixableDealState

            // validate the party that initiated is the one on the deal and that the recipient corresponds with it.
            // TODO: this is in no way secure and will be replaced by general session initiation logic in the future
            // Also check we are one of the parties
            require(deal.participants.count { it.owningKey == ourIdentity.owningKey } == 1)

            return handshake
        }

        @Suspendable
        override fun assembleSharedTX(handshake: TwoPartyDealFlow.Handshake<FixingSession>): Triple<TransactionBuilder, List<PublicKey>, List<TransactionSignature>> {
            val fixOf = deal.nextFixingOf()!!

            // TODO Do we need/want to substitute in new public keys for the Parties?
            val myOldParty = deal.participants.single { it.owningKey == ourIdentity.owningKey }

            val newDeal = deal

            val ptx = TransactionBuilder(txState.notary)

            // DOCSTART 1
            val addFixing = object : RatesFixFlow(ptx, handshake.payload.oracle, fixOf, BigDecimal.ZERO, BigDecimal.ONE) {
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
                        // Only expose Fix commands in which the oracle is on the list of requested signers
                        // to the oracle node, to avoid leaking privacy
                        is Command<*> -> handshake.payload.oracle.owningKey in elem.signers && elem.value is Fix
                        else -> false
                    }
                }
            }
            val sig = subFlow(addFixing)
            // DOCEND 1
            return Triple(ptx, arrayListOf(myOldParty.owningKey), listOf(sig))
        }
    }

    /**
     * One side of the fixing flow for an interest rate swap, but could easily be generalised further.
     *
     * As per the [Fixer], do not infer too much from this class name in terms of business roles.  This
     * is just the "side" of the flow run by the party with the floating leg as a way of deciding who
     * does what in the flow.
     */
    class Floater(override val otherSideSession: FlowSession,
                  override val payload: FixingSession,
                  override val progressTracker: ProgressTracker = TwoPartyDealFlow.Primary.tracker()) : TwoPartyDealFlow.Primary() {
        private val dealToFix: StateAndRef<FixableDealState> by transient {
            val state: TransactionState<FixableDealState> = uncheckedCast(serviceHub.loadState(payload.ref))
            StateAndRef(state, payload.ref)
        }

        override val notaryParty: Party get() = dealToFix.state.notary

        @Suspendable override fun checkProposal(stx: SignedTransaction) = requireThat {
            // Add some constraints here.
        }
    }


    /** Used to set up the session between [Floater] and [Fixer] */
    @CordaSerializable
    data class FixingSession(val ref: StateRef, val oracle: Party)

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
        override fun call() {
            progressTracker.nextStep()
            val dealToFix = serviceHub.loadState(ref)
            val fixableDeal = (dealToFix.data as FixableDealState)
            val parties = fixableDeal.participants.sortedBy { it.owningKey.toBase58String() }
            val myKey = ourIdentity.owningKey
            if (parties[0].owningKey == myKey) {
                val fixing = FixingSession(ref, fixableDeal.oracle)
                val counterparty = serviceHub.identityService.wellKnownPartyFromAnonymous(parties[1]) ?: throw IllegalStateException("Cannot resolve floater party")
                // Start the Floater which will then kick-off the Fixer
                val session = initiateFlow(counterparty)
                subFlow(Floater(session, fixing))
            }
        }
    }
}
