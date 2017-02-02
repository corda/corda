package net.corda.loadtest.tests

import net.corda.client.mock.Generator
import net.corda.client.mock.pickN
import net.corda.contracts.asset.Cash
import net.corda.core.contracts.Issued
import net.corda.core.contracts.PartyAndReference
import net.corda.core.contracts.USD
import net.corda.core.crypto.AnonymousParty
import net.corda.core.crypto.Party
import net.corda.core.flows.FlowException
import net.corda.core.getOrThrow
import net.corda.core.messaging.startFlow
import net.corda.core.serialization.OpaqueBytes
import net.corda.flows.CashCommand
import net.corda.flows.CashFlow
import net.corda.loadtest.LoadTest
import net.corda.loadtest.NodeHandle
import org.slf4j.LoggerFactory
import java.util.*

private val log = LoggerFactory.getLogger("CrossCash")

/**
 * Cross Cash test generates random issues, spends and exits between nodes and checks whether they succeeded fine. The
 * logic is significantly more complicated than e.g. the Self Issue test because of the non-determinism of how
 * transaction notifications arrive.
 */

data class CrossCashCommand(
        val command: CashCommand,
        val node: NodeHandle
) {
    override fun toString(): String {
        return when (command) {
            is CashCommand.IssueCash -> {
                "ISSUE ${node.info.legalIdentity} -> ${command.recipient} : ${command.amount}"
            }
            is CashCommand.PayCash -> {
                "MOVE ${node.info.legalIdentity} -> ${command.recipient} : ${command.amount}"
            }
            is CashCommand.ExitCash -> {
                "EXIT ${node.info.legalIdentity} : ${command.amount}"
            }
        }
    }
}

/**
 * Map from node to (map from issuer to USD quantity)
 */
data class CrossCashState(
        val nodeVaults: Map<AnonymousParty, Map<AnonymousParty, Long>>,

        // node -> (notifying node -> [(issuer, amount)])
        // This map holds the queues that encode the non-determinism of how tx notifications arrive in the background.
        // Only moves and issues create non-determinism on the receiver side.
        // This together with [nodeVaults] should give the eventually consistent state of all nodes.
        // We check a gathered state against this by searching for interleave patterns that produce the gathered state.
        // If none is found we diverged. If several then we only pop from the queues so that all interleave patterns are
        // still satisfied, as we don't know which one happened in reality. Most of the time we should find a single
        // pattern where we can cut off the queues, thus collapsing the non-determinism. Note that we should do this
        // frequently, otherwise the search blows up. (for queues of size [A,B,C] (A+1)*(B+1)*(C+1) states need to be
        // checked)
        // Alternative: We could track the transactions directly, which would remove the need for searching. However
        // there is a sync issue between the vault's view and the tx db's view about the UTXOs. Furthermore the tracking
        // itself would either require downloading the tx graph on every check or using the Observable stream which
        // requires more concurrent code which is conceptually also more complex than the current design.
        // TODO: Alternative: We may possibly reduce the complexity of the search even further using some form of
        //     knapsack instead of the naive search
        val diffQueues: Map<AnonymousParty, Map<AnonymousParty, List<Pair<AnonymousParty, Long>>>>
) {
    fun copyVaults(): HashMap<AnonymousParty, HashMap<AnonymousParty, Long>> {
        val newNodeVaults = HashMap<AnonymousParty, HashMap<AnonymousParty, Long>>()
        for ((key, value) in nodeVaults) {
            newNodeVaults[key] = HashMap(value)
        }
        return newNodeVaults
    }

    fun copyQueues(): HashMap<AnonymousParty, HashMap<AnonymousParty, ArrayList<Pair<AnonymousParty, Long>>>> {
        val newDiffQueues = HashMap<AnonymousParty, HashMap<AnonymousParty, ArrayList<Pair<AnonymousParty, Long>>>>()
        for ((node, queues) in diffQueues) {
            val newQueues = HashMap<AnonymousParty, ArrayList<Pair<AnonymousParty, Long>>>()
            for ((sender, value) in queues) {
                newQueues[sender] = ArrayList(value)
            }
            newDiffQueues[node] = newQueues
        }
        return newDiffQueues
    }

    override fun toString(): String {
        return "Base vault:\n" +
                nodeVaults.map {
                    val node = it.key
                    "    $node:\n" +
                            it.value.map {
                                val issuer = it.key
                                "        $issuer: ${it.value}"
                            }.joinToString("\n")
                }.joinToString("\n") +
                "\nDiff queues:\n" +
                diffQueues.map {
                    val node = it.key
                    "    $node:\n" +
                            it.value.map {
                                val notifier = it.key
                                "        $notifier: [" + it.value.map {
                                    Issued(PartyAndReference(it.first, OpaqueBytes.of(0)), it.second)
                                }.joinToString(",") + "]"
                            }.joinToString("\n")
                }.joinToString("\n")
    }
}

