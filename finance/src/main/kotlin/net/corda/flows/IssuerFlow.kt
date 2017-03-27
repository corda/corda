package net.corda.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.*
import net.corda.core.crypto.Party
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.node.PluginServiceHub
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.OpaqueBytes
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap
import java.util.*

/**
 *  This flow enables a client to request issuance of some [FungibleAsset] from a
 *  server acting as an issuer (see [Issued]) of FungibleAssets.
 *
 *  It is not intended for production usage, but rather for experimentation and testing purposes where it may be
 *  useful for creation of fake assets.
 */
object IssuerFlow {
    @CordaSerializable
    data class IssuanceRequestState(val amount: Amount<Currency>, val issueToParty: Party, val issuerPartyRef: OpaqueBytes)

    /**
     * IssuanceRequester should be used by a client to ask a remote node to issue some [FungibleAsset] with the given details.
     * Returns the transaction created by the Issuer to move the cash to the Requester.
     */
    class IssuanceRequester(val amount: Amount<Currency>, val issueToParty: Party, val issueToPartyRef: OpaqueBytes,
                            val issuerBankParty: Party): FlowLogic<SignedTransaction>() {
        @Suspendable
        @Throws(CashException::class)
        override fun call(): SignedTransaction {
            val issueRequest = IssuanceRequestState(amount, issueToParty, issueToPartyRef)
            return sendAndReceive<SignedTransaction>(issuerBankParty, issueRequest).unwrap { it }
        }
    }

    /**
     * Issuer refers to a Node acting as a Bank Issuer of [FungibleAsset], and processes requests from a [IssuanceRequester] client.
     * Returns the generated transaction representing the transfer of the [Issued] [FungibleAsset] to the issue requester.
     */
    class Issuer(val otherParty: Party): FlowLogic<SignedTransaction>() {
        companion object {
            object AWAITING_REQUEST : ProgressTracker.Step("Awaiting issuance request")
            object ISSUING : ProgressTracker.Step("Self issuing asset")
            object TRANSFERRING : ProgressTracker.Step("Transferring asset to issuance requester")
            object SENDING_CONFIRM : ProgressTracker.Step("Confirming asset issuance to requester")
            fun tracker() = ProgressTracker(AWAITING_REQUEST, ISSUING, TRANSFERRING, SENDING_CONFIRM)
            private val VALID_CURRENCIES = listOf(USD, GBP, EUR, CHF)
        }

        override val progressTracker: ProgressTracker = tracker()

        @Suspendable
        @Throws(CashException::class)
        override fun call(): SignedTransaction {
            progressTracker.currentStep = AWAITING_REQUEST
            val issueRequest = receive<IssuanceRequestState>(otherParty).unwrap {
                // validate request inputs (for example, lets restrict the types of currency that can be issued)
                if (it.amount.token !in VALID_CURRENCIES) throw FlowException("Currency must be one of $VALID_CURRENCIES")
                it
            }
            // TODO: parse request to determine Asset to issue
            val txn = issueCashTo(issueRequest.amount, issueRequest.issueToParty, issueRequest.issuerPartyRef)
            progressTracker.currentStep = SENDING_CONFIRM
            send(otherParty, txn)
            return txn
        }

        @Suspendable
        private fun issueCashTo(amount: Amount<Currency>,
                                issueTo: Party,
                                issuerPartyRef: OpaqueBytes): SignedTransaction {
            // TODO: pass notary in as request parameter
            val notaryParty = serviceHub.networkMapCache.notaryNodes[0].notaryIdentity
            // invoke Cash subflow to issue Asset
            progressTracker.currentStep = ISSUING
            val bankOfCordaParty = serviceHub.myInfo.legalIdentity
            val issueCashFlow = CashIssueFlow(amount, issuerPartyRef, bankOfCordaParty, notaryParty)
            val issueTx = subFlow(issueCashFlow)
            // NOTE: issueCashFlow performs a Broadcast (which stores a local copy of the txn to the ledger)
            // short-circuit when issuing to self
            if (issueTo == serviceHub.myInfo.legalIdentity)
                return issueTx
            // now invoke Cash subflow to Move issued assetType to issue requester
            progressTracker.currentStep = TRANSFERRING
            val moveCashFlow = CashPaymentFlow(amount, issueTo)
            val moveTx = subFlow(moveCashFlow)
            // NOTE: CashFlow PayCash calls FinalityFlow which performs a Broadcast (which stores a local copy of the txn to the ledger)
            return moveTx
        }

        class Service(services: PluginServiceHub) {
            init {
                services.registerFlowInitiator(IssuanceRequester::class.java, ::Issuer)
            }
        }
    }
}