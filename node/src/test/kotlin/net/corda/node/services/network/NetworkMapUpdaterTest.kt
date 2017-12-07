package net.corda.node.services.network

import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify
import net.corda.cordform.CordformNode
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.SignedData
import net.corda.core.identity.Party
import net.corda.core.internal.div
import net.corda.core.internal.uncheckedCast
import net.corda.core.node.NodeInfo
import net.corda.core.serialization.serialize
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.millis
import net.corda.node.services.api.NetworkMapCacheInternal
import net.corda.testing.SerializationEnvironmentRule
import org.junit.Rule
import org.junit.Test
import rx.schedulers.TestScheduler
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals

class NetworkMapUpdaterTest {
    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule(true)
    private val jimFs = Jimfs.newFileSystem(Configuration.unix())
    private val baseDir = jimFs.getPath("/node")

    @Test
    fun `publish node info`() {
        val keyPair = Crypto.generateKeyPair()

        val nodeInfo1 = TestNodeInfoFactory.createNodeInfo("Info 1").verified()
        val signedNodeInfo = TestNodeInfoFactory.sign(keyPair, nodeInfo1)

        val sameNodeInfoDifferentTime = nodeInfo1.copy(serial = System.currentTimeMillis())
        val signedSameNodeInfoDifferentTime = TestNodeInfoFactory.sign(keyPair, sameNodeInfoDifferentTime)

        val differentNodeInfo = nodeInfo1.copy(addresses = listOf(NetworkHostAndPort("my.new.host.com", 1000)))
        val signedDifferentNodeInfo = TestNodeInfoFactory.sign(keyPair, differentNodeInfo)

        val networkMapCache = getMockNetworkMapCache()

        val networkMapClient = mock<NetworkMapClient>()

        val scheduler = TestScheduler()
        val fileWatcher = NodeInfoWatcher(baseDir, scheduler)
        val updater = NetworkMapUpdater(networkMapCache, fileWatcher, networkMapClient)

        // Publish node info for the first time.
        updater.updateNodeInfo(nodeInfo1) { signedNodeInfo }
        // Sleep as publish is asynchronous.
        // TODO: Remove sleep in unit test
        Thread.sleep(200)
        verify(networkMapClient, times(1)).publish(any())

        networkMapCache.addNode(nodeInfo1)

        // Publish the same node info, but with different serial.
        updater.updateNodeInfo(sameNodeInfoDifferentTime) { signedSameNodeInfoDifferentTime }
        // TODO: Remove sleep in unit test.
        Thread.sleep(200)

        // Same node info should not publish twice
        verify(networkMapClient, times(0)).publish(signedSameNodeInfoDifferentTime)

        // Publish different node info.
        updater.updateNodeInfo(differentNodeInfo) { signedDifferentNodeInfo }
        // TODO: Remove sleep in unit test.
        Thread.sleep(200)
        verify(networkMapClient, times(1)).publish(signedDifferentNodeInfo)

        updater.close()
    }

    @Test
    fun `process add node updates from network map, with additional node infos from dir`() {
        val nodeInfo1 = TestNodeInfoFactory.createNodeInfo("Info 1")
        val nodeInfo2 = TestNodeInfoFactory.createNodeInfo("Info 2")
        val nodeInfo3 = TestNodeInfoFactory.createNodeInfo("Info 3")
        val nodeInfo4 = TestNodeInfoFactory.createNodeInfo("Info 4")
        val fileNodeInfo = TestNodeInfoFactory.createNodeInfo("Info from file")
        val networkMapCache = getMockNetworkMapCache()

        val nodeInfoMap = ConcurrentHashMap<SecureHash, SignedData<NodeInfo>>()
        val networkMapClient = mock<NetworkMapClient> {
            on { publish(any()) }.then {
                val signedNodeInfo: SignedData<NodeInfo> = uncheckedCast(it.arguments.first())
                nodeInfoMap.put(signedNodeInfo.verified().serialize().hash, signedNodeInfo)
            }
            on { getNetworkMap() }.then { NetworkMapResponse(nodeInfoMap.keys.toList(), 100.millis) }
            on { getNodeInfo(any()) }.then { nodeInfoMap[it.arguments.first()]?.verified() }
        }

        val scheduler = TestScheduler()
        val fileWatcher = NodeInfoWatcher(baseDir, scheduler)
        val updater = NetworkMapUpdater(networkMapCache, fileWatcher, networkMapClient)

        // Test adding new node.
        networkMapClient.publish(nodeInfo1)
        // Not subscribed yet.
        verify(networkMapCache, times(0)).addNode(any())

        updater.subscribeToNetworkMap()
        networkMapClient.publish(nodeInfo2)

        // TODO: Remove sleep in unit test.
        Thread.sleep(200)
        verify(networkMapCache, times(2)).addNode(any())
        verify(networkMapCache, times(1)).addNode(nodeInfo1.verified())
        verify(networkMapCache, times(1)).addNode(nodeInfo2.verified())

        NodeInfoWatcher.saveToFile(baseDir / CordformNode.NODE_INFO_DIRECTORY, fileNodeInfo)
        networkMapClient.publish(nodeInfo3)
        networkMapClient.publish(nodeInfo4)

        scheduler.advanceTimeBy(10, TimeUnit.SECONDS)
        // TODO: Remove sleep in unit test.
        Thread.sleep(200)

        // 4 node info from network map, and 1 from file.
        verify(networkMapCache, times(5)).addNode(any())
        verify(networkMapCache, times(1)).addNode(nodeInfo3.verified())
        verify(networkMapCache, times(1)).addNode(nodeInfo4.verified())
        verify(networkMapCache, times(1)).addNode(fileNodeInfo.verified())

        updater.close()
    }

