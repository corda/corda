package net.corda.loadtest

import net.corda.client.mock.Generator
import net.corda.core.utilities.loggerFor
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.Executors

private val log = loggerFor<Disruption>()

/**
 * A [Disruption] puts strain on the passed in node in some way. Each disruption runs in its own thread in a tight loop
 * and may be interrupted at will, plan your cleanup accordingly so that nodes are left in a usable state.
 */
// DOCS START 1
data class Disruption(
        val name: String,
        val disrupt: (NodeConnection, SplittableRandom) -> Unit
)

data class DisruptionSpec(
        val nodeFilter: (NodeConnection) -> Boolean,
        val disruption: Disruption,
        val noDisruptionWindowMs: LongRange
)
// DOCS END 1

/**
 * TODO Further Disruptions to add:
 *   * Strain on filesystem.
 *     * Keep clearing fs caches?
 *     * Keep allocating lots of fds.
 *     * Exhaust disk space
 *     * Delete non-DB stored files like attachments.
 *   * Strain on DB.
 *     * In theory starting flows that hang in a tight loop should do the job.
 *     * We could also mess with the database directly.
 *   * Strain on ActiveMQ.
 *     * Requires exposing of the Artemis client in [NodeConnection].
 *     * Fuzz inputs.
 *     * Randomly block queues.
 *     * Randomly duplicate messages, perhaps to other queues even.
 */
val isNotary = { node: NodeConnection ->
    val notaries = node.proxy.notaryIdentities()
    node.info.legalIdentities.any { it in notaries }
}

fun <A> ((A) -> Boolean).or(other: (A) -> Boolean): (A) -> Boolean = { this(it) || other(it) }

fun hang(hangIntervalRange: LongRange) = Disruption("Hang randomly") { node, random ->
    val hangIntervalMs = Generator.longRange(hangIntervalRange).generateOrFail(random)
    node.doWhileSigStopped { Thread.sleep(hangIntervalMs) }
}

val restart = Disruption("Restart randomly") { connection, _ ->
    connection.restartNode()
    connection.waitUntilUp()
}

val kill = Disruption("Kill randomly") { node, _ ->
    node.kill()
}

val deleteDb = Disruption("Delete persistence database without restart") { connection, _ ->
    connection.runShellCommandGetOutput("sudo rm ${connection.remoteNode.nodeDirectory}/persistence.mv.db").getResultOrThrow()
}

// DOCS START 2
fun strainCpu(parallelism: Int, durationSeconds: Int) = Disruption("Put strain on cpu") { connection, _ ->
    val shell = "for c in {1..$parallelism} ; do openssl enc -aes-128-cbc -in /dev/urandom -pass pass: -e > /dev/null & done && JOBS=\$(jobs -p) && (sleep $durationSeconds && kill \$JOBS) & wait"
    connection.runShellCommandGetOutput(shell).getResultOrThrow()
}
// DOCS END 2

fun <A> Nodes.withDisruptions(disruptions: List<DisruptionSpec>, mainRandom: SplittableRandom, action: () -> A): A {
    val executor = Executors.newCachedThreadPool()
    disruptions.map { disruption ->
        val random = mainRandom.split()
        val relevantNodes = allNodes.filter(disruption.nodeFilter)
        executor.submit {
            while (true) {
                val noDisruptionIntervalMs = Generator.longRange(disruption.noDisruptionWindowMs).generateOrFail(random)
                Thread.sleep(noDisruptionIntervalMs)
                val randomNodes = Generator.sampleBernoulli(relevantNodes).generateOrFail(random)
                val nodes = if (randomNodes.isEmpty()) {
                    listOf(Generator.pickOne(relevantNodes).generateOrFail(random))
                } else {
                    randomNodes
                }
                executor.invokeAll(nodes.map { node ->
                    val nodeRandom = random.split()
                    Callable {
                        log.info("Disrupting ${node.remoteNode.hostname} with '${disruption.disruption.name}'")
                        disruption.disruption.disrupt(node, nodeRandom)
                    }
                })
            }
        }
    }
    try {
        return action()
    } finally {
        executor.shutdownNow()
    }
}
