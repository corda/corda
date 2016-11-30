package net.corda.node.visualiser

import net.corda.core.contracts.CommandData
import net.corda.core.contracts.ContractState
import net.corda.core.crypto.SecureHash
import net.corda.testing.LedgerDSL
import net.corda.testing.TestLedgerDSLInterpreter
import net.corda.testing.TestTransactionDSLInterpreter
import org.graphstream.graph.Edge
import org.graphstream.graph.Node
import org.graphstream.graph.implementations.SingleGraph
import kotlin.reflect.memberProperties

@Suppress("unused") // TODO: Re-evaluate by EOY2016 if this code is still useful and if not, delete.
class GraphVisualiser(val dsl: LedgerDSL<TestTransactionDSLInterpreter, TestLedgerDSLInterpreter>) {
    companion object {
        val css = GraphVisualiser::class.java.getResourceAsStream("graph.css").bufferedReader().readText()
    }

    fun convert(): SingleGraph {
        val testLedger: TestLedgerDSLInterpreter = dsl.interpreter
        val graph = createGraph("Transaction group", css)

        // Map all the transactions, including the bogus non-verified ones (with no inputs) to graph nodes.
        for ((txIndex, tx) in (testLedger.transactionsToVerify + testLedger.transactionsUnverified).withIndex()) {
            val txNode = graph.addNode<Node>("tx$txIndex")
            if (tx !in testLedger.transactionsUnverified)
                txNode.label = dsl.interpreter.transactionName(tx.id).let { it ?: "TX[${tx.id.prefixChars()}]" }
            txNode.styleClass = "tx"

            // Now create a vertex for each output state.
            for (outIndex in tx.outputs.indices) {
                val node = graph.addNode<Node>(tx.outRef<ContractState>(outIndex).ref.toString())
                val state = tx.outputs[outIndex]
                node.label = stateToLabel(state.data)
                node.styleClass = stateToCSSClass(state.data) + ",state"
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
        for ((txIndex, tx) in testLedger.transactionsToVerify.withIndex()) {
            for ((inputIndex, ref) in tx.inputs.withIndex()) {
                val edge = graph.addEdge<Edge>("tx$txIndex-in$inputIndex", ref.toString(), "tx$txIndex", true)
                edge.weight = 1.2
            }
        }
        return graph
    }

    private fun stateToLabel(state: ContractState): String {
        return dsl.interpreter.outputToLabel(state) ?: stateToTypeName(state)
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
