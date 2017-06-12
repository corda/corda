package net.corda.explorer

import joptsimple.OptionSet
import net.corda.client.mock.EventGenerator
import net.corda.client.mock.Generator
import net.corda.client.mock.pickOne
import net.corda.client.rpc.CordaRPCConnection
import net.corda.contracts.asset.Cash
import net.corda.core.contracts.Amount
import net.corda.core.contracts.GBP
import net.corda.core.contracts.USD
import net.corda.core.failure
import net.corda.core.identity.Party
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.FlowHandle
import net.corda.core.node.services.ServiceInfo
import net.corda.core.node.services.ServiceType
import net.corda.core.serialization.OpaqueBytes
import net.corda.core.success
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ALICE
import net.corda.core.utilities.BOB
import net.corda.core.utilities.DUMMY_NOTARY
import net.corda.flows.CashExitFlow
import net.corda.flows.CashFlowCommand
import net.corda.flows.CashIssueFlow
import net.corda.flows.CashPaymentFlow
import net.corda.flows.IssuerFlow
import net.corda.node.driver.NodeHandle
import net.corda.node.driver.PortAllocation
import net.corda.node.driver.driver
import net.corda.node.services.startFlowPermission
import net.corda.node.services.transactions.SimpleNotaryService
import net.corda.nodeapi.User
import org.bouncycastle.asn1.x500.X500Name
import java.time.Instant
import java.util.*

class ExplorerSimulation(val options: OptionSet) {
    val user = User("user1", "test", permissions = setOf(
            startFlowPermission<CashPaymentFlow>()
    ))
    val manager = User("manager", "test", permissions = setOf(
            startFlowPermission<CashIssueFlow>(),
            startFlowPermission<CashPaymentFlow>(),
            startFlowPermission<CashExitFlow>(),
            startFlowPermission<IssuerFlow.IssuanceRequester>())
    )

    lateinit var notaryNode: NodeHandle
    lateinit var aliceNode: NodeHandle
    lateinit var bobNode: NodeHandle
    lateinit var issuerNodeGBP: NodeHandle
    lateinit var issuerNodeUSD: NodeHandle

    val RPCConnections = ArrayList<CordaRPCConnection>()
    val issuers = HashMap<Currency, CordaRPCOps>()
    val parties = ArrayList<Pair<Party, CordaRPCOps>>()

    init {
        startDemoNodes()
    }

    private fun onEnd() {
        println("Closing RPC connections")
        RPCConnections.forEach { it.close() }
    }

    private fun startDemoNodes() {
        val portAllocation = PortAllocation.Incremental(20000)
        driver(portAllocation = portAllocation) {
            // TODO : Supported flow should be exposed somehow from the node instead of set of ServiceInfo.
            val notary = startNode(DUMMY_NOTARY.name, advertisedServices = setOf(ServiceInfo(SimpleNotaryService.type)),
                    customOverrides = mapOf("nearestCity" to "Zurich"))
            val alice = startNode(ALICE.name, rpcUsers = arrayListOf(user),
                    advertisedServices = setOf(ServiceInfo(ServiceType.corda.getSubType("cash"))),
                    customOverrides = mapOf("nearestCity" to "Milan"))
            val bob = startNode(BOB.name, rpcUsers = arrayListOf(user),
                    advertisedServices = setOf(ServiceInfo(ServiceType.corda.getSubType("cash"))),
                    customOverrides = mapOf("nearestCity" to "Madrid"))
            val ukBankName = X500Name("CN=UK Bank Plc,O=UK Bank Plc,L=London,C=UK")
            val usaBankName = X500Name("CN=USA Bank Corp,O=USA Bank Corp,L=New York,C=USA")
            val issuerGBP = startNode(ukBankName, rpcUsers = arrayListOf(manager),
                    advertisedServices = setOf(ServiceInfo(ServiceType.corda.getSubType("issuer.GBP"))),
                    customOverrides = mapOf("nearestCity" to "London"))
            val issuerUSD = startNode(usaBankName, rpcUsers = arrayListOf(manager),
                    advertisedServices = setOf(ServiceInfo(ServiceType.corda.getSubType("issuer.USD"))),
                    customOverrides = mapOf("nearestCity" to "New York"))

            notaryNode = notary.get()
            aliceNode = alice.get()
            bobNode = bob.get()
            issuerNodeGBP = issuerGBP.get()
            issuerNodeUSD = issuerUSD.get()

            arrayOf(notaryNode, aliceNode, bobNode, issuerNodeGBP, issuerNodeUSD).forEach {
                println("${it.nodeInfo.legalIdentity} started on ${it.configuration.rpcAddress}")
            }

            when {
                options.has("S") -> startNormalSimulation()
            }

            waitForAllNodesToFinish()
        }
    }

