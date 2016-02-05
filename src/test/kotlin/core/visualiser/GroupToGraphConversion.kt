/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package core.visualiser

import core.CommandData
import core.ContractState
import core.crypto.SecureHash
import core.testutils.TransactionGroupDSL
import org.graphstream.graph.Edge
import org.graphstream.graph.Node
import org.graphstream.graph.implementations.SingleGraph
import kotlin.reflect.memberProperties

class GraphVisualiser(val dsl: TransactionGroupDSL<in ContractState>) {
    companion object {
        val css = GraphVisualiser::class.java.getResourceAsStream("graph.css").bufferedReader().readText()
    }

    fun convert(): SingleGraph {
        val tg = dsl.toTransactionGroup()
        val graph = createGraph("Transaction group", css)

        // Map all the transactions, including the bogus non-verified ones (with no inputs) to graph nodes.
        for ((txIndex, tx) in (tg.transactions + tg.nonVerifiedRoots).withIndex()) {
            val txNode = graph.addNode<Node>("tx$txIndex")
            if (tx !in tg.nonVerifiedRoots)
                txNode.label = dsl.labelForTransaction(tx).let { it ?: "TX ${tx.hash.prefixChars()}" }
            txNode.styleClass = "tx"

            // Now create a vertex for each output state.
            for (outIndex in tx.outStates.indices) {
                val node = graph.addNode<Node>(tx.outRef<ContractState>(outIndex).ref.toString())
                val state = tx.outStates[outIndex]
                node.label = stateToLabel(state)
                node.styleClass = stateToCSSClass(state) + ",state"
                node.setAttribute("state", state)
                val edge = graph.addEdge<Edge>("tx$txIndex-out$outIndex", txNode, node, true)
                edge.weight = 0.7
            }

            // And a vertex for each command.
            for ((index, cmd) in tx.commands.withIndex()) {
                val node = graph.addNode<Node>(SecureHash.randomSHA256().prefixChars())
                node.label = commandToTypeName(cmd.value)
                node.styleClass = "command"
                val edge = graph.addEdge<Edge>("tx$txIndex-cmd-$index", node, txNode)
                edge.weight = 0.4
            }
        }
        // And now all states and transactions were mapped to graph nodes, hook up the input edges.
        for ((txIndex, tx) in tg.transactions.withIndex()) {
            for ((inputIndex, ref) in tx.inStateRefs.withIndex()) {
                val edge = graph.addEdge<Edge>("tx$txIndex-in$inputIndex", ref.toString(), "tx$txIndex", true)
                edge.weight = 1.2
            }
        }
        return graph
    }

    private fun stateToLabel(state: ContractState): String {
        return dsl.labelForState(state) ?: stateToTypeName(state)
    }

    private fun commandToTypeName(state: CommandData) = state.javaClass.canonicalName.removePrefix("contracts.").replace('$', '.')
    private fun stateToTypeName(state: ContractState) = state.javaClass.canonicalName.removePrefix("contracts.").removeSuffix(".State")
    private fun stateToCSSClass(state: ContractState) = stateToTypeName(state).replace('.', '_').toLowerCase()

    fun display() {
        runGraph(convert(), nodeOnClick = { node ->
            val state: ContractState? = node.getAttribute("state")
            if (state != null) {
                val props: List<Pair<String, Any?>> = state.javaClass.kotlin.memberProperties.map { it.name to it.getter.call(state) }
                StateViewer.show(props)
            }
        })
    }
}