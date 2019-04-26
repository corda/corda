package net.corda.bank.api

import net.corda.bank.api.BankOfCordaWebApi.IssueRequestParams
import net.corda.client.rpc.internal.ReconnectingCordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.loggerFor
import net.corda.finance.flows.CashIssueAndPaymentFlow
import net.corda.testing.http.HttpApi

/**
 * Interface for communicating with Bank of Corda node
 */
object BankOfCordaClientApi {
    const val BOC_RPC_USER = "bankUser"
    const val BOC_RPC_PWD = "test"

    private val logger = loggerFor<BankOfCordaClientApi>()

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
     * @return a payment transaction (following successful issuance of cash to self).
     */
    fun requestRPCIssue(rpcAddress: NetworkHostAndPort, params: IssueRequestParams): SignedTransaction = requestRPCIssueHA(listOf(rpcAddress), params)

    /**
     * RPC API
     *
     * @return a cash issue transaction.
     */
    fun requestRPCIssueHA(availableRpcServers: List<NetworkHostAndPort>, params: IssueRequestParams): SignedTransaction {
        // TODO: privileged security controls required
        ReconnectingCordaRPCOps(availableRpcServers, BOC_RPC_USER, BOC_RPC_PWD).use { rpc->
            rpc.waitUntilNetworkReady().getOrThrow()

            // Resolve parties via RPC
            val issueToParty = rpc.wellKnownPartyFromX500Name(params.issueToPartyName)
                    ?: throw IllegalStateException("Unable to locate ${params.issueToPartyName} in Network Map Service")
            val notaryLegalIdentity = rpc.notaryIdentities().firstOrNull { it.name == params.notaryName }
                    ?: throw IllegalStateException("Couldn't locate notary ${params.notaryName} in NetworkMapCache")

            val anonymous = true
            val issuerBankPartyRef = OpaqueBytes.of(params.issuerBankPartyRef.toByte())

            logger.info("${rpc.nodeInfo()} issuing ${params.amount} to transfer to $issueToParty ...")
            return rpc.startFlow(::CashIssueAndPaymentFlow, params.amount, issuerBankPartyRef, issueToParty, anonymous, notaryLegalIdentity)
                    .returnValue.getOrThrow().stx
        }
    }
}
