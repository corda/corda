package net.corda.notarydemo

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Amount
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.Party
import net.corda.core.node.PluginServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.loggerFor
import net.corda.core.utilities.unwrap
import net.corda.flows.*
import java.util.*

// START 1
@CordaService
class CustomIssuerService(val services: PluginServiceHub) : SingletonSerializeAsToken() {

    private companion object {
        val log = loggerFor<CustomIssuerService>()
    }

    init {
        log.info("Service initialising ...")

        log.info("Service initialisation completed successfully ...")
    }

    fun rebalanceCurrencyReserves() {
        // search cash contract state table for all currencies and balances
        val nativeQuery = """
            select
                sum(cashschema1_.pennies) as col_0_0_,
                cashschema1_.ccy_code as col_1_0_
            from
                vault_states vaultschem0_ cross
            join
                contract_cash_states cashschema1_
            where
                vaultschem0_.output_index=cashschema1_.output_index
                and vaultschem0_.transaction_id=cashschema1_.transaction_id
                and vaultschem0_.state_status=?
            group by
                cashschema1_.ccy_code limit ?
            order by
                sum(cashschema1_.pennies)
        """
        val session = services.jdbcSession()
        val prepStatement = session.prepareStatement(nativeQuery)
        val rs = prepStatement.executeQuery()
        while (rs.next()) {
            println("${rs.getString(2)} : ${rs.getInt(1)}")
        }
    }

}
// END 1

// START 2
class CustomIssuer(val otherParty: Party, val service: CustomIssuerService) : FlowLogic<SignedTransaction>() {
    companion object {
        object AWAITING_REQUEST : ProgressTracker.Step("Awaiting issuance request")
        object ISSUING : ProgressTracker.Step("Self issuing asset")
        object TRANSFERRING : ProgressTracker.Step("Transferring asset to issuance requester")
        object SENDING_CONFIRM : ProgressTracker.Step("Confirming asset issuance to requester")

        fun tracker() = ProgressTracker(AWAITING_REQUEST, ISSUING, TRANSFERRING, SENDING_CONFIRM)
    }

    override val progressTracker: ProgressTracker = tracker()

    @Suspendable
    @Throws(CashException::class)
    override fun call(): SignedTransaction {
        progressTracker.currentStep = AWAITING_REQUEST
        val issueRequest = receive<IssuerFlow.IssuanceRequestState>(otherParty).unwrap {
            it
        }
        // TODO: parse request to determine Asset to issue

        val reserves = service.rebalanceCurrencyReserves()

        val txn = issueCashTo(issueRequest.amount, issueRequest.issueToParty, issueRequest.issuerPartyRef, issueRequest.anonymous)
        progressTracker.currentStep = SENDING_CONFIRM
        send(otherParty, txn)
        return txn.stx
    }

    @Suspendable
    private fun issueCashTo(amount: Amount<Currency>,
                            issueTo: Party,
                            issuerPartyRef: OpaqueBytes,
                            anonymous: Boolean): AbstractCashFlow.Result {
        // TODO: pass notary in as request parameter
        val notaryParty = serviceHub.networkMapCache.notaryNodes[0].notaryIdentity
        // invoke Cash subflow to issue Asset
        progressTracker.currentStep = ISSUING
        val issueRecipient = serviceHub.myInfo.legalIdentity
        val issueCashFlow = CashIssueFlow(amount, issuerPartyRef, issueRecipient, notaryParty, anonymous = false)
        val issueTx = subFlow(issueCashFlow)
        // NOTE: issueCashFlow performs a Broadcast (which stores a local copy of the txn to the ledger)
        // short-circuit when issuing to self
        if (issueTo == serviceHub.myInfo.legalIdentity)
            return issueTx
        // now invoke Cash subflow to Move issued assetType to issue requester
        progressTracker.currentStep = TRANSFERRING
        val moveCashFlow = CashPaymentFlow(amount, issueTo, anonymous)
        val moveTx = subFlow(moveCashFlow)
        // NOTE: CashFlow PayCash calls FinalityFlow which performs a Broadcast (which stores a local copy of the txn to the ledger)
        return moveTx
    }
}
// END 2
