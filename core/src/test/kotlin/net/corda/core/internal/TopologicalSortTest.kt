package net.corda.core.internal

import net.corda.client.mock.Generator
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TransactionState
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.SignatureMetadata
import net.corda.core.crypto.TransactionSignature
import net.corda.core.crypto.sign
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.serialization.serialize
import net.corda.core.transactions.CoreTransaction
import net.corda.core.transactions.SignedTransaction
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.core.TestIdentity
import org.junit.Rule
import org.junit.Test
import rx.Observable
import java.util.*

class TopologicalSortTest {
    class DummyTransaction(
            override val id: SecureHash,
            override val inputs: List<StateRef>,
            @Suppress("CanBeParameter") private val numberOfOutputs: Int,
            override val notary: Party
    ) : CoreTransaction() {
        override val outputs: List<TransactionState<ContractState>> = (1..numberOfOutputs).map {
            TransactionState(DummyState(), "", notary)
        }
    }

    class DummyState : ContractState {
        override val participants: List<AbstractParty> = emptyList()
    }

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    @Test
    fun topologicalObservableSort() {
        val testIdentity = TestIdentity.fresh("asd")

        val N = 10
        // generate random tx DAG
        val ids = (1..N).map { SecureHash.sha256("$it") }
        val forwardsGenerators = (0 until ids.size).map { i ->
            Generator.sampleBernoulli(ids.subList(i + 1, ids.size), 0.8).map { outputs -> ids[i] to outputs }
        }
        val transactions = Generator.sequence(forwardsGenerators).map { forwardGraph ->
            val backGraph = forwardGraph.flatMap { it.second.map { output -> it.first to output } }.fold(HashMap<SecureHash, HashSet<SecureHash>>()) { backGraph, edge ->
                backGraph.getOrPut(edge.second) { HashSet() }.add(edge.first)
                backGraph
            }
            val outrefCounts = HashMap<SecureHash, Int>()
            val transactions = ArrayList<SignedTransaction>()
            for ((id, outputs) in forwardGraph) {
                val inputs = (backGraph[id]?.toList() ?: emptyList()).map { inputTxId ->
                    val ref = outrefCounts.compute(inputTxId) { _, count ->
                        if (count == null) {
                            0
                        } else {
                            count + 1
                        }
                    }!!
                    StateRef(inputTxId, ref)
                }
                val tx = DummyTransaction(id, inputs, outputs.size, testIdentity.party)
                val bits = tx.serialize().bytes
                val sig = TransactionSignature(testIdentity.keyPair.private.sign(bits).bytes, testIdentity.publicKey, SignatureMetadata(0, 0))
                val stx = SignedTransaction(tx, listOf(sig))
                transactions.add(stx)
            }
            transactions
        }

        // Swap two random items
        transactions.combine(Generator.intRange(0, N - 1), Generator.intRange(0, N - 2)) { txs, i, _ ->
            val k = 0 // if (i == j) i + 1 else j
            val tmp = txs[i]
            txs[i] = txs[k]
            txs[k] = tmp
            txs
        }

        val random = SplittableRandom()
        for (i in 1..100) {
            val txs = transactions.generateOrFail(random)
            val ordered = Observable.from(txs).topologicalSort(emptyList()).toList().toBlocking().first()
            checkTopologicallyOrdered(ordered)
        }
    }

    private fun checkTopologicallyOrdered(txs: List<SignedTransaction>) {
        val outputs = HashSet<StateRef>()
        for (tx in txs) {
            if (!outputs.containsAll(tx.inputs)) {
                throw IllegalStateException("Transaction $tx's inputs ${tx.inputs} are not satisfied by $outputs")
            }
            outputs.addAll(tx.coreTransaction.outputs.mapIndexed { i, _ -> StateRef(tx.id, i) })
        }
    }
}