package net.corda.behave.service.proxy

import net.corda.core.internal.openHttpConnection
import net.corda.core.internal.responseAs
import net.corda.core.internal.sumByLong
import net.corda.core.messaging.startFlow
import net.corda.core.node.services.Vault
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.finance.DOLLARS
import net.corda.finance.POUNDS
import net.corda.finance.SWISS_FRANCS
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.flows.CashExitFlow
import net.corda.finance.flows.CashIssueFlow
import net.corda.finance.flows.CashPaymentFlow
import org.assertj.core.api.Assertions.assertThat
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Ignore
import org.junit.Test
import java.net.URL

@Ignore
class RPCProxyWebServiceTest {

    /**
     *  client -> HTTPtoRPCProxy -> Corda Node
     *
     *  Please note these tests require a running network with at 3 Nodes
     *  (listening on ports 12002, 12007, and 12012) and a Notary
     */
    private val hostAndPort = NetworkHostAndPort("localhost", 13002)
    private val rpcProxyClient = CordaRPCProxyClient(hostAndPort)

    private val hostAndPortB = NetworkHostAndPort("localhost", 13007)
    private val rpcProxyClientB = CordaRPCProxyClient(hostAndPortB)

    private val hostAndPortC = NetworkHostAndPort("localhost", 13012)
    private val rpcProxyClientC = CordaRPCProxyClient(hostAndPortC)

    @Test
    fun myIp() {
        val response = doGet<String>("my-ip")
        println(response)
        assertTrue(response.contains("My ip is"))
    }

    @Test
    fun nodeInfo() {
        val response = rpcProxyClient.nodeInfo()
        println(response)
        assertThat(response.toString()).matches("NodeInfo\\(addresses=\\[.*\\], legalIdentitiesAndCerts=\\[.*\\], platformVersion=.*, serial=.*\\)")
    }

    @Test
    fun registeredFlows() {
        val response = rpcProxyClient.registeredFlows()
        println(response)
        // Node built-in flows
        assertThat(response).contains("net.corda.core.flows.ContractUpgradeFlow\$Authorise",
                "net.corda.core.flows.ContractUpgradeFlow\$Deauthorise",
                "net.corda.core.flows.ContractUpgradeFlow\$Initiate")
    }

    @Test
    fun notaryIdentities() {
        val response = rpcProxyClient.notaryIdentities()
        println(response)
        assertThat(response.first().name.toString()).isEqualTo("O=Notary, L=London, C=GB")
    }

    @Test
    fun networkMapSnapshot() {
        val response = rpcProxyClient.networkMapSnapshot()
        println(response)
        assertThat(response).contains(rpcProxyClient.nodeInfo())
        assertThat(response.size).isEqualTo(4)
    }

    @Test
    fun startFlowCashIssuePartyA() {
        val notary = rpcProxyClient.notaryIdentities()[0]
        val response = rpcProxyClient.startFlow(::CashIssueFlow, POUNDS(500), OpaqueBytes.of(1), notary)
        val result = response.returnValue.getOrThrow().stx
        println(result)
        assertThat(result.toString()).matches("SignedTransaction\\(id=.*\\)")
    }

    @Test
    fun startFlowCashIssuePartyB() {
        val notary = rpcProxyClientB.notaryIdentities()[0]
        val response = rpcProxyClientB.startFlow(::CashIssueFlow, DOLLARS(1000), OpaqueBytes.of(1), notary)
        val result = response.returnValue.getOrThrow().stx
        println(result)
        assertThat(result.toString()).matches("SignedTransaction\\(id=.*\\)")
    }

    @Test
    fun startFlowCashPaymentToPartyC() {
        val recipient = rpcProxyClientB.partiesFromName("PartyC", false).first()
        val response = rpcProxyClientB.startFlow(::CashPaymentFlow, DOLLARS(100), recipient)
        val result = response.returnValue.getOrThrow().stx
        println(result)
        assertThat(result.toString()).matches("SignedTransaction\\(id=.*\\)")
    }

    @Test
    fun startFlowCashPaymentToPartyB() {
        val recipient = rpcProxyClient.partiesFromName("PartyB", false).first()
        val response = rpcProxyClient.startFlow(::CashPaymentFlow, POUNDS(250), recipient)
        val result = response.returnValue.getOrThrow().stx
        println(result)
        assertThat(result.toString()).matches("SignedTransaction\\(id=.*\\)")
    }

    @Test
    fun startFlowCashPaymentToPartyA() {
        val recipient = rpcProxyClientB.partiesFromName("PartyA", false).first()
        val response = rpcProxyClientB.startFlow(::CashPaymentFlow, DOLLARS(500), recipient)
        val result = response.returnValue.getOrThrow().stx
        println(result)
        assertThat(result.toString()).matches("SignedTransaction\\(id=.*\\)")
    }

