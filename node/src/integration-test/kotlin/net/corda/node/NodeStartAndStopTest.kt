package net.corda.node

import net.corda.core.utilities.getOrThrow
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.node.internal.NodeBasedTest
import org.junit.Test

class NodeStartAndStopTest : NodeBasedTest() {

    @Test
    fun `start stop start`() {
        val node = startNode(ALICE_NAME)
        node.internals.startupComplete.get()
        node.internals.stop()

        node.internals.start()
        node.internals.startupComplete.getOrThrow()
    }
}