package net.corda.bank.api

import net.corda.bank.api.BankOfCordaWebApi.IssueRequestParams
import net.corda.client.rpc.CordaRPCClient
import net.corda.core.contracts.Amount
import net.corda.core.messaging.startFlow
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.flows.CashIssueFlow
import net.corda.flows.CashPaymentFlow
import net.corda.testing.http.HttpApi
import java.util.*

/**
 * Interface for communicating with Bank of Corda node
 */
class BankOfCordaClientApi(val hostAndPort: NetworkHostAndPort) {
    private val apiRoot = "api/bank"
    /**
     * HTTP API
     */
    // TODO: security controls required
    fun requestWebIssue(params: IssueRequestParams): Boolean {
        val api = HttpApi.fromHostAndPort(hostAndPort, apiRoot)
        return api.postJson("issue-asset-request", params)
    }

    /**
     * RPC API
     *
     * @return a pair of the issuing and payment transactions.
     */
    fun requestRPCIssue(params: IssueRequestParams): SignedTransaction {
        val client = CordaRPCClient(hostAndPort)
        // TODO: privileged security controls required
        client.start("bankUser", "test").use { connection ->
            val rpc = connection.proxy

            // Resolve parties via RPC
            val issueToParty = rpc.partyFromX500Name(params.issueToPartyName)
                    ?: throw Exception("Unable to locate ${params.issueToPartyName} in Network Map Service")
            val notaryLegalIdentity = rpc.partyFromX500Name(params.notaryName)
                    ?: throw IllegalStateException("Unable to locate ${params.notaryName} in Network Map Service")
            val notaryNode = rpc.nodeIdentityFromParty(notaryLegalIdentity)
                    ?: throw IllegalStateException("Unable to locate notary node in network map cache")

            val amount = Amount(params.amount, Currency.getInstance(params.currency))
            val issuerBankPartyRef = OpaqueBytes.of(params.issuerBankPartyRef.toByte())

            rpc.startFlow(::CashIssueFlow, amount, issuerBankPartyRef, notaryNode.notaryIdentity)
                    .returnValue.getOrThrow().stx
            return rpc.startFlow(::CashPaymentFlow, amount, issueToParty, params.anonymous)
                    .returnValue.getOrThrow().stx
        }
    }
}
