package net.corda.docs

import co.paralleluniverse.fibers.Suspendable
import net.corda.contracts.asset.Cash
import net.corda.core.contracts.Amount
import net.corda.core.contracts.Issued
import net.corda.core.contracts.StateAndRef
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.TransactionSignature
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.crypto.toStringShort
import net.corda.core.flows.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.AnonymousPartyAndPath
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.builder
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.unwrap
import net.corda.schemas.CashSchemaV1
import java.util.*

@CordaSerializable
data class FxRequest(val tradeId: String,
                     val amount: Amount<Issued<Currency>>,
                     val owner: Party,
                     val counterparty: Party,
                     val notary: Party? = null)

@CordaSerializable
data class FxResponse(val inputs: List<StateAndRef<Cash.State>>,
                      val identities: List<AnonymousPartyAndPath>,
                      val outputs: List<Cash.State>)

// DOCSTART 1
// This is equivalent to the VaultService.generateSpend
// Which is brought here to make the filtering logic more visible in the example
private fun gatherOurInputs(serviceHub: ServiceHub,
                            amountRequired: Amount<Issued<Currency>>,
                            notary: Party?): Pair<List<StateAndRef<Cash.State>>, Long> {
    // extract our identity for convenience
    val ourKeys = serviceHub.keyManagementService.keys
    val ourParties = ourKeys.map { serviceHub.identityService.partyFromKey(it) ?: throw IllegalStateException("Unable to resolve party from key") }
    val fungibleCriteria = QueryCriteria.FungibleAssetQueryCriteria(owner = ourParties)

    val logicalExpression = builder { CashSchemaV1.PersistentCashState::currency.equal(amountRequired.token.product.currencyCode) }
    val cashCriteria = QueryCriteria.VaultCustomQueryCriteria(logicalExpression)

    // Collect cash type inputs
    val suitableCashStates = serviceHub.vaultQueryService.queryBy<Cash.State>(fungibleCriteria.and(cashCriteria)).states
    require(!suitableCashStates.isEmpty()) { "Insufficient funds" }

    var remaining = amountRequired.quantity
    // We will need all of the inputs to be on the same notary.
    // For simplicity we just filter on the first notary encountered
    // A production quality flow would need to migrate notary if the
    // the amounts were not sufficient in any one notary
    val sourceNotary: Party = notary ?: suitableCashStates.first().state.notary

    val inputsList = mutableListOf<StateAndRef<Cash.State>>()
    // Iterate over filtered cash states to gather enough to pay
    for (cash in suitableCashStates.filter { it.state.notary == sourceNotary }) {
        inputsList += cash
        if (remaining <= cash.state.data.amount.quantity) {
            return Pair(inputsList, cash.state.data.amount.quantity - remaining)
        }
        remaining -= cash.state.data.amount.quantity
    }
    throw IllegalStateException("Insufficient funds")
}
// DOCEND 1

private fun prepareOurInputsAndOutputs(serviceHub: ServiceHub, request: FxRequest): FxResponse {
    // Create amount with correct issuer details
    val sellAmount = request.amount

    // DOCSTART 2
    // Gather our inputs. We would normally use VaultService.generateSpend
    // to carry out the build in a single step. To be more explicit
    // we will use query manually in the helper function below.
    // Putting this into a non-suspendable function also prevents issues when
    // the flow is suspended.
    val (inputs, residual) = gatherOurInputs(serviceHub, sellAmount, request.notary)

    // Build and an output state for the counterparty
    val transferredFundsOutput = Cash.State(sellAmount, request.counterparty)

    val (outputs, myStates) = if (residual > 0L) {
        // Build an output state for the residual change back to us
        val residualAmount = Amount(residual, sellAmount.token)
        val residualOutput = Cash.State(residualAmount, serviceHub.myInfo.legalIdentity)
        Pair(listOf(transferredFundsOutput, residualOutput), (inputs.map { it.state.data } + residualOutput))
    } else {
        Pair(listOf(transferredFundsOutput), inputs.map { it.state.data })
    }

    // Extract all of our anonymous identities so the counterparty knows who they're dealing with. This includes
    // the change output so they know we're paying ourselves and not some third party.
    val identities: List<AnonymousPartyAndPath> = myStates.map { it.owner.owningKey }
            .toSet()
            .map(serviceHub.identityService::anonymousFromKey)
            .requireNoNulls()

    return FxResponse(inputs, identities, outputs)
    // DOCEND 2
}

