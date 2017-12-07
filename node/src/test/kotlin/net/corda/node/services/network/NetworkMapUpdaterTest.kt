package net.corda.node.services.network

import com.google.common.jimfs.Configuration.unix
import com.google.common.jimfs.Jimfs
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify
import net.corda.cordform.CordformNode.NODE_INFO_DIRECTORY
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.div
import net.corda.core.internal.uncheckedCast
import net.corda.core.node.NodeInfo
import net.corda.core.serialization.serialize
import net.corda.core.utilities.millis
import net.corda.node.services.api.NetworkMapCacheInternal
import net.corda.nodeapi.internal.SignedNodeInfo
import net.corda.nodeapi.internal.network.NetworkMap
import net.corda.testing.ALICE_NAME
import net.corda.testing.SerializationEnvironmentRule
import net.corda.testing.internal.TestNodeInfoBuilder
import net.corda.testing.internal.createNodeInfoAndSigned
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Rule
import org.junit.Test
import rx.schedulers.TestScheduler
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class NetworkMapUpdaterTest {
    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule(true)

    private val fs = Jimfs.newFileSystem(unix())
    private val baseDir = fs.getPath("/node")
    private val networkMapCache = createMockNetworkMapCache()
    private val nodeInfoMap = ConcurrentHashMap<SecureHash, SignedNodeInfo>()
    private val cacheExpiryMs = 100
    private val networkMapClient = createMockNetworkMapClient()
    private val scheduler = TestScheduler()
    private val networkParametersHash = SecureHash.randomSHA256()
    private val fileWatcher = NodeInfoWatcher(baseDir, scheduler)
    private val updater = NetworkMapUpdater(networkMapCache, fileWatcher, networkMapClient, networkParametersHash)
    private val nodeInfoBuilder = TestNodeInfoBuilder()

    @After
    fun cleanUp() {
        updater.close()
        fs.close()
    }

    @Test
    fun `publish node info`() {
        nodeInfoBuilder.addIdentity(ALICE_NAME)

        val (nodeInfo1, signedNodeInfo1) = nodeInfoBuilder.buildWithSigned()
        val (sameNodeInfoDifferentTime, signedSameNodeInfoDifferentTime) = nodeInfoBuilder.buildWithSigned(serial = System.currentTimeMillis())

        // Publish node info for the first time.
        updater.updateNodeInfo(nodeInfo1) { signedNodeInfo1 }
        // Sleep as publish is asynchronous.
        // TODO: Remove sleep in unit test
        Thread.sleep(2L * cacheExpiryMs)
        verify(networkMapClient, times(1)).publish(any())

        networkMapCache.addNode(nodeInfo1)

        // Publish the same node info, but with different serial.
        updater.updateNodeInfo(sameNodeInfoDifferentTime) { signedSameNodeInfoDifferentTime }
        // TODO: Remove sleep in unit test.
        Thread.sleep(2L * cacheExpiryMs)

        // Same node info should not publish twice
        verify(networkMapClient, times(0)).publish(signedSameNodeInfoDifferentTime)

        val (differentNodeInfo, signedDifferentNodeInfo) = createNodeInfoAndSigned("Bob")

        // Publish different node info.
        updater.updateNodeInfo(differentNodeInfo) { signedDifferentNodeInfo }
        // TODO: Remove sleep in unit test.
        Thread.sleep(200)
        verify(networkMapClient, times(1)).publish(signedDifferentNodeInfo)
    }

    @Test
    fun `process add node updates from network map, with additional node infos from dir`() {
        val (nodeInfo1, signedNodeInfo1) = createNodeInfoAndSigned("Info 1")
        val (nodeInfo2, signedNodeInfo2) = createNodeInfoAndSigned("Info 2")
        val (nodeInfo3, signedNodeInfo3) = createNodeInfoAndSigned("Info 3")
        val (nodeInfo4, signedNodeInfo4) = createNodeInfoAndSigned("Info 4")
        val (fileNodeInfo, signedFileNodeInfo) = createNodeInfoAndSigned("Info from file")

        // Test adding new node.
        networkMapClient.publish(signedNodeInfo1)
        // Not subscribed yet.
        verify(networkMapCache, times(0)).addNode(any())

        updater.subscribeToNetworkMap()
        networkMapClient.publish(signedNodeInfo2)

        // TODO: Remove sleep in unit test.
        Thread.sleep(2L * cacheExpiryMs)
        verify(networkMapCache, times(2)).addNode(any())
        verify(networkMapCache, times(1)).addNode(nodeInfo1)
        verify(networkMapCache, times(1)).addNode(nodeInfo2)

        NodeInfoWatcher.saveToFile(baseDir / NODE_INFO_DIRECTORY, signedFileNodeInfo)
        networkMapClient.publish(signedNodeInfo3)
        networkMapClient.publish(signedNodeInfo4)

        scheduler.advanceTimeBy(10, TimeUnit.SECONDS)
        // TODO: Remove sleep in unit test.
        Thread.sleep(2L * cacheExpiryMs)

        // 4 node info from network map, and 1 from file.
        verify(networkMapCache, times(5)).addNode(any())
        verify(networkMapCache, times(1)).addNode(nodeInfo3)
        verify(networkMapCache, times(1)).addNode(nodeInfo4)
        verify(networkMapCache, times(1)).addNode(fileNodeInfo)
    }

    @Test
    fun `process remove node updates from network map, with additional node infos from dir`() {
        val (nodeInfo1, signedNodeInfo1) = createNodeInfoAndSigned("Info 1")
        val (nodeInfo2, signedNodeInfo2) = createNodeInfoAndSigned("Info 2")
        val (nodeInfo3, signedNodeInfo3) = createNodeInfoAndSigned("Info 3")
        val (nodeInfo4, signedNodeInfo4) = createNodeInfoAndSigned("Info 4")
        val (fileNodeInfo, signedFileNodeInfo) = createNodeInfoAndSigned("Info from file")

        // Add all nodes.
        NodeInfoWatcher.saveToFile(baseDir / NODE_INFO_DIRECTORY, signedFileNodeInfo)
        networkMapClient.publish(signedNodeInfo1)
        networkMapClient.publish(signedNodeInfo2)
        networkMapClient.publish(signedNodeInfo3)
        networkMapClient.publish(signedNodeInfo4)

        updater.subscribeToNetworkMap()
        scheduler.advanceTimeBy(10, TimeUnit.SECONDS)
        // TODO: Remove sleep in unit test.
        Thread.sleep(2L * cacheExpiryMs)

        // 4 node info from network map, and 1 from file.
        assertThat(nodeInfoMap).hasSize(4)
        verify(networkMapCache, times(5)).addNode(any())
        verify(networkMapCache, times(1)).addNode(fileNodeInfo)

        // Test remove node.
        nodeInfoMap.clear()
        // TODO: Remove sleep in unit test.
        Thread.sleep(2L * cacheExpiryMs)
        verify(networkMapCache, times(4)).removeNode(any())
        verify(networkMapCache, times(1)).removeNode(nodeInfo1)
        verify(networkMapCache, times(1)).removeNode(nodeInfo2)
        verify(networkMapCache, times(1)).removeNode(nodeInfo3)
        verify(networkMapCache, times(1)).removeNode(nodeInfo4)

        // Node info from file should not be deleted
        assertThat(networkMapCache.allNodeHashes).containsOnly(fileNodeInfo.serialize().hash)
    }

    @Test
    fun `receive node infos from directory, without a network map`() {
        val (fileNodeInfo, signedFileNodeInfo) = createNodeInfoAndSigned("Info from file")

        // Not subscribed yet.
        verify(networkMapCache, times(0)).addNode(any())

        updater.subscribeToNetworkMap()

        NodeInfoWatcher.saveToFile(baseDir / NODE_INFO_DIRECTORY, signedFileNodeInfo)
        scheduler.advanceTimeBy(10, TimeUnit.SECONDS)

        verify(networkMapCache, times(1)).addNode(any())
        verify(networkMapCache, times(1)).addNode(fileNodeInfo)

        assertThat(networkMapCache.allNodeHashes).containsOnly(fileNodeInfo.serialize().hash)
    }

    private fun createMockNetworkMapClient(): NetworkMapClient {
        return mock {
            on { publish(any()) }.then {
                val signedNodeInfo: SignedNodeInfo = uncheckedCast(it.arguments[0])
                nodeInfoMap.put(signedNodeInfo.verified().serialize().hash, signedNodeInfo)
            }
            on { getNetworkMap() }.then {
                NetworkMapResponse(NetworkMap(nodeInfoMap.keys.toList(), networkParametersHash), cacheExpiryMs.millis)
            }
            on { getNodeInfo(any()) }.then {
                nodeInfoMap[it.arguments[0]]?.verified()
            }
        }
    }

    private fun createMockNetworkMapCache(): NetworkMapCacheInternal {
        return mock {
            val data = ConcurrentHashMap<Party, NodeInfo>()
            on { addNode(any()) }.then {
                val nodeInfo = it.arguments[0] as NodeInfo
                data.put(nodeInfo.legalIdentities[0], nodeInfo)
            }
            on { removeNode(any()) }.then { data.remove((it.arguments[0] as NodeInfo).legalIdentities[0]) }
            on { getNodeByLegalIdentity(any()) }.then { data[it.arguments[0]] }
            on { allNodeHashes }.then { data.values.map { it.serialize().hash } }
            on { getNodeByHash(any()) }.then { mock -> data.values.single { it.serialize().hash == mock.arguments[0] } }
        }
    }

    private fun createNodeInfoAndSigned(org: String): Pair<NodeInfo, SignedNodeInfo> {
        return createNodeInfoAndSigned(CordaX500Name(org, "London", "GB"))
    }
}