    private fun setUpRPC() {
        // Register with alice to use alice's RPC proxy to create random events.
        val aliceClient = aliceNode.rpcClientToNode()
        val aliceConnection = aliceClient.start(user.username, user.password)
        val aliceRPC = aliceConnection.proxy

        val bobClient = bobNode.rpcClientToNode()
        val bobConnection = bobClient.start(user.username, user.password)
        val bobRPC = bobConnection.proxy

        val issuerClientGBP = issuerNodeGBP.rpcClientToNode()
        val issuerGBPConnection = issuerClientGBP.start(manager.username, manager.password)
        val issuerRPCGBP = issuerGBPConnection.proxy

        val issuerClientUSD = issuerNodeUSD.rpcClientToNode()
        val issuerUSDConnection =issuerClientUSD.start(manager.username, manager.password)
        val issuerRPCUSD = issuerUSDConnection.proxy

        RPCConnections.addAll(listOf(aliceConnection, bobConnection, issuerGBPConnection, issuerUSDConnection))
        issuers.putAll(mapOf(USD to issuerRPCUSD, GBP to issuerRPCGBP))

        parties.addAll(listOf(aliceNode.nodeInfo.legalIdentity to aliceRPC,
                bobNode.nodeInfo.legalIdentity to bobRPC,
                issuerNodeGBP.nodeInfo.legalIdentity to issuerRPCGBP,
                issuerNodeUSD.nodeInfo.legalIdentity to issuerRPCUSD))
    }

    private fun startSimulation(eventGenerator: EventGenerator, maxIterations: Int) {
        // Log to logger when flow finish.
        fun FlowHandle<SignedTransaction>.log(seq: Int, name: String) {
            val out = "[$seq] $name $id :"
            returnValue.success {
                Main.log.info("$out ${it.id} ${(it.tx.outputs.first().data as Cash.State).amount}")
            }.failure {
                Main.log.info("$out ${it.message}")
            }
        }

        for (i in 0..maxIterations) {
            Thread.sleep(300)
            // Issuer requests.
            eventGenerator.issuerGenerator.map { command ->
                when (command) {
                    is CashFlowCommand.IssueCash -> issuers[command.amount.token]?.let {
                        println("${Instant.now()} [$i] ISSUING ${command.amount} with ref ${command.issueRef} to ${command.recipient}")
                        command.startFlow(it).log(i, "${command.amount.token}Issuer")
                    }
                    is CashFlowCommand.ExitCash -> issuers[command.amount.token]?.let {
                        println("${Instant.now()} [$i] EXITING ${command.amount} with ref ${command.issueRef}")
                        command.startFlow(it).log(i, "${command.amount.token}Exit")
                    }
                    else -> throw IllegalArgumentException("Unsupported command: $command")
                }
            }.generate(SplittableRandom())
            // Party pay requests.
            eventGenerator.moveCashGenerator.combine(Generator.pickOne(parties)) { command, (party, rpc) ->
                println("${Instant.now()} [$i] SENDING ${command.amount} from $party to ${command.recipient}")
                command.startFlow(rpc).log(i, party.name.toString())
            }.generate(SplittableRandom())
        }
        println("Simulation completed")
    }

    private fun startNormalSimulation() {
        println("Running simulation mode ...")
        setUpRPC()
        val eventGenerator = EventGenerator(
                parties = parties.map { it.first },
                notary = notaryNode.nodeInfo.notaryIdentity,
                currencies = listOf(GBP, USD)
        )
        val maxIterations = 100_000
        // Pre allocate some money to each party.
        eventGenerator.parties.forEach {
            for (ref in 0..1) {
                for ((currency, issuer) in issuers) {
                    CashFlowCommand.IssueCash(Amount(1_000_000, currency), OpaqueBytes(ByteArray(1, { ref.toByte() })), it, notaryNode.nodeInfo.notaryIdentity).startFlow(issuer)
                }
            }
        }
        startSimulation(eventGenerator, maxIterations)
        onEnd()
    }
}
