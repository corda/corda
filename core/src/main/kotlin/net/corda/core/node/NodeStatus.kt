package net.corda.core.node

import net.corda.core.serialization.CordaSerializable

// TODO : Include uptime, number of queues etc and shows the data in Node explorer.
@CordaSerializable
data class NodeStatus(val stateMachineCount: Int, val freeMemory: Long, val totalMemory: Long)