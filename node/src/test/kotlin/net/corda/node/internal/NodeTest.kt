package net.corda.node.internal

import net.corda.core.createDirectories
import net.corda.core.crypto.commonName
import net.corda.core.div
import net.corda.core.getOrThrow
import net.corda.core.utilities.ALICE
import net.corda.testing.node.NodeBasedTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class NodeTest : NodeBasedTest() {
    @Test
    fun `empty plugins directory`() {
        val baseDirectory = tempFolder.root.toPath() / ALICE.name.commonName
        (baseDirectory / "plugins").createDirectories()
        val node = startNode(ALICE.name).getOrThrow()
        // Make sure we created the plugins dir in the correct place
        assertThat(baseDirectory).isEqualTo(node.configuration.baseDirectory)

    }
}