/** A flow representing creating a transaction that carries out exchange of cash assets. */
@InitiatingFlow
class ForeignExchangeFlow(val localRequest: FxRequest, val remoteRequest: FxRequest) : FlowLogic<SecureHash>() {
    companion object {
        fun buildBuyer(tradeId: String,
                baseCurrencyAmount: Amount<Issued<Currency>>,
                quoteCurrencyAmount: Amount<Issued<Currency>>,
                baseCurrencyBuyer: Party,
                baseCurrencySeller: Party) : ForeignExchangeFlow {
            return ForeignExchangeFlow(
                    FxRequest(tradeId, quoteCurrencyAmount, baseCurrencyBuyer, baseCurrencySeller),
                    FxRequest(tradeId, baseCurrencyAmount, baseCurrencySeller, baseCurrencyBuyer)
            )
        }

        fun buildSeller(tradeId: String,
                       baseCurrencyAmount: Amount<Issued<Currency>>,
                       quoteCurrencyAmount: Amount<Issued<Currency>>,
                       baseCurrencyBuyer: Party,
                       baseCurrencySeller: Party) : ForeignExchangeFlow {
            return ForeignExchangeFlow(
                    FxRequest(tradeId, baseCurrencyAmount, baseCurrencySeller, baseCurrencyBuyer),
                    FxRequest(tradeId, quoteCurrencyAmount, baseCurrencyBuyer, baseCurrencySeller)
            )
        }
    }
    @Suspendable
    override fun call(): SecureHash {
        // Call the helper method to identify suitable inputs and make the outputs
        val (ourInputStates, _, ourOutputStates) = prepareOurInputsAndOutputs(serviceHub, localRequest)

        // identify the notary for our states
        val notary = ourInputStates.first().state.notary
        // ensure request to other side is for a consistent notary
        val remoteRequestWithNotary = remoteRequest.copy(notary = notary)

        // Send the request to the counterparty to verify and call their version of prepareOurInputsAndOutputs
        // Then they can return their candidate states
        send(remoteRequestWithNotary.owner, remoteRequestWithNotary)
        val theirInputStates = subFlow(ReceiveStateAndRefFlow<Cash.State>(remoteRequestWithNotary.owner))
        val theirIdentitites = receive<List<AnonymousPartyAndPath>>(remoteRequestWithNotary.owner).unwrap { it }
        val theirOutputStates = receive<List<Cash.State>>(remoteRequestWithNotary.owner).unwrap {
            require(theirInputStates.all { it.state.notary == notary }) {
                "notary of remote states must be same as for our states"
            }
            require(theirInputStates.all { it.state.data.amount.token == remoteRequestWithNotary.amount.token }) {
                "Inputs not of the correct currency"
            }
            require(it.all { it.amount.token == remoteRequestWithNotary.amount.token }) {
                "Outputs not of the correct currency"
            }
            require(theirInputStates.map { it.state.data.amount.quantity }.sum()
                    >= remoteRequestWithNotary.amount.quantity) {
                "the provided inputs don't provide sufficient funds"
            }
            require(it.filter { it.owner == serviceHub.myInfo.legalIdentity }.
                    map { it.amount.quantity }.sum() == remoteRequestWithNotary.amount.quantity) {
                "the provided outputs don't provide the request quantity"
            }
            it // return validated response
        }

        // register the identities presented by the counterparty
        theirIdentitites.forEach { it ->
            serviceHub.identityService.verifyAndRegisterAnonymousIdentity(it, remoteRequestWithNotary.owner)
        }

        // verify we have all the identities for states (otherwise we have a potential KYC issue)
        val theirStateOwners: Set<AbstractParty> = (theirInputStates.map { it.state.data } + theirOutputStates)
                .map(Cash.State::owner)
                .filter { it != serviceHub.myInfo.legalIdentity }
                .toSet()
        theirStateOwners.forEach {
            require(serviceHub.identityService.partyFromAnonymous(it) != null) { "Unknown owner ${it} in counterparty states" }
        }

        // having collated the data create the full transaction.
        val signedTransaction = buildTradeProposal(ourInputStates, ourOutputStates, theirInputStates, theirOutputStates)

        // pass transaction details to the counterparty to revalidate and confirm with a signature
        // Allow otherParty to access our data to resolve the transaction.
        subFlow(SendTransactionFlow(remoteRequestWithNotary.owner, signedTransaction))
        val allPartySignedTx = receive<TransactionSignature>(remoteRequestWithNotary.owner).unwrap {
            val withNewSignature = signedTransaction + it
            // check all signatures are present except the notary
            withNewSignature.verifySignaturesExcept(withNewSignature.tx.notary!!.owningKey)

            // This verifies that the transaction is contract-valid, even though it is missing signatures.
            // In a full solution there would be states tracking the trade request which
            // would be included in the transaction and enforce the amounts and tradeId
            withNewSignature.tx.toLedgerTransaction(serviceHub).verify()

            withNewSignature // return the almost complete transaction
        }

        // Initiate the standard protocol to notarise and distribute to the involved parties.
        subFlow(FinalityFlow(allPartySignedTx))

        return allPartySignedTx.id
    }

