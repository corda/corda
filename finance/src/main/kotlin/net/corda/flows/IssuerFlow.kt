package net.corda.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.ThreadBox
import net.corda.core.contracts.*
import net.corda.core.crypto.Party
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowLogic
import net.corda.core.node.NodeInfo
import net.corda.core.node.PluginServiceHub
import net.corda.core.serialization.OpaqueBytes
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ProgressTracker
import net.corda.flows.CashCommand
import net.corda.flows.CashFlow
import net.corda.flows.CashFlowResult
import java.util.*

/**
 *  This flow enables a client to request issuance of some [FungibleAsset] from a
 *  server acting as an issuer (see [Issued]) of FungibleAssets.
 *
 *  It is not intended for production usage, but rather for experimentation and testing purposes where it may be
 *  useful for creation of fake assets.
 */
object IssuerFlow {
    data class IssuanceRequestState(val amount: Amount<Currency>, val issueToParty: Party, val issuerPartyRef: OpaqueBytes)

    /**
     * IssuanceRequester should be used by a client to ask a remote note to issue some [FungibleAsset] with the given details.
     * Returns the transaction created by the Issuer to move the cash to the Requester.
     */
    class IssuanceRequester(val amount: Amount<Currency>, val issueToParty: Party, val issueToPartyRef: OpaqueBytes,
                            val issuerBankParty: Party): FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val issueRequest = IssuanceRequestState(amount, issueToParty, issueToPartyRef)
            try {
                return sendAndReceive<SignedTransaction>(issuerBankParty, issueRequest).unwrap { it }
            // catch and report exception before throwing back to caller
            } catch (e: Exception) {
                logger.error("IssuanceRequesterException: request failed: [${issueRequest}]")
                // TODO: awaiting exception handling strategy (what action should be taken here?)
                throw e
            }
        }
    }

    /**
     * Issuer refers to a Node acting as a Bank Issuer of [FungibleAsset], and processes requests from a [IssuanceRequester] client.
     * Returns the generated transaction representing the transfer of the [Issued] [FungibleAsset] to the issue requester.
     */
    class Issuer(val otherParty: Party): FlowLogic<SignedTransaction>() {
        override val progressTracker: ProgressTracker = tracker()
        companion object {
            object AWAITING_REQUEST : ProgressTracker.Step("Awaiting issuance request")
            object ISSUING : ProgressTracker.Step("Self issuing asset")
            object TRANSFERRING : ProgressTracker.Step("Transferring asset to issuance requester")
            object SENDING_CONFIRM : ProgressTracker.Step("Confirming asset issuance to requester")
            fun tracker() = ProgressTracker(AWAITING_REQUEST, ISSUING, TRANSFERRING, SENDING_CONFIRM)
        }

        @Suspendable
        override fun call(): SignedTransaction {
            progressTracker.currentStep = AWAITING_REQUEST
            val issueRequest = receive<IssuanceRequestState>(otherParty).unwrap { it }
            // validate request inputs (for example, lets restrict the types of currency that can be issued)
            require(listOf<Currency>(USD, GBP, EUR, CHF).contains(issueRequest.amount.token)) {
                logger.error("Currency must be one of USD, GBP, EUR, CHF")
            }
            // TODO: parse request to determine Asset to issue
            val txn = issueCashTo(issueRequest.amount, issueRequest.issueToParty, issueRequest.issuerPartyRef)
            progressTracker.currentStep = SENDING_CONFIRM
            send(otherParty, txn)
            return txn
        }

        // TODO: resolve race conditions caused by the 2 separate Cashflow commands (Issue and Pay) not reusing the same
        //       state references (thus causing Notarisation double spend exceptions).
        @Suspendable
        private fun issueCashTo(amount: Amount<Currency>,
                                issueTo: Party, issuerPartyRef: OpaqueBytes): SignedTransaction {
            // TODO: pass notary in as request parameter
            val notaryParty = serviceHub.networkMapCache.notaryNodes[0].notaryIdentity
            // invoke Cash subflow to issue Asset
            progressTracker.currentStep = ISSUING
            val bankOfCordaParty = serviceHub.myInfo.legalIdentity
            try {
                val issueCashFlow = CashFlow(CashCommand.IssueCash(amount, issuerPartyRef, bankOfCordaParty, notaryParty))
                val resultIssue = subFlow(issueCashFlow)
                // NOTE: issueCashFlow performs a Broadcast (which stores a local copy of the txn to the ledger)
                if (resultIssue is CashFlowResult.Failed) {
                    logger.error("Problem issuing cash: ${resultIssue.message}")
                    throw Exception(resultIssue.message)
                }
                // short-circuit when issuing to self
                if (issueTo.equals(serviceHub.myInfo.legalIdentity))
                    return (resultIssue as CashFlowResult.Success).transaction!!
                // now invoke Cash subflow to Move issued assetType to issue requester
                progressTracker.currentStep = TRANSFERRING
                val moveCashFlow = CashFlow(CashCommand.PayCash(amount.issuedBy(bankOfCordaParty.ref(issuerPartyRef)), issueTo))
                val resultMove = subFlow(moveCashFlow)
                // NOTE: CashFlow PayCash calls FinalityFlow which performs a Broadcast (which stores a local copy of the txn to the ledger)
                if (resultMove is CashFlowResult.Failed) {
                    logger.error("Problem transferring cash: ${resultMove.message}")
                    throw Exception(resultMove.message)
                }
                val txn = (resultMove as CashFlowResult.Success).transaction
                txn?.let {
                    return txn
                }
                // NOTE: CashFlowResult.Success should always return a signedTransaction
                throw Exception("Missing CashFlow transaction [${(resultMove)}]")
            }
            // catch and report exception before throwing back to caller flow
            catch (e: Exception) {
                logger.error("Issuer Exception: failed for amount ${amount} issuedTo ${issueTo} with issuerPartyRef ${issuerPartyRef}")
                // TODO: awaiting exception handling strategy (what action should be taken here?)
                throw e
            }
        }

        class Service(services: PluginServiceHub) {
            init {
                services.registerFlowInitiator(IssuanceRequester::class) {
                    Issuer(it)
                }
            }
        }
    }
}