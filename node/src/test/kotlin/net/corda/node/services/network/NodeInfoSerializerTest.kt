package net.corda.node.services.network

import net.corda.cordform.CordformNode
import net.corda.core.internal.div
import net.corda.core.node.NodeInfo
import net.corda.core.node.services.KeyManagementService
import net.corda.node.services.identity.InMemoryIdentityService
import net.corda.testing.*
import net.corda.testing.node.MockKeyManagementService
import net.corda.testing.node.NodeBasedTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.charset.Charset
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.contentOf

class NodeInfoSerializerTest : NodeBasedTest() {

    @Rule @JvmField var folder = TemporaryFolder()

    lateinit var keyManagementService: KeyManagementService

    // Object under test
    val nodeInfoSerializer = NodeInfoSerializer()

    companion object {
        val nodeInfoFileRegex = Regex("nodeInfo\\-.*")
        val nodeInfo = NodeInfo(listOf(), listOf(getTestPartyAndCertificate(ALICE)), 0, 0)
    }

    @Before
    fun start() {
        val identityService = InMemoryIdentityService(trustRoot = DEV_TRUST_ROOT)
        keyManagementService = MockKeyManagementService(identityService, ALICE_KEY)
    }

    @Test
    fun `save a NodeInfo`() {
        nodeInfoSerializer.saveToFile(folder.root.toPath(), nodeInfo, keyManagementService)

        assertEquals(1, folder.root.list().size)
        val fileName = folder.root.list()[0]
        assertTrue(fileName.matches(nodeInfoFileRegex))
        val file = (folder.root.path / fileName).toFile()
        // Just check that something is written, another tests verifies that the written value can be read back.
        assertThat(contentOf(file)).isNotEmpty()
    }

    @Test
    fun `load an empty Directory`() {
        assertEquals(0, nodeInfoSerializer.loadFromDirectory(folder.root.toPath()).size)
    }

    @Test
    fun `load a non empty Directory`() {
        val nodeInfoFolder = folder.newFolder(CordformNode.NODE_INFO_DIRECTORY)
        nodeInfoSerializer.saveToFile(nodeInfoFolder.toPath(), nodeInfo, keyManagementService)
        val nodeInfos = nodeInfoSerializer.loadFromDirectory(folder.root.toPath())

        assertEquals(1, nodeInfos.size)
        assertEquals(nodeInfo, nodeInfos.first())
    }
}
