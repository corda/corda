package net.corda.docs

import net.corda.client.rpc.notUsed
import net.corda.contracts.asset.Cash
import net.corda.core.contracts.Amount
import net.corda.core.contracts.USD
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.node.CordaPluginRegistry
import net.corda.core.node.services.ServiceInfo
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.OpaqueBytes
import net.corda.core.serialization.SerializationCustomization
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ALICE
import net.corda.core.utilities.DUMMY_NOTARY
import net.corda.flows.CashExitFlow
import net.corda.flows.CashIssueFlow
import net.corda.flows.CashPaymentFlow
import net.corda.node.driver.driver
import net.corda.node.services.startFlowPermission
import net.corda.node.services.transactions.ValidatingNotaryService
import net.corda.nodeapi.User
import org.graphstream.graph.Edge
import org.graphstream.graph.Node
import org.graphstream.graph.implementations.MultiGraph
import rx.Observable
import java.nio.file.Paths
import java.util.*
import kotlin.concurrent.thread

/**
 * This is example code for the Client RPC API tutorial. The START/END comments are important and used by the documentation!
 */

// START 1
enum class PrintOrVisualise {
    Print,
    Visualise
}

fun main(args: Array<String>) {
    require(args.isNotEmpty()) { "Usage: <binary> [Print|Visualise]" }
    val printOrVisualise = PrintOrVisualise.valueOf(args[0])

    val baseDirectory = Paths.get("build/rpc-api-tutorial")
    val user = User("user", "password", permissions = setOf(startFlowPermission<CashIssueFlow>(),
            startFlowPermission<CashPaymentFlow>(),
            startFlowPermission<CashExitFlow>()))

    driver(driverDirectory = baseDirectory) {
        startNode(DUMMY_NOTARY.name, advertisedServices = setOf(ServiceInfo(ValidatingNotaryService.type)))
        val node = startNode(ALICE.name, rpcUsers = listOf(user)).get()
        // END 1

        // START 2
        val client = node.rpcClientToNode()
        val proxy = client.start("user", "password").proxy

        thread {
            generateTransactions(proxy)
        }
        // END 2

        // START 3
        val (transactions: List<SignedTransaction>, futureTransactions: Observable<SignedTransaction>) = proxy.verifiedTransactions()
        // END 3

        // START 4
        when (printOrVisualise) {
            PrintOrVisualise.Print -> {
                futureTransactions.startWith(transactions).subscribe { transaction ->
                    println("NODE ${transaction.id}")
                    transaction.tx.inputs.forEach { input ->
                        println("EDGE ${input.txhash} ${transaction.id}")
                    }
                }
            }
        // END 4
        // START 5
            PrintOrVisualise.Visualise -> {
                val graph = MultiGraph("transactions")
                transactions.forEach { transaction ->
                    graph.addNode<Node>("${transaction.id}")
                }
                transactions.forEach { transaction ->
                    transaction.tx.inputs.forEach { ref ->
                        graph.addEdge<Edge>("$ref", "${ref.txhash}", "${transaction.id}")
                    }
                }
                futureTransactions.subscribe { transaction ->
                    graph.addNode<Node>("${transaction.id}")
                    transaction.tx.inputs.forEach { ref ->
                        graph.addEdge<Edge>("$ref", "${ref.txhash}", "${transaction.id}")
                    }
                }
                graph.display()
            }
        }
        waitForAllNodesToFinish()
    }

}
// END 5

// START 6
fun generateTransactions(proxy: CordaRPCOps) {
    val (vault, vaultUpdates) = proxy.vaultAndUpdates()
    vaultUpdates.notUsed()
    var ownedQuantity = vault.fold(0L) { sum, state ->
        sum + (state.state.data as Cash.State).amount.quantity
    }
    val issueRef = OpaqueBytes.of(0)
    val (parties, partyUpdates) = proxy.networkMapUpdates()
    partyUpdates.notUsed()
    val notary = parties.first { it.advertisedServices.any { it.info.type.isNotary() } }.notaryIdentity
    val me = proxy.nodeIdentity().legalIdentity
    while (true) {
        Thread.sleep(1000)
        val random = SplittableRandom()
        val n = random.nextDouble()
        if (ownedQuantity > 10000 && n > 0.8) {
            val quantity = Math.abs(random.nextLong()) % 2000
            proxy.startFlow(::CashExitFlow, Amount(quantity, USD), issueRef)
            ownedQuantity -= quantity
        } else if (ownedQuantity > 1000 && n < 0.7) {
            val quantity = Math.abs(random.nextLong() % Math.min(ownedQuantity, 2000))
            proxy.startFlow(::CashPaymentFlow, Amount(quantity, USD), me)
        } else {
            val quantity = Math.abs(random.nextLong() % 1000)
            proxy.startFlow(::CashIssueFlow, Amount(quantity, USD), issueRef, me, notary)
            ownedQuantity += quantity
        }
    }
}
// END 6

// START 7
// Not annotated, so need to whitelist manually.
data class ExampleRPCValue(val foo: String)

// Annotated, so no need to whitelist manually.
@CordaSerializable
data class ExampleRPCValue2(val bar: Int)

class ExampleRPCCordaPluginRegistry : CordaPluginRegistry() {
    override fun customizeSerialization(custom: SerializationCustomization): Boolean {
        // Add classes like this.
        custom.addToWhitelist(ExampleRPCValue::class.java)
        // You should return true, otherwise your plugin will be ignored for registering classes with Kryo.
        return true
    }
}
// END 7
