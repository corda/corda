package net.corda.node.internal

import net.corda.core.createDirectories
import net.corda.core.crypto.commonName
import net.corda.core.div
import net.corda.core.getOrThrow
import net.corda.core.utilities.ALICE
import net.corda.core.utilities.WHITESPACE
import net.corda.testing.node.NodeBasedTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class NodeTest : NodeBasedTest() {
    @Test
    fun `empty plugins directory`() {
        val baseDirectory = baseDirectory(ALICE.name)
        (baseDirectory / "plugins").createDirectories()
        startNode(ALICE.name).getOrThrow()
    }
}
