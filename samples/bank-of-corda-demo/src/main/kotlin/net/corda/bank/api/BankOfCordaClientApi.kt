package net.corda.bank.api

import net.corda.bank.api.BankOfCordaWebApi.IssueRequestParams
import net.corda.client.rpc.CordaRPCClient
import net.corda.client.rpc.CordaRPCClientConfiguration
import net.corda.client.rpc.CordaRPCConnection
import net.corda.client.rpc.RPCException
import net.corda.core.messaging.startFlow
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.*
import net.corda.finance.flows.CashIssueAndPaymentFlow
import net.corda.testing.http.HttpApi
import org.apache.activemq.artemis.api.core.ActiveMQSecurityException

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
    fun requestRPCIssue(rpcAddress: NetworkHostAndPort, params: IssueRequestParams): SignedTransaction {
        val client = establishConnectionWithRetry(listOf(rpcAddress), BOC_RPC_USER, BOC_RPC_PWD)
        // TODO: privileged security controls required
        client.use { connection ->
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

    /**
     * RPC API
     *
     * @return a cash issue transaction.
     */
    fun requestRPCIssueHA(availableRpcServers: List<NetworkHostAndPort>, params: IssueRequestParams): SignedTransaction {
        val client = establishConnectionWithRetry(availableRpcServers, BOC_RPC_USER, BOC_RPC_PWD)
        // TODO: privileged security controls required
        client.use { connection ->
            val rpc = connection.proxy
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

    private fun establishConnectionWithRetry(nodeHostAndPorts: List<NetworkHostAndPort>, username: String, password: String): CordaRPCConnection {
        val retryInterval = 5.seconds
        var connection: CordaRPCConnection?
        do {
            connection = try {
                logger.info("Connecting to: $nodeHostAndPorts")
                val client = CordaRPCClient(
                        nodeHostAndPorts,
                        CordaRPCClientConfiguration(connectionMaxRetryInterval = retryInterval)
                )
                val _connection = client.start(username, password)
                // Check connection is truly operational before returning it.
                val nodeInfo = _connection.proxy.nodeInfo()
                require(nodeInfo.legalIdentitiesAndCerts.isNotEmpty())
                _connection
            } catch (secEx: ActiveMQSecurityException) {
                // Happens when incorrect credentials provided - no point retrying connection
                logger.info("Security exception upon attempt to establish connection: " + secEx.message)
                throw secEx
            } catch (ex: RPCException) {
                logger.info("Exception upon attempt to establish connection: " + ex.message)
                null    // force retry are sleep
            }
            // Could not connect this time round - pause before giving another try.
            Thread.sleep(retryInterval.toMillis())
        } while (connection == null)

        logger.info("Connection successfully established with: ${connection.proxy.nodeInfo()}")
        return connection
    }
}