val crossCashTest = LoadTest<CrossCashCommand, CrossCashState>(
        "Creating Cash transactions randomly",

        generate = { state, parallelism ->
            val nodeMap = simpleNodes.associateBy { it.info.legalIdentity }
            Generator.pickN(parallelism, simpleNodes).bind { nodes ->
                Generator.sequence(
                        nodes.map { node ->
                            val quantities = state.nodeVaults[node.info.legalIdentity] ?: mapOf()
                            val possibleRecipients = nodeMap.keys.toList()
                            val moves = quantities.map {
                                it.value.toDouble() / 1000 to generateMove(it.value, USD, it.key, possibleRecipients)
                            }
                            val exits = quantities.mapNotNull {
                                if (it.key == node.info.legalIdentity) {
                                    it.value.toDouble() / 3000 to generateExit(it.value, USD)
                                } else {
                                    null
                                }
                            }
                            val command = Generator.frequency(
                                    listOf(1.0 to generateIssue(10000, USD, notary.info.notaryIdentity, possibleRecipients)) + moves + exits
                            )
                            command.map { CrossCashCommand(it, nodeMap[node.info.legalIdentity]!!) }
                        }
                )
            }
        },

        interpret = { state, command ->
            when (command.command) {
                is CashCommand.IssueCash -> {
                    val newDiffQueues = state.copyQueues()
                    val originators = newDiffQueues.getOrPut(command.command.recipient, { HashMap() })
                    val issuer = command.node.info.legalIdentity
                    val quantity = command.command.amount.quantity
                    val originator = issuer
                    val queue = originators.getOrPut(originator, { ArrayList() })
                    queue.add(Pair(issuer, quantity))
                    CrossCashState(state.nodeVaults, newDiffQueues)
                }
                is CashCommand.PayCash -> {
                    val newNodeVaults = state.copyVaults()
                    val newDiffQueues = state.copyQueues()
                    val recipientOriginators = newDiffQueues.getOrPut(command.command.recipient, { HashMap() })
                    val senderQuantities = newNodeVaults[command.node.info.legalIdentity]!!
                    val quantity = command.command.amount.quantity
                    val issuer = command.command.amount.token.issuer.party
                    val originator = command.node.info.legalIdentity
                    val senderQuantity = senderQuantities[issuer] ?: throw Exception(
                            "Generated payment of ${command.command.amount} from ${command.node.info.legalIdentity}, " +
                                    "however there is no cash from $issuer!"
                    )
                    if (senderQuantity < quantity) {
                        throw Exception(
                                "Generated payment of ${command.command.amount} from ${command.node.info.legalIdentity}, " +
                                        "however they only have $senderQuantity!"
                        )
                    }
                    if (senderQuantity == quantity) {
                        senderQuantities.remove(issuer)
                    } else {
                        senderQuantities.put(issuer, senderQuantity - quantity)
                    }
                    val recipientQueue = recipientOriginators.getOrPut(originator, { ArrayList() })
                    recipientQueue.add(Pair(issuer, quantity))
                    CrossCashState(newNodeVaults, newDiffQueues)
                }
                is CashCommand.ExitCash -> {
                    val newNodeVaults = state.copyVaults()
                    val issuer = command.node.info.legalIdentity
                    val quantity = command.command.amount.quantity
                    val issuerQuantities = newNodeVaults[issuer]!!
                    val issuerQuantity = issuerQuantities[issuer] ?: throw Exception(
                            "Generated exit of ${command.command.amount} from $issuer, however there is no cash to exit!"
                    )
                    if (issuerQuantity < quantity) {
                        throw Exception(
                                "Generated payment of ${command.command.amount} from $issuer, " +
                                        "however they only have $issuerQuantity!"
                        )
                    }
                    if (issuerQuantity == quantity) {
                        issuerQuantities.remove(issuer)
                    } else {
                        issuerQuantities.put(issuer, issuerQuantity - quantity)
                    }
                    CrossCashState(newNodeVaults, state.diffQueues)
                }
            }
        },

        execute = { command ->
            try {
                val result = command.node.connection.proxy.startFlow(::CashFlow, command.command).returnValue.getOrThrow()
                log.info("Success: $result")
            } catch (e: FlowException) {
                log.error("Failure", e)
            }
        },

        gatherRemoteState = { previousState ->
            log.info("Reifying state...")
            val currentNodeVaults = HashMap<AnonymousParty, HashMap<AnonymousParty, Long>>()
            simpleNodes.forEach {
                val quantities = HashMap<AnonymousParty, Long>()
                val vault = it.connection.proxy.vaultAndUpdates().first
                vault.forEach {
                    val state = it.state.data
                    if (state is Cash.State) {
                        val issuer = state.amount.token.issuer.party
                        quantities.put(issuer, (quantities[issuer] ?: 0L) + state.amount.quantity)
                    }
                }
                currentNodeVaults.put(it.info.legalIdentity, quantities)
            }
            val (consistentVaults, diffQueues) = if (previousState == null) {
                Pair(currentNodeVaults, mapOf<AnonymousParty, Map<AnonymousParty, List<Pair<AnonymousParty, Long>>>>())
            } else {
                log.info("${previousState.diffQueues.values.sumBy { it.values.sumBy { it.size } }} txs in limbo")
                val newDiffQueues = previousState.copyQueues()
                val newConsistentVault = previousState.copyVaults()
                previousState.diffQueues.forEach { entry ->
                    val (node, queues) = entry
                    val searchedState = currentNodeVaults[node]
                    val baseState = previousState.nodeVaults[node]
                    if (searchedState != null) {
                        val matches = searchForState(searchedState, baseState ?: mapOf(), queues)
                        if (matches.isEmpty()) {
                            log.warn(
                                    "Divergence detected, the remote state doesn't match any of our possible predictions." +
                                            "\nPredicted state/queues:\n$previousState" +
                                            "\nActual gathered state:\n${CrossCashState(currentNodeVaults, mapOf())}"
                            )
                            // TODO We should terminate here with an exception, we cannot carry on as we have an inconsistent model. We carry on currently because we always diverge due to notarisation failures
                            return@LoadTest CrossCashState(currentNodeVaults, mapOf<AnonymousParty, Map<AnonymousParty, List<Pair<AnonymousParty, Long>>>>())
                        }
                        if (matches.size > 1) {
                            log.warn("Multiple predicted states match the remote state")
                        }
                        val minimumMatches = matches.fold<Map<AnonymousParty, Int>, HashMap<AnonymousParty, Int>?>(null) { minimum, next ->
                            if (minimum == null) {
                                HashMap(next)
                            } else {
                                next.forEach { entry ->
                                    minimum.merge(entry.key, entry.value, Math::min)
                                }
                                minimum
                            }
                        }!!
                        // Now compute the new consistent state
                        val newNodeDiffQueues = newDiffQueues[node]
                        val newNodeVault = newConsistentVault.getOrPut(node) { HashMap() }
                        minimumMatches.forEach { originator, consumedTxs ->
                            if (consumedTxs > 0) {
                                newNodeDiffQueues!!
                                for (i in 0..consumedTxs - 1) {
                                    val (issuer, quantity) = newNodeDiffQueues[originator]!!.removeAt(0)
                                    newNodeVault.put(issuer, (newNodeVault[issuer] ?: 0L) + quantity)
                                }
                            }
                        }
                    } else {
                        require(baseState == null)
                    }
                }
                Pair(newConsistentVault, newDiffQueues)
            }
            CrossCashState(consistentVaults, diffQueues)
        },

        isConsistent = { state ->
            state.diffQueues.all { it.value.all { it.value.isEmpty() } }
        }
)