    @Test
    fun startFlowCashExit() {
        val response = rpcProxyClient.startFlow(::CashExitFlow, POUNDS(500), OpaqueBytes.of(1))
        val result = response.returnValue.getOrThrow().stx
        println(result)
        assertThat(result.toString()).matches("SignedTransaction\\(id=.*\\)")
    }

    @Test
    fun startMultiPartyCashFlows() {
        val notary = rpcProxyClient.notaryIdentities()[0]

        // Party A issue 500 GBP
        println("Party A issuing 500 GBP")
        rpcProxyClient.startFlow(::CashIssueFlow, POUNDS(500), OpaqueBytes.of(1), notary).returnValue.getOrThrow()

        // Party B issue 1000 USD
        println("Party B issuing 1000 USD")
        rpcProxyClientB.startFlow(::CashIssueFlow, DOLLARS(1000), OpaqueBytes.of(1), notary).returnValue.getOrThrow()

        // Party A transfers 250 GBP to Party B
        println("Party A transferring 250 GBP to Party B")
        val partyB = rpcProxyClient.partiesFromName("PartyB", false).first()
        rpcProxyClient.startFlow(::CashPaymentFlow, POUNDS(250), partyB).returnValue.getOrThrow()

        // Party B transfers 500 USD to Party A
        println("Party B transferring 500 USD to Party A")
        val partyA = rpcProxyClientB.partiesFromName("PartyA", false).first()
        rpcProxyClientB.startFlow(::CashPaymentFlow, DOLLARS(500), partyA).returnValue.getOrThrow()

        // Party B transfers back 125 GBP to Party A
        println("Party B transferring back 125 GBP to Party A")
        rpcProxyClientB.startFlow(::CashPaymentFlow, POUNDS(125), partyA).returnValue.getOrThrow()

        // Party A transfers back 250 USD to Party B
        println("Party A transferring back 250 USD to Party B")
        rpcProxyClient.startFlow(::CashPaymentFlow, DOLLARS(250), partyB).returnValue.getOrThrow()

        // Query the Vault of each respective Party
        val responseA = rpcProxyClient.vaultQuery(Cash.State::class.java)
        responseA.states.forEach { state ->
            println("PartyA: ${state.state.data.amount}")
        }

        val responseB = rpcProxyClientB.vaultQuery(Cash.State::class.java)
        responseB.states.forEach { state ->
            println("PartyB: ${state.state.data.amount}")
        }

        assertVaultHoldsCash(responseA, responseB)
    }

    @Test
    fun startMultiABCPartyCashFlows() {
        val notary = rpcProxyClient.notaryIdentities()[0]

        while(true) {
            // Party A issue 500 GBP
            println("Party A issuing 500 GBP")
            rpcProxyClient.startFlow(::CashIssueFlow, POUNDS(500), OpaqueBytes.of(1), notary).returnValue.getOrThrow()

            // Party B issue 500 USD
            println("Party B issuing 500 USD")
            rpcProxyClientB.startFlow(::CashIssueFlow, DOLLARS(500), OpaqueBytes.of(1), notary).returnValue.getOrThrow()

            // Party C issue 500 CHF
            println("Party C issuing 500 CHF")
            rpcProxyClientC.startFlow(::CashIssueFlow, SWISS_FRANCS(500), OpaqueBytes.of(1), notary).returnValue.getOrThrow()

            // Party A transfers 250 GBP to Party B who transfers to party C
            println("Party A transferring 250 GBP to Party B")
            val partyB = rpcProxyClient.partiesFromName("PartyB", false).first()
            rpcProxyClient.startFlow(::CashPaymentFlow, POUNDS(250), partyB).returnValue.getOrThrow()

            println(" ... and forwarding to Party C")
            val partyC = rpcProxyClientB.partiesFromName("PartyC", false).first()
            rpcProxyClientB.startFlow(::CashPaymentFlow, POUNDS(250), partyC).returnValue.getOrThrow()

            // Party B transfers 500 USD to Party C who transfers to party A
            println("Party B transferring 500 USD to Party C")
            rpcProxyClientB.startFlow(::CashPaymentFlow, DOLLARS(250), partyC).returnValue.getOrThrow()

            println(" ... and forwarding to Party A")
            val partyA = rpcProxyClientC.partiesFromName("PartyA", false).first()
            rpcProxyClientC.startFlow(::CashPaymentFlow, DOLLARS(250), partyA).returnValue.getOrThrow()

            // Party C transfers 550 CHF to Party A who transfers to party B
            println("Party C transferring 250 CHF to Party A")
            rpcProxyClientC.startFlow(::CashPaymentFlow, SWISS_FRANCS(250), partyA).returnValue.getOrThrow()

            println(" ... and forwarding to Party B")
            rpcProxyClient.startFlow(::CashPaymentFlow, SWISS_FRANCS(250), partyB).returnValue.getOrThrow()

            // Query the Vault of each respective Party
            val responseA = rpcProxyClient.vaultQuery(Cash.State::class.java)
            responseA.states.forEach { state ->
                println("PartyA: ${state.state.data.amount}")
            }

            val responseB = rpcProxyClientB.vaultQuery(Cash.State::class.java)
            responseB.states.forEach { state ->
                println("PartyB: ${state.state.data.amount}")
            }

            val responseC = rpcProxyClientC.vaultQuery(Cash.State::class.java)
            responseC.states.forEach { state ->
                println("PartyC: ${state.state.data.amount}")
            }

            println("============================================================================================")
        }
    }

