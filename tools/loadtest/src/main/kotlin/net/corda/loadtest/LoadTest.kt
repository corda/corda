/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.loadtest

import com.google.common.util.concurrent.RateLimiter
import net.corda.client.mock.Generator
import net.corda.core.utilities.loggerFor
import net.corda.core.utilities.toBase58String
import net.corda.testing.driver.PortAllocation
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

private val log = loggerFor<LoadTest<*, *>>()

/**
 * @param T The type of generated object in the load test. This should describe the basic unit of execution, for example
 *     a single transaction to execute.
 * @param S The type of state that describes the state of the load test, for example a hashmap of vaults. Note that this
 *     most probably won't be the actual vault, because that includes [StateRef]s which we cannot predict in advance
 *     in [interpret] due to usage of nonces.
 * @param generate Generator function for [T]s. (e.g. generate payment transactions of random quantity). It takes as
 *     input a number indicating around how many objects it should generate. This need not be the case, but the generator
 *     must generate the objects so that they run consistently when executed in parallel. (e.g. if Alice has 100 USD we
 *     cannot generate two Spend(80 USD) txs, even though individually they are consistent).
 * @param interpret A pure function that applies the generated object to the abstract state. (e.g. subtract/add payment
 *     quantity to relevant vaults)
 * @param execute A function that executes the generated object by executing IO (e.g. make RPC call to execute tx).
 * @param gatherRemoteState A function that assembles the abstract state from the real world (e.g. by getting snapshots
 *     from nodes) and the current simulated state. When run the simulated state will be replaced by the returned value.
 *     It should throw an exception if a divergence from the expected state is detected.
 * @param isConsistent Should be specified if the abstract state tracks non-determinism, in which case it should return
 *     false if the state is not yet consistent, true otherwise. The final convergence check will poll this value on
 *     gathered states.
 *
 * TODO Perhaps an interface would be more idiomatic here
 */
// DOCS START 1
data class LoadTest<T, S>(
        val testName: String,
        val generate: Nodes.(S, Int) -> Generator<List<T>>,
        val interpret: (S, T) -> S,
        val execute: Nodes.(T) -> Unit,
        val gatherRemoteState: Nodes.(S?) -> S,
        val isConsistent: (S) -> Boolean = { true }
) {
// DOCS END 1

    // DOCS START 2
    /**
     * @param parallelism Number of concurrent threads to use to run commands. Note that the actual parallelism may be
     *     further limited by the batches that [generate] returns.
     * @param generateCount Number of total commands to generate. Note that the actual number of generated commands may
     *     exceed this, it is used just for cutoff.
     * @param clearDatabaseBeforeRun Indicates whether the node databases should be cleared before running the test. May
     *     significantly slow down testing as this requires bringing the nodes down and up again.
     * @param gatherFrequency Indicates after how many commands we should gather the remote states.
     * @param disruptionPatterns A list of disruption-lists. The test will be run for each such list, and the test will
     *     be interleaved with the specified disruptions.
     */
    data class RunParameters(
            val parallelism: Int,
            val generateCount: Int,
            val clearDatabaseBeforeRun: Boolean,
            val executionFrequency: Int?,
            val gatherFrequency: Int,
            val disruptionPatterns: List<List<DisruptionSpec>>
    )
    // DOCS END 2

    fun run(nodes: Nodes, parameters: RunParameters, random: SplittableRandom) {
        log.info("Running '$testName' with parameters $parameters")
        if (parameters.clearDatabaseBeforeRun) {
            log.info("Clearing databases as clearDatabaseBeforeRun=true")
            // We need to clear the network map first so that other nodes register fine
            (nodes.simpleNodes + listOf(nodes.notary)).parallelStream().forEach {
                it.clearDb()
            }
        }

        val rateLimiter = parameters.executionFrequency?.let {
            log.info("Execution rate limited to $it per second.")
            RateLimiter.create(it.toDouble())
        }
        val executor = Executors.newFixedThreadPool(parameters.parallelism)

        parameters.disruptionPatterns.forEach { disruptions ->
            log.info("Running test '$testName' with disruptions ${disruptions.map { it.disruption.name }}")
            nodes.withDisruptions(disruptions, random) {
                var state = nodes.gatherRemoteState(null)
                var count = parameters.generateCount
                var countSinceLastCheck = 0

                while (count > 0) {
                    log.info("$count remaining commands, state:\n$state")
                    // Generate commands
                    val commands = nodes.generate(state, parameters.parallelism).generate(random).getOrThrow()
                    require(commands.isNotEmpty())
                    log.info("Generated command batch of size ${commands.size}: $commands")
                    // Interpret commands
                    val newState = commands.fold(state, interpret)
                    // Execute commands
                    executor.invokeAll(
                            commands.map {
                                Callable<Unit> {
                                    rateLimiter?.acquire()
                                    log.info("Executing $it")
                                    try {
                                        nodes.execute(it)
                                    } catch (exception: Throwable) {
                                        val diagnostic = executeDiagnostic(state, newState, it, exception)
                                        log.error(diagnostic)
                                        throw Exception(diagnostic)
                                    }
                                }
                            }
                    )
                    countSinceLastCheck += commands.size
                    if (countSinceLastCheck >= parameters.gatherFrequency) {
                        log.info("Checking consistency...")
                        countSinceLastCheck %= parameters.gatherFrequency
                        state = nodes.gatherRemoteState(newState)
                    } else {
                        state = newState
                    }
                    count -= commands.size
                }
                log.info("Checking final consistency...")
                poll {
                    state = nodes.gatherRemoteState(state)
                    isConsistent(state).apply {
                        if (!this) {
                            log.warn("State is not yet consistent: $state")
                        }
                    }
                }
                log.info("'$testName' done!")
            }
        }
        executor.shutdown()
    }

    companion object {
        fun <T, S> executeDiagnostic(oldState: S, newState: S, failedCommand: T, exception: Throwable): String {
            return "There was a problem executing command $failedCommand." +
                    "\nOld simulated state: $oldState" +
                    "\nNew simulated state(after batch): $newState" +
                    "\nException: $exception"
        }
    }
}

