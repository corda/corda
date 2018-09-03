package net.corda.bank.api

import net.corda.bank.api.BankOfCordaWebApi.IssueRequestParams
import net.corda.client.rpc.CordaRPCClient
import net.corda.core.messaging.startFlow
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.finance.flows.CashIssueAndPaymentFlow
import net.corda.testing.http.HttpApi

/**
 * Interface for communicating with Bank of Corda node
 */
object BankOfCordaClientApi {
    const val BOC_RPC_USER = "bankUser"
    const val BOC_RPC_PWD = "test"

    /**
     * HTTP API
     */
    // TODO: security controls required
    fun requestWebIssue(webAddress: NetworkHostAndPort, params: IssueRequestParams) {
        val api = HttpApi.fromHostAndPort(webAddress, "api/bank")
        api.postJson("issue-asset-request", params)
    }

    /**
     * RPC API
     *
     * @return a pair of the issuing and payment transactions.
     */
    fun requestRPCIssue(rpcAddress: NetworkHostAndPort, params: IssueRequestParams): SignedTransaction {
        val client = CordaRPCClient(rpcAddress)
        // TODO: privileged security controls required
        client.start(BOC_RPC_USER, BOC_RPC_PWD).use { connection ->
            val rpc = connection.proxy
            rpc.waitUntilNetworkReady().getOrThrow()

            // Resolve parties via RPC
            val issueToParty = rpc.wellKnownPartyFromX500Name(params.issueToPartyName)
                    ?: throw IllegalStateException("Unable to locate ${params.issueToPartyName} in Network Map Service")
            val notaryLegalIdentity = rpc.notaryIdentities().firstOrNull { it.name == params.notaryName }
                    ?: throw IllegalStateException("Couldn't locate notary ${params.notaryName} in NetworkMapCache")

            val anonymous = true
            val issuerBankPartyRef = OpaqueBytes.of(params.issuerBankPartyRef.toByte())

            return rpc.startFlow(::CashIssueAndPaymentFlow, params.amount, issuerBankPartyRef, issueToParty, anonymous, notaryLegalIdentity)
                    .returnValue.getOrThrow().stx
        }
    }
}