    // enable Flow Draining on Node B
    @Test
    fun startMultiACPartyCashFlows() {
        val notary = rpcProxyClient.notaryIdentities()[0]

        while(true) {
            // Party A issue 500 GBP
            println("Party A issuing 500 GBP")
            rpcProxyClient.startFlow(::CashIssueFlow, POUNDS(500), OpaqueBytes.of(1), notary).returnValue.getOrThrow()

            // Party C issue 500 CHF
            println("Party C issuing 500 CHF")
            rpcProxyClientC.startFlow(::CashIssueFlow, SWISS_FRANCS(500), OpaqueBytes.of(1), notary).returnValue.getOrThrow()

            // Party A transfers 250 GBP to Party B who transfers to party C
            println("Party A transferring 250 GBP to Party B")
            val partyB = rpcProxyClient.partiesFromName("PartyB", false).first()
            rpcProxyClient.startFlow(::CashPaymentFlow, POUNDS(250), partyB)    // BLOCKS!!!!!

            println(" ... and forwarding to Party A")
            val partyA = rpcProxyClientC.partiesFromName("PartyA", false).first()
            rpcProxyClientC.startFlow(::CashPaymentFlow, DOLLARS(250), partyA).returnValue.getOrThrow()

            // Party C transfers 550 CHF to Party A who transfers to party B
            println("Party C transferring 250 CHF to Party A")
            rpcProxyClientC.startFlow(::CashPaymentFlow, SWISS_FRANCS(250), partyA).returnValue.getOrThrow()

            println(" ... and forwarding to Party B")
            rpcProxyClient.startFlow(::CashPaymentFlow, SWISS_FRANCS(250), partyB)

            // Query the Vault of each respective Party
            val responseA = rpcProxyClient.vaultQuery(Cash.State::class.java)
            responseA.states.forEach { state ->
                println("PartyA: ${state.state.data.amount}")
            }

            val responseB = rpcProxyClientB.vaultQuery(Cash.State::class.java)
            responseB.states.forEach { state ->
                println("PartyB: ${state.state.data.amount}")
            }

            val responseC = rpcProxyClientC.vaultQuery(Cash.State::class.java)
            responseC.states.forEach { state ->
                println("PartyC: ${state.state.data.amount}")
            }

            println("============================================================================================")
        }
    }

    @Test
    fun vaultQueryCash() {
        try {
            val responseA = rpcProxyClient.vaultQuery(Cash.State::class.java)
            responseA.states.forEach { state ->
                println("PartyA: ${state.state.data.amount}")
            }

            val responseB = rpcProxyClientB.vaultQuery(Cash.State::class.java)
            responseB.states.forEach { state ->
                println("PartyB: ${state.state.data.amount}")
            }

            val responseC = rpcProxyClientC.vaultQuery(Cash.State::class.java)
            responseC.states.forEach { state ->
                println("PartyC: ${state.state.data.amount}")
            }

            assertVaultHoldsCash(responseA, responseB, responseC)
        }
        catch (e: Exception) {
            println("Vault Cash query error: ${e.message}")
            fail()
        }
    }

    private fun assertVaultHoldsCash(vararg vaultPages: Vault.Page<Cash.State>) {
        vaultPages.forEach { vaultPage ->
            assertThat(vaultPage.states.size).isGreaterThan(0)
            vaultPage.states.groupBy { it.state.data.amount.token.product.currencyCode }.forEach { _, value ->
                assertThat(value.sumByLong { it.state.data.amount.quantity }).isGreaterThan(0L)
            }
        }
    }

    private inline fun <reified T : Any> doGet(path: String): T {
        return URL("http://$hostAndPort/rpc/$path").openHttpConnection().responseAs()
    }
}