data class Nodes(
        val notary: NodeConnection,
        val simpleNodes: List<NodeConnection>
) {
    val allNodes by lazy { (listOf(notary) + simpleNodes).associateBy { it.info }.values }
}

/**
 * Runs the given [LoadTest]s using the given configuration.
 */
fun runLoadTests(configuration: LoadTestConfiguration, tests: List<Pair<LoadTest<*, *>, LoadTest.RunParameters>>) {
    val seed = configuration.seed ?: Random().nextLong()
    log.info("Using seed $seed")
    val random = SplittableRandom(seed)

    val remoteNodes = configuration.nodeHosts.map { hostname ->
        configuration.let {
            RemoteNode(hostname, it.remoteSystemdServiceName, it.sshUser, it.rpcUser, it.rpcPort, it.remoteNodeDirectory)
        }
    }

    connectToNodes(remoteNodes, PortAllocation.Incremental(configuration.localTunnelStartingPort)) { connections ->
        log.info("Connected to all nodes!")
        val hostNodeMap = ConcurrentHashMap<String, NodeConnection>()
        connections.parallelStream().forEach { connection ->
            log.info("Getting node info of ${connection.remoteNode.hostname}")
            val info = connection.info
            log.info("Got node info of ${connection.remoteNode.hostname}: $info!")
            val otherInfo = connection.proxy.networkMapSnapshot()
            val pubKeysString = otherInfo.map {
                // TODO Rethink, we loose ability for nice showing of NodeInfos.
                "NodeInfo identities set:\n" +
                        it.legalIdentitiesAndCerts.fold("") { acc, elem -> acc + "\n" + elem.name + ": " + elem.owningKey.toBase58String() }
            }.joinToString("\n")
            log.info("${connection.remoteNode.hostname} waiting for network map")
            connection.proxy.waitUntilNetworkReady().get()
            log.info("${connection.remoteNode.hostname} sees\n$pubKeysString")
            hostNodeMap.put(connection.remoteNode.hostname, connection)
        }

        val notaryIdentity = hostNodeMap.values.first().proxy.notaryIdentities().single()
        val notaryNode = hostNodeMap.values.single { notaryIdentity in it.info.legalIdentities }
        val nodes = Nodes(
                notary = notaryNode,
                simpleNodes = hostNodeMap.values.filter { it.info.legalIdentitiesAndCerts.size == 1 } // TODO Fix it with network map.
        )

        tests.forEach { (test, parameters) ->
            test.run(nodes, parameters, random)
        }
    }
}