/**
 * @param searchedState The state to search for
 * @param baseState The consistent base knowledge
 * @param diffQueues The queues to interleave
 * @return List of (node -> number of txs consumed) maps, each of which results in [searchedState].
 */
private fun <A> searchForState(
        searchedState: Map<A, Long>,
        baseState: Map<A, Long>,
        diffQueues: Map<A, List<Pair<A, Long>>>
): List<Map<A, Int>> {

    val diffQueuesList = diffQueues.toList()
    fun searchForStateHelper(state: Map<A, Long>, diffIx: Int, consumedTxs: HashMap<A, Int>, matched: ArrayList<Map<A, Int>>) {
        if (diffIx >= diffQueuesList.size) {
            if (state == searchedState) {
                matched.add(HashMap(consumedTxs))
            }
        } else {
            val (originator, queue) = diffQueuesList[diffIx]
            consumedTxs[originator] = 0
            searchForStateHelper(state, diffIx + 1, consumedTxs, matched)
            var currentState = state
            queue.forEachIndexed { index, pair ->
                consumedTxs[originator] = index + 1
                // Prune search if we exceeded the searched quantity anyway
                currentState = applyDiff(pair.first, pair.second, currentState, searchedState) ?: return
                searchForStateHelper(currentState, diffIx + 1, consumedTxs, matched)
            }
        }
    }

    val matched = ArrayList<Map<A, Int>>()
    searchForStateHelper(baseState, 0, HashMap(), matched)
    return matched
}

// Returns null if we exceeded the searched quantity.
private fun <A> applyDiff(
        issuer: A,
        quantity: Long,
        state: Map<A, Long>,
        searchedState: Map<A, Long>
): Map<A, Long>? {
    val newState = HashMap(state)
    val newQuantity = (newState[issuer] ?: 0L) + quantity
    val searchedQuantity = searchedState[issuer]
    if (searchedQuantity == null || newQuantity > searchedQuantity) {
        return null
    }
    newState.put(issuer, newQuantity)
    return newState
}
