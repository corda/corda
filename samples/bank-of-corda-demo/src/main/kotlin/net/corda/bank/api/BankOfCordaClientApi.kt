package net.corda.bank.api

import net.corda.bank.api.BankOfCordaWebApi.IssueRequestParams
import net.corda.client.rpc.CordaRPCClient
import net.corda.core.contracts.Amount
import net.corda.core.messaging.startFlow
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.finance.flows.CashIssueAndPaymentFlow
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
            rpc.waitUntilNetworkReady().getOrThrow()

            // Resolve parties via RPC
            val issueToParty = rpc.wellKnownPartyFromX500Name(params.issueToPartyName)
                    ?: throw IllegalStateException("Unable to locate ${params.issueToPartyName} in Network Map Service")
            val notaryLegalIdentity = rpc.notaryIdentities().firstOrNull { it.name == params.notaryName } ?:
                    throw IllegalStateException("Couldn't locate notary ${params.notaryName} in NetworkMapCache")

            val amount = Amount(params.amount, Currency.getInstance(params.currency))
            val anonymous = true
            val issuerBankPartyRef = OpaqueBytes.of(params.issuerBankPartyRef.toByte())

            return rpc.startFlow(::CashIssueAndPaymentFlow, amount, issuerBankPartyRef, issueToParty, anonymous, notaryLegalIdentity)
                    .returnValue.getOrThrow().stx
        }
    }
}
