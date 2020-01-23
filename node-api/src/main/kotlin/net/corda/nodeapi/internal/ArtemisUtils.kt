@file:JvmName("ArtemisUtils")

package net.corda.nodeapi.internal

import org.apache.activemq.artemis.api.core.client.loadbalance.ConnectionLoadBalancingPolicy
import java.nio.file.FileSystems
import java.nio.file.Path

/**
 * Require that the [Path] is on a default file system, and therefore is one that Artemis is willing to use.
 * @throws IllegalArgumentException if the path is not on a default file system.
 */
fun Path.requireOnDefaultFileSystem() {
    require(fileSystem == FileSystems.getDefault()) { "Artemis only uses the default file system" }
}

fun requireMessageSize(messageSize: Int, limit: Int) {
    require(messageSize <= limit) { "Message exceeds maxMessageSize network parameter, maxMessageSize: [$limit], message size: [$messageSize]" }
}

/**
 * Implementation of an Artemis load balancing policy. It does round-robin always starting from the first position, whereas
 * the current [RoundRobinConnectionLoadBalancingPolicy] in Artemis picks the starting position randomly. This can lead to
 * attempting to connect to an inactive broker on the first attempt, which can cause start-up delays depending on what connection
 * settings are used.
 */
class RoundRobinConnectionPolicy : ConnectionLoadBalancingPolicy {
    private var pos = 0

    override fun select(max: Int): Int {
        pos = if (pos >= max) 0 else pos
        return pos++
    }
}
