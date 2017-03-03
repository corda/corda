package net.corda.bank.api

import com.google.common.net.HostAndPort
import net.corda.bank.api.BankOfCordaWebApi.IssueRequestParams
import net.corda.core.contracts.Amount
import net.corda.core.contracts.currency
import net.corda.core.getOrThrow
import net.corda.core.messaging.startFlow
import net.corda.core.serialization.OpaqueBytes
import net.corda.core.transactions.SignedTransaction
import net.corda.flows.IssuerFlow.IssuanceRequester
import net.corda.node.services.config.configureTestSSL
import net.corda.node.services.messaging.CordaRPCClient
import net.corda.testing.http.HttpApi

/**
 * Interface for communicating with Bank of Corda node
 */
class BankOfCordaClientApi(val hostAndPort: HostAndPort) {
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
        val client = CordaRPCClient(hostAndPort, configureTestSSL())
        // TODO: privileged security controls required
        client.start("bankUser", "test")
        val proxy = client.proxy()

        // Resolve parties via RPC
        val issueToParty = proxy.partyFromName(params.issueToPartyName)
                ?: throw Exception("Unable to locate ${params.issueToPartyName} in Network Map Service")
        val issuerBankParty = proxy.partyFromName(params.issuerBankName)
                ?: throw Exception("Unable to locate ${params.issuerBankName} in Network Map Service")

        val amount = Amount(params.amount, currency(params.currency))
        val issuerToPartyRef = OpaqueBytes.of(params.issueToPartyRefAsString.toByte())

        return proxy.startFlow(::IssuanceRequester, amount, issueToParty, issuerToPartyRef, issuerBankParty).returnValue.getOrThrow()
    }
}
