package net.corda.docs

import com.google.common.net.HostAndPort
import net.corda.client.CordaRPCClient
import net.corda.core.transactions.SignedTransaction
import net.corda.node.services.config.NodeSSLConfiguration
import org.graphstream.graph.Edge
import org.graphstream.graph.Node
import org.graphstream.graph.implementations.SingleGraph
import rx.Observable
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture

/**
 * This is example code for the Client RPC API tutorial. The START/END comments are important and used by the documentation!
 */

// START 1
enum class PrintOrVisualise {
    Print,
    Visualise
}

fun main(args: Array<String>) {
    if (args.size < 2) {
        throw IllegalArgumentException("Usage: <binary> <node address> [Print|Visualise]")
    }
    val nodeAddress = HostAndPort.fromString(args[0])
    val printOrVisualise = PrintOrVisualise.valueOf(args[1])
    val sslConfig = object : NodeSSLConfiguration {
        override val certificatesPath = Paths.get("build/trader-demo/buyer/certificates")
        override val keyStorePassword = "cordacadevpass"
        override val trustStorePassword = "trustpass"
    }
    // END 1

    // START 2
    val username = System.console().readLine("Enter username: ")
    val password = String(System.console().readPassword("Enter password: "))
    val client = CordaRPCClient(nodeAddress, sslConfig)
    client.start(username, password)
    val proxy = client.proxy()
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
            CompletableFuture<Unit>().get() // block indefinitely
        }
    // END 4
    // START 5
        PrintOrVisualise.Visualise -> {
            val graph = SingleGraph("transactions")
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
}
// END 5
