package net.corda.docs

import co.paralleluniverse.fibers.Suspendable
import net.corda.contracts.asset.Cash
import net.corda.core.contracts.Amount
import net.corda.core.contracts.Issued
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.TransactionType
import net.corda.core.crypto.DigitalSignature
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.Party
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.node.PluginServiceHub
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.unconsumedStates
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.unwrap
import net.corda.flows.FinalityFlow
import net.corda.flows.ResolveTransactionsFlow
import java.util.*

@CordaSerializable
private data class FxRequest(val tradeId: String,
                             val amount: Amount<Issued<Currency>>,
                             val owner: Party,
                             val counterparty: Party,
                             val notary: Party? = null)

@CordaSerializable
private data class FxResponse(val inputs: List<StateAndRef<Cash.State>>,
                              val outputs: List<Cash.State>)

// DOCSTART 1
// This is equivalent to the VaultService.generateSpend
// Which is brought here to make the filtering logic more visible in the example
private fun gatherOurInputs(serviceHub: ServiceHub,
                            amountRequired: Amount<Issued<Currency>>,
                            notary: Party?): Pair<List<StateAndRef<Cash.State>>, Long> {
    // Collect cash type inputs
    val cashStates = serviceHub.vaultService.unconsumedStates<Cash.State>()
    // extract our identity for convenience
    val ourKeys = serviceHub.keyManagementService.keys
    // Filter down to our own cash states with right currency and issuer
    val suitableCashStates = cashStates.filter {
        val state = it.state.data
        // TODO: We may want to have the list of our states pre-cached somewhere for performance
        (state.owner.owningKey in ourKeys) && (state.amount.token == amountRequired.token)
    }
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
    val transferedFundsOutput = Cash.State(sellAmount, request.counterparty)

    if (residual > 0L) {
        // Build an output state for the residual change back to us
        val residualAmount = Amount(residual, sellAmount.token)
        val residualOutput = Cash.State(residualAmount, serviceHub.myInfo.legalIdentity)
        return FxResponse(inputs, listOf(transferedFundsOutput, residualOutput))
    } else {
        return FxResponse(inputs, listOf(transferedFundsOutput))
    }
    // DOCEND 2
}