    @Test
    fun `process remove node updates from network map, with additional node infos from dir`() {
        val nodeInfo1 = TestNodeInfoFactory.createNodeInfo("Info 1")
        val nodeInfo2 = TestNodeInfoFactory.createNodeInfo("Info 2")
        val nodeInfo3 = TestNodeInfoFactory.createNodeInfo("Info 3")
        val nodeInfo4 = TestNodeInfoFactory.createNodeInfo("Info 4")
        val fileNodeInfo = TestNodeInfoFactory.createNodeInfo("Info from file")
        val networkMapCache = getMockNetworkMapCache()

        val nodeInfoMap = ConcurrentHashMap<SecureHash, SignedData<NodeInfo>>()
        val networkMapClient = mock<NetworkMapClient> {
            on { publish(any()) }.then {
                val signedNodeInfo: SignedData<NodeInfo> = uncheckedCast(it.arguments.first())
                nodeInfoMap.put(signedNodeInfo.verified().serialize().hash, signedNodeInfo)
            }
            on { getNetworkMap() }.then { NetworkMapResponse(nodeInfoMap.keys.toList(), 100.millis) }
            on { getNodeInfo(any()) }.then { nodeInfoMap[it.arguments.first()]?.verified() }
        }

        val scheduler = TestScheduler()
        val fileWatcher = NodeInfoWatcher(baseDir, scheduler)
        val updater = NetworkMapUpdater(networkMapCache, fileWatcher, networkMapClient)

        // Add all nodes.
        NodeInfoWatcher.saveToFile(baseDir / CordformNode.NODE_INFO_DIRECTORY, fileNodeInfo)
        networkMapClient.publish(nodeInfo1)
        networkMapClient.publish(nodeInfo2)
        networkMapClient.publish(nodeInfo3)
        networkMapClient.publish(nodeInfo4)

        updater.subscribeToNetworkMap()
        scheduler.advanceTimeBy(10, TimeUnit.SECONDS)
        // TODO: Remove sleep in unit test.
        Thread.sleep(200)

        // 4 node info from network map, and 1 from file.
        assertEquals(4, nodeInfoMap.size)
        verify(networkMapCache, times(5)).addNode(any())
        verify(networkMapCache, times(1)).addNode(fileNodeInfo.verified())

        // Test remove node.
        nodeInfoMap.clear()
        // TODO: Remove sleep in unit test.
        Thread.sleep(200)
        verify(networkMapCache, times(4)).removeNode(any())
        verify(networkMapCache, times(1)).removeNode(nodeInfo1.verified())
        verify(networkMapCache, times(1)).removeNode(nodeInfo2.verified())
        verify(networkMapCache, times(1)).removeNode(nodeInfo3.verified())
        verify(networkMapCache, times(1)).removeNode(nodeInfo4.verified())

        // Node info from file should not be deleted
        assertEquals(1, networkMapCache.allNodeHashes.size)
        assertEquals(fileNodeInfo.verified().serialize().hash, networkMapCache.allNodeHashes.first())

        updater.close()
    }

    @Test
    fun `receive node infos from directory, without a network map`() {
        val fileNodeInfo = TestNodeInfoFactory.createNodeInfo("Info from file")

        val networkMapCache = getMockNetworkMapCache()

        val scheduler = TestScheduler()
        val fileWatcher = NodeInfoWatcher(baseDir, scheduler)
        val updater = NetworkMapUpdater(networkMapCache, fileWatcher, null)

        // Not subscribed yet.
        verify(networkMapCache, times(0)).addNode(any())

        updater.subscribeToNetworkMap()

        NodeInfoWatcher.saveToFile(baseDir / CordformNode.NODE_INFO_DIRECTORY, fileNodeInfo)
        scheduler.advanceTimeBy(10, TimeUnit.SECONDS)

        verify(networkMapCache, times(1)).addNode(any())
        verify(networkMapCache, times(1)).addNode(fileNodeInfo.verified())

        assertEquals(1, networkMapCache.allNodeHashes.size)
        assertEquals(fileNodeInfo.verified().serialize().hash, networkMapCache.allNodeHashes.first())

        updater.close()
    }

    private fun getMockNetworkMapCache() = mock<NetworkMapCacheInternal> {
        val data = ConcurrentHashMap<Party, NodeInfo>()
        on { addNode(any()) }.then {
            val nodeInfo = it.arguments.first() as NodeInfo
            data.put(nodeInfo.legalIdentities.first(), nodeInfo)
        }
        on { removeNode(any()) }.then { data.remove((it.arguments.first() as NodeInfo).legalIdentities.first()) }
        on { getNodeByLegalIdentity(any()) }.then { data[it.arguments.first()] }
        on { allNodeHashes }.then { data.values.map { it.serialize().hash } }
        on { getNodeByHash(any()) }.then { mock -> data.values.single { it.serialize().hash == mock.arguments.first() } }
    }
}