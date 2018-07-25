package net.corda.node.internal

import net.corda.core.node.NodeInfo

interface StartedNode {
    val info: NodeInfo

    fun dispose()
}
