package net.corda.explorer

import joptsimple.OptionSet
import net.corda.client.mock.ErrorFlowsEventGenerator
import net.corda.client.mock.EventGenerator
import net.corda.client.mock.Generator
import net.corda.client.rpc.CordaRPCClient
import net.corda.client.rpc.CordaRPCConnection
import net.corda.core.contracts.Amount
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.concurrent.thenMatch
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.FlowHandle
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.finance.GBP
import net.corda.finance.USD
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.flows.*
import net.corda.finance.flows.CashExitFlow.ExitRequest
import net.corda.finance.flows.CashIssueAndPaymentFlow.IssueAndPaymentRequest
import net.corda.node.services.Permissions.Companion.startFlow
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.driver.*
import net.corda.testing.node.User
import java.time.Instant
import java.util.*

class ExplorerSimulation(private val options: OptionSet) {
    private val user = User("user1", "test", permissions = setOf(
            startFlow<CashPaymentFlow>(),
            startFlow<CashConfigDataFlow>()
    ))
    private val manager = User("manager", "test", permissions = setOf(
            startFlow<CashIssueAndPaymentFlow>(),
            startFlow<CashPaymentFlow>(),
            startFlow<CashExitFlow>(),
            startFlow<CashConfigDataFlow>())
    )

    private lateinit var notaryNode: NodeHandle
    private lateinit var aliceNode: NodeHandle
    private lateinit var bobNode: NodeHandle
    private lateinit var issuerNodeGBP: NodeHandle
    private lateinit var issuerNodeUSD: NodeHandle
    private lateinit var notary: Party

    private val RPCConnections = ArrayList<CordaRPCConnection>()
    private val issuers = HashMap<Currency, CordaRPCOps>()
    private val parties = ArrayList<Pair<Party, CordaRPCOps>>()

    init {
        startDemoNodes()
    }

    private fun onEnd() {
        println("Closing RPC connections")
        RPCConnections.forEach { it.close() }
    }

    private fun startDemoNodes() {
        val portAllocation = PortAllocation.Incremental(20000)
        driver(DriverParameters(portAllocation = portAllocation, extraCordappPackagesToScan = listOf(Cash::class.java.`package`.name), waitForAllNodesToFinish = true, jmxPolicy = JmxPolicy(true))) {
            // TODO : Supported flow should be exposed somehow from the node instead of set of ServiceInfo.
            val alice = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user))
            val bob = startNode(providedName = BOB_NAME, rpcUsers = listOf(user))
            val ukBankName = CordaX500Name(organisation = "UK Bank Plc", locality = "London", country = "GB")
            val usaBankName = CordaX500Name(organisation = "USA Bank Corp", locality = "New York", country = "US")
            val issuerGBP = startNode(providedName = ukBankName, rpcUsers = listOf(manager),
                    customOverrides = mapOf("custom" to mapOf("issuableCurrencies" to listOf("GBP"))))
            val issuerUSD = startNode(providedName = usaBankName, rpcUsers = listOf(manager),
                    customOverrides = mapOf("custom" to mapOf("issuableCurrencies" to listOf("USD"))))

            notaryNode = defaultNotaryNode.get()
            aliceNode = alice.get()
            bobNode = bob.get()
            issuerNodeGBP = issuerGBP.get()
            issuerNodeUSD = issuerUSD.get()

            arrayOf(notaryNode, aliceNode, bobNode, issuerNodeGBP, issuerNodeUSD).forEach {
                println("${it.nodeInfo.legalIdentities.first()} started on ${it.rpcAddress}")
            }

