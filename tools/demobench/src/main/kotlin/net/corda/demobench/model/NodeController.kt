package net.corda.demobench.model

import tornadofx.Controller
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class NodeController : Controller() {
    private val FIRST_PORT = 10000

    private val nodes = ConcurrentHashMap<String, NodeConfig>()
    private val port = AtomicInteger(FIRST_PORT)

    fun validate(nodeData: NodeData): NodeConfig? {
        val config = NodeConfig(
            nodeData.legalName.value,
            nodeData.p2pPort.value,
            nodeData.artemisPort.value,
            nodeData.webPort.value
        )

        if (nodes.putIfAbsent(config.key, config) != null) {
            return null
        }

        return config
    }

    val nextPort: Int
        get() { return port.andIncrement }

}