// A flow representing creating a transaction that
// carries out exchange of cash assets.
@InitiatingFlow
class ForeignExchangeFlow(val tradeId: String,
                          val baseCurrencyAmount: Amount<Issued<Currency>>,
                          val quoteCurrencyAmount: Amount<Issued<Currency>>,
                          val baseCurrencyBuyer: Party,
                          val baseCurrencySeller: Party) : FlowLogic<SecureHash>() {
    @Suspendable
    override fun call(): SecureHash {
        // Select correct sides of the Fx exchange to query for.
        // Specifically we own the assets we wish to sell.
        // Also prepare the other side query
        val (localRequest, remoteRequest) = if (baseCurrencySeller == serviceHub.myInfo.legalIdentity) {
            val local = FxRequest(tradeId, baseCurrencyAmount, baseCurrencySeller, baseCurrencyBuyer)
            val remote = FxRequest(tradeId, quoteCurrencyAmount, baseCurrencyBuyer, baseCurrencySeller)
            Pair(local, remote)
        } else if (baseCurrencyBuyer == serviceHub.myInfo.legalIdentity) {
            val local = FxRequest(tradeId, quoteCurrencyAmount, baseCurrencyBuyer, baseCurrencySeller)
            val remote = FxRequest(tradeId, baseCurrencyAmount, baseCurrencySeller, baseCurrencyBuyer)
            Pair(local, remote)
        } else throw IllegalArgumentException("Our identity must be one of the parties in the trade.")

        // Call the helper method to identify suitable inputs and make the outputs
        val ourStates = prepareOurInputsAndOutputs(serviceHub, localRequest)

        // identify the notary for our states
        val notary = ourStates.inputs.first().state.notary
        // ensure request to other side is for a consistent notary
        val remoteRequestWithNotary = remoteRequest.copy(notary = notary)

        // Send the request to the counterparty to verify and call their version of prepareOurInputsAndOutputs
        // Then they can return their candidate states
        val theirStates = sendAndReceive<FxResponse>(remoteRequestWithNotary.owner, remoteRequestWithNotary).unwrap {
            require(it.inputs.all { it.state.notary == notary }) {
                "notary of remote states must be same as for our states"
            }
            require(it.inputs.all { it.state.data.amount.token == remoteRequestWithNotary.amount.token }) {
                "Inputs not of the correct currency"
            }
            require(it.outputs.all { it.amount.token == remoteRequestWithNotary.amount.token }) {
                "Outputs not of the correct currency"
            }
            require(it.inputs.map { it.state.data.amount.quantity }.sum()
                    >= remoteRequestWithNotary.amount.quantity) {
                "the provided inputs don't provide sufficient funds"
            }
            require(it.outputs.filter { it.owner == serviceHub.myInfo.legalIdentity }.
                    map { it.amount.quantity }.sum() == remoteRequestWithNotary.amount.quantity) {
                "the provided outputs don't provide the request quantity"
            }
            // Download their inputs chains to validate that they are OK
            val dependencyTxIDs = it.inputs.map { it.ref.txhash }.toSet()
            subFlow(ResolveTransactionsFlow(dependencyTxIDs, remoteRequestWithNotary.owner))

            it // return validated response
        }

        // having collated the data create the full transaction.
        val signedTransaction = buildTradeProposal(ourStates, theirStates)

        // pass transaction details to the counterparty to revalidate and confirm with a signature
        val allPartySignedTx = sendAndReceive<DigitalSignature.WithKey>(remoteRequestWithNotary.owner, signedTransaction).unwrap {
            val withNewSignature = signedTransaction + it
            // check all signatures are present except the notary
            withNewSignature.verifySignatures(withNewSignature.tx.notary!!.owningKey)

            // This verifies that the transaction is contract-valid, even though it is missing signatures.
            // In a full solution there would be states tracking the trade request which
            // would be included in the transaction and enforce the amounts and tradeId
            withNewSignature.tx.toLedgerTransaction(serviceHub).verify()

            withNewSignature // return the almost complete transaction
        }

        // Initiate the standard protocol to notarise and distribute to the involved parties.
        subFlow(FinalityFlow(allPartySignedTx, setOf(baseCurrencyBuyer, baseCurrencySeller)))

        return allPartySignedTx.id
    }

    // DOCSTART 3
    private fun buildTradeProposal(ourStates: FxResponse, theirStates: FxResponse): SignedTransaction {
        // This is the correct way to create a TransactionBuilder,
        // do not construct directly.
        // We also set the notary to match the input notary
        val builder = TransactionType.General.Builder(ourStates.inputs.first().state.notary)

        // Add the move commands and key to indicate all the respective owners and need to sign
        val ourSigners = ourStates.inputs.map { it.state.data.owner.owningKey }.toSet()
        val theirSigners = theirStates.inputs.map { it.state.data.owner.owningKey }.toSet()
        builder.addCommand(Cash.Commands.Move(), (ourSigners + theirSigners).toList())

        // Build and add the inputs and outputs
        builder.withItems(*ourStates.inputs.toTypedArray())
        builder.withItems(*theirStates.inputs.toTypedArray())
        builder.withItems(*ourStates.outputs.toTypedArray())
        builder.withItems(*theirStates.outputs.toTypedArray())

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
        val ourResponse = prepareOurInputsAndOutputs(serviceHub, request)

        // Send back our proposed states and await the full transaction to verify
        val ourKeys = serviceHub.keyManagementService.keys
        val ourKey = serviceHub.keyManagementService.filterMyKeys(ourResponse.inputs.flatMap { it.state.data.participants }.map { it.owningKey }).single()
        val proposedTrade = sendAndReceive<SignedTransaction>(source, ourResponse).unwrap {
            val wtx = it.tx
            // check all signatures are present except our own and the notary
            it.verifySignatures(ourKey, wtx.notary!!.owningKey)

            // We need to fetch their complete input states and dependencies so that verify can operate
            checkDependencies(it)

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

    @Suspendable
    private fun checkDependencies(stx: SignedTransaction) {
        // Download and check all the transactions that this transaction depends on, but do not check this
        // transaction itself.
        val dependencyTxIDs = stx.tx.inputs.map { it.txhash }.toSet()
        subFlow(ResolveTransactionsFlow(dependencyTxIDs, source))
    }
}
