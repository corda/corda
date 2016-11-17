package net.corda.irs.protocols

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.TransientProperty
import net.corda.core.contracts.*
import net.corda.core.crypto.Party
import net.corda.core.crypto.PublicKeyTree
import net.corda.core.node.CordaPluginRegistry
import net.corda.core.node.NodeInfo
import net.corda.core.node.PluginServiceHub
import net.corda.core.node.services.ServiceType
import net.corda.core.protocols.ProtocolLogic
import net.corda.core.seconds
import net.corda.core.transactions.FilterFuns
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.trace
import net.corda.node.services.api.ServiceHubInternal
import net.corda.protocols.TwoPartyDealProtocol
import java.math.BigDecimal
import java.security.KeyPair
import java.security.PublicKey

object FixingProtocol {

    class Service(services: PluginServiceHub) {
        init {
            services.registerProtocolInitiator(Floater::class) { Fixer(it) }
        }
    }

    /**
     * One side of the fixing protocol for an interest rate swap, but could easily be generalised further.
     *
     * Do not infer too much from the name of the class.  This is just to indicate that it is the "side"
     * of the protocol that is run by the party with the fixed leg of swap deal, which is the basis for deciding
     * who does what in the protocol.
     */
    class Fixer(override val otherParty: Party,
                override val progressTracker: ProgressTracker = TwoPartyDealProtocol.Secondary.tracker()) : TwoPartyDealProtocol.Secondary<FixingSession>() {

        private lateinit var txState: TransactionState<*>
        private lateinit var deal: FixableDealState

        override fun validateHandshake(handshake: TwoPartyDealProtocol.Handshake<FixingSession>): TwoPartyDealProtocol.Handshake<FixingSession> {
            logger.trace { "Got fixing request for: ${handshake.payload}" }

            txState = serviceHub.loadState(handshake.payload.ref)
            deal = txState.data as FixableDealState

            // validate the party that initiated is the one on the deal and that the recipient corresponds with it.
            // TODO: this is in no way secure and will be replaced by general session initiation logic in the future
            val myName = serviceHub.myInfo.legalIdentity.name
            // Also check we are one of the parties
            deal.parties.filter { it.name == myName }.single()

            return handshake
        }

        @Suspendable
        override fun assembleSharedTX(handshake: TwoPartyDealProtocol.Handshake<FixingSession>): Pair<TransactionBuilder, List<PublicKeyTree>> {
            @Suppress("UNCHECKED_CAST")
            val fixOf = deal.nextFixingOf()!!

            // TODO Do we need/want to substitute in new public keys for the Parties?
            val myName = serviceHub.myInfo.legalIdentity.name
            val myOldParty = deal.parties.single { it.name == myName }

            val newDeal = deal

            val ptx = TransactionType.General.Builder(txState.notary)

            val oracle = serviceHub.networkMapCache.get(handshake.payload.oracleType).first()
            val oracleParty = oracle.serviceIdentities(handshake.payload.oracleType).first()

            // TODO Could it be solved in better way, move filtering here not in RatesFixProtocol?
            fun filterCommands(c: Command) = oracleParty.owningKey in c.signers && c.value is Fix
            val filterFuns = FilterFuns(filterCommands = ::filterCommands)
            val addFixing = object : RatesFixProtocol(ptx, filterFuns, oracleParty, fixOf, BigDecimal.ZERO, BigDecimal.ONE) {
                @Suspendable
                override fun beforeSigning(fix: Fix) {
                    newDeal.generateFix(ptx, StateAndRef(txState, handshake.payload.ref), fix)

                    // And add a request for timestamping: it may be that none of the contracts need this! But it can't hurt
                    // to have one.
                    ptx.setTime(serviceHub.clock.instant(), 30.seconds)
                }
            }
            subProtocol(addFixing)
            return Pair(ptx, arrayListOf(myOldParty.owningKey))
        }
    }

    /**
     * One side of the fixing protocol for an interest rate swap, but could easily be generalised furher.
     *
     * As per the [Fixer], do not infer too much from this class name in terms of business roles.  This
     * is just the "side" of the protocol run by the party with the floating leg as a way of deciding who
     * does what in the protocol.
     */
    class Floater(override val otherParty: Party,
                  override val payload: FixingSession,
                  override val progressTracker: ProgressTracker = TwoPartyDealProtocol.Primary.tracker()) : TwoPartyDealProtocol.Primary() {

        @Suppress("UNCHECKED_CAST")
        internal val dealToFix: StateAndRef<FixableDealState> by TransientProperty {
            val state = serviceHub.loadState(payload.ref) as TransactionState<FixableDealState>
            StateAndRef(state, payload.ref)
        }

        override val myKeyPair: KeyPair get() {
            val myName = serviceHub.myInfo.legalIdentity.name
            val myKeys = dealToFix.state.data.parties.filter { it.name == myName }.single().owningKey.keys
            return serviceHub.keyManagementService.toKeyPair(myKeys)
        }

        override val notaryNode: NodeInfo get() =
        serviceHub.networkMapCache.notaryNodes.filter { it.notaryIdentity == dealToFix.state.notary }.single()
    }


    /** Used to set up the session between [Floater] and [Fixer] */
    data class FixingSession(val ref: StateRef, val oracleType: ServiceType)

    /**
     * This protocol looks at the deal and decides whether to be the Fixer or Floater role in agreeing a fixing.
     *
     * It is kicked off as an activity on both participant nodes by the scheduler when it's time for a fixing.  If the
     * Fixer role is chosen, then that will be initiated by the [FixingSession] message sent from the other party and
     * handled by the [FixingSessionInitiationHandler].
     *
     * TODO: Replace [FixingSession] and [FixingSessionInitiationHandler] with generic session initiation logic once it exists.
     */
    class FixingRoleDecider(val ref: StateRef,
                            override val progressTracker: ProgressTracker = tracker()) : ProtocolLogic<Unit>() {

        companion object {
            class LOADING() : ProgressTracker.Step("Loading state to decide fixing role")

            fun tracker() = ProgressTracker(LOADING())
        }

        @Suspendable
        override fun call(): Unit {
            progressTracker.nextStep()
            val dealToFix = serviceHub.loadState(ref)
            // TODO: this is not the eventual mechanism for identifying the parties
            val fixableDeal = (dealToFix.data as FixableDealState)
            val sortedParties = fixableDeal.parties.sortedBy { it.name }
            if (sortedParties[0].name == serviceHub.myInfo.legalIdentity.name) {
                val fixing = FixingSession(ref, fixableDeal.oracleType)
                // Start the Floater which will then kick-off the Fixer
                subProtocol(Floater(sortedParties[1], fixing))
            }
        }
    }
}