            when {
                options.has("S") -> startNormalSimulation()
                options.has("F") -> startErrorFlowsSimulation()
            }
        }
    }

    private fun setUpRPC() {
        // Register with alice to use alice's RPC proxy to create random events.
        val aliceClient = CordaRPCClient(aliceNode.rpcAddress)
        val aliceConnection = aliceClient.start(user.username, user.password)
        val aliceRPC = aliceConnection.proxy

        val bobClient = CordaRPCClient(bobNode.rpcAddress)
        val bobConnection = bobClient.start(user.username, user.password)
        val bobRPC = bobConnection.proxy

        val issuerClientGBP = CordaRPCClient(issuerNodeGBP.rpcAddress)
        val issuerGBPConnection = issuerClientGBP.start(manager.username, manager.password)
        val issuerRPCGBP = issuerGBPConnection.proxy

        val issuerClientUSD = CordaRPCClient(issuerNodeUSD.rpcAddress)
        val issuerUSDConnection = issuerClientUSD.start(manager.username, manager.password)
        val issuerRPCUSD = issuerUSDConnection.proxy

        RPCConnections.addAll(listOf(aliceConnection, bobConnection, issuerGBPConnection, issuerUSDConnection))
        issuers.putAll(mapOf(USD to issuerRPCUSD, GBP to issuerRPCGBP))

        parties.addAll(listOf(aliceNode.nodeInfo.legalIdentities.first() to aliceRPC,
                bobNode.nodeInfo.legalIdentities.first() to bobRPC,
                issuerNodeGBP.nodeInfo.legalIdentities.first() to issuerRPCGBP,
                issuerNodeUSD.nodeInfo.legalIdentities.first() to issuerRPCUSD))
    }

    private fun startSimulation(eventGenerator: EventGenerator, maxIterations: Int) {
        // Log to logger when flow finish.
        fun FlowHandle<AbstractCashFlow.Result>.log(seq: Int, name: String) {
            val out = "[$seq] $name $id :"
            returnValue.thenMatch({ (stx) ->
                Main.log.info("$out ${stx.id} ${(stx.tx.outputs.first().data as Cash.State).amount}") // XXX: Why Main's log?
            }, {
                Main.log.info("$out ${it.message}")
            })
        }

        for (i in 0..maxIterations) {
            Thread.sleep(1000)
            // Issuer requests.
            eventGenerator.issuerGenerator.map { request ->
                when (request) {
                    is IssueAndPaymentRequest -> issuers[request.amount.token]?.let {
                        println("${Instant.now()} [$i] ISSUING ${request.amount} with ref ${request.issueRef} to ${request.recipient}")
                        it.startFlow(::CashIssueAndPaymentFlow, request).log(i, "${request.amount.token}Issuer")
                    }
                    is ExitRequest -> issuers[request.amount.token]?.let {
                        println("${Instant.now()} [$i] EXITING ${request.amount} with ref ${request.issuerRef}")
                        it.startFlow(::CashExitFlow, request).log(i, "${request.amount.token}Exit")
                    }
                    else -> throw IllegalArgumentException("Unsupported command: $request")
                }
            }.generate(SplittableRandom())
            // Party pay requests.
            eventGenerator.moveCashGenerator.combine(Generator.pickOne(parties)) { request, (party, rpc) ->
                println("${Instant.now()} [$i] SENDING ${request.amount} from $party to ${request.recipient}")
                rpc.startFlow(::CashPaymentFlow, request).log(i, party.name.toString())
            }.generate(SplittableRandom())
        }
        println("Simulation completed")
    }

    private fun startNormalSimulation() {
        println("Running simulation mode ...")
        setUpRPC()
        notary = aliceNode.rpc.notaryIdentities().first()
        val eventGenerator = EventGenerator(
                parties = parties.map { it.first },
                notary = notary,
                currencies = listOf(GBP, USD)
        )
        val maxIterations = 100_000
        val anonymous = true
        // Pre allocate some money to each party.
        eventGenerator.parties.forEach {
            for (ref in 0..1) {
                for ((currency, issuer) in issuers) {
                    val amount = Amount(1_000_000, currency)
                    issuer.startFlow(::CashIssueAndPaymentFlow, amount, OpaqueBytes(ByteArray(1, { ref.toByte() })),
                            it, anonymous, notary).returnValue.getOrThrow()
                }
            }
        }
        startSimulation(eventGenerator, maxIterations)
        onEnd()
    }

    private fun startErrorFlowsSimulation() {
        println("Running flows with errors simulation mode ...")
        setUpRPC()
        val eventGenerator = ErrorFlowsEventGenerator(
                parties = parties.map { it.first },
                notary = notary,
                currencies = listOf(GBP, USD)
        )
        val maxIterations = 10_000
        startSimulation(eventGenerator, maxIterations)
        onEnd()
    }
}
