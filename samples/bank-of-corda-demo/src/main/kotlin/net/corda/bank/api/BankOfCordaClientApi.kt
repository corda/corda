package net.corda.bank.api

import net.corda.bank.api.BankOfCordaWebApi.IssueRequestParams
import net.corda.client.rpc.CordaRPCClient
import net.corda.core.contracts.Amount
import net.corda.core.contracts.currency
import net.corda.core.getOrThrow
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.flows.IssuerFlow.IssuanceRequester
import net.corda.testing.DUMMY_NOTARY
import net.corda.testing.http.HttpApi

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
     */
    fun requestRPCIssue(params: IssueRequestParams): SignedTransaction {
        val client = CordaRPCClient(hostAndPort)
        // TODO: privileged security controls required
        client.start("bankUser", "test").use { connection ->
            val rpc = connection.proxy

            // Resolve parties via RPC
            val issueToParty = rpc.partyFromX500Name(params.issueToPartyName)
                    ?: throw Exception("Unable to locate ${params.issueToPartyName} in Network Map Service")
            val issuerBankParty = rpc.partyFromX500Name(params.issuerBankName)
                    ?: throw Exception("Unable to locate ${params.issuerBankName} in Network Map Service")
            val notaryLegalIdentity = rpc.partyFromX500Name(params.notaryName)
                    ?: throw IllegalStateException("Unable to locate ${params.notaryName} in Network Map Service")
            val notaryNode = rpc.nodeIdentityFromParty(notaryLegalIdentity)
                    ?: throw IllegalStateException("Unable to locate notary node in network map cache")

            val amount = Amount(params.amount, currency(params.currency))
            val issuerToPartyRef = OpaqueBytes.of(params.issueToPartyRefAsString.toByte())

            return rpc.startFlow(::IssuanceRequester, amount, issueToParty, issuerToPartyRef, issuerBankParty, notaryNode.notaryIdentity, params.anonymous)
                    .returnValue.getOrThrow().stx
        }
    }
}