    // DOCSTART 3
    private fun buildTradeProposal(ourInputStates: List<StateAndRef<Cash.State>>,
                                   ourOutputState: List<Cash.State>,
                                   theirInputStates: List<StateAndRef<Cash.State>>,
                                   theirOutputState: List<Cash.State>): SignedTransaction {
        // This is the correct way to create a TransactionBuilder,
        // do not construct directly.
        // We also set the notary to match the input notary
        val builder = TransactionBuilder(ourInputStates.first().state.notary)

        // Add the move commands and key to indicate all the respective owners and need to sign
        val ourSigners = ourInputStates.map { it.state.data.owner.owningKey }.toSet()
        val theirSigners = theirInputStates.map { it.state.data.owner.owningKey }.toSet()
        builder.addCommand(Cash.Commands.Move(), (ourSigners + theirSigners).toList())

        // Build and add the inputs and outputs
        builder.withItems(*ourInputStates.toTypedArray())
        builder.withItems(*theirInputStates.toTypedArray())
        builder.withItems(*ourOutputState.toTypedArray())
        builder.withItems(*theirOutputState.toTypedArray())

        // We have already validated their response and trust our own data
        // so we can sign. Note the returned SignedTransaction is still not fully signed
        // and would not pass full verification yet.
        return serviceHub.signInitialTransaction(builder, ourSigners.single())
    }
    // DOCEND 3
}

@InitiatedBy(ForeignExchangeFlow::class)
class ForeignExchangeRemoteFlow(val source: Party) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        // Initial receive from remote party
        val request = receive<FxRequest>(source).unwrap {
            // We would need to check that this is a known trade ID here!
            // Also that the amounts and source are correct with the trade details.
            // In a production system there would be other Corda contracts tracking
            // the lifecycle of the Fx trades which would be included in the transaction

            // Check request is for us
            require(serviceHub.myInfo.legalIdentity == it.owner) {
                "Request does not include the correct counterparty"
            }
            require(source == it.counterparty) {
                "Request does not include the correct counterparty"
            }
            it // return validated request
        }

        // Gather our inputs. We would normally use VaultService.generateSpend
        // to carry out the build in a single step. To be more explicit
        // we will use query manually in the helper function below.
        // Putting this into a non-suspendable function also prevent issues when
        // the flow is suspended.
        val (ourInputState, ourIdentities, ourOutputState) = prepareOurInputsAndOutputs(serviceHub, request)

        // Send back our proposed states and await the full transaction to verify
        val ourKey = serviceHub.keyManagementService.filterMyKeys(ourInputState.flatMap { it.state.data.participants }.map { it.owningKey }).single()
        // SendStateAndRefFlow allows otherParty to access our transaction data to resolve the transaction.
        subFlow(SendStateAndRefFlow(source, ourInputState))
        send(source, ourIdentities)
        send(source, ourOutputState)
        val proposedTrade = subFlow(ReceiveTransactionFlow(source, checkSufficientSignatures = false)).let {
            val wtx = it.tx
            // check all signatures are present except our own and the notary
            it.verifySignaturesExcept(ourKey, wtx.notary!!.owningKey)

            // This verifies that the transaction is contract-valid, even though it is missing signatures.
            // In a full solution there would be states tracking the trade request which
            // would be included in the transaction and enforce the amounts and tradeId
            wtx.toLedgerTransaction(serviceHub).verify()
            it // return the SignedTransaction
        }

        // assuming we have completed state and business level validation we can sign the trade
        val ourSignature = serviceHub.createSignature(proposedTrade, ourKey)

        // send the other side our signature.
        send(source, ourSignature)
        // N.B. The FinalityProtocol will be responsible for Notarising the SignedTransaction
        // and broadcasting the result to us.
    }
}
