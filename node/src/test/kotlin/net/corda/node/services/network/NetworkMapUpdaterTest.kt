package net.corda.node.services.network

import com.google.common.jimfs.Configuration.unix
import com.google.common.jimfs.Jimfs
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify
import net.corda.cordform.CordformNode.NODE_INFO_DIRECTORY
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.*
import net.corda.core.messaging.ParametersUpdateInfo
import net.corda.core.node.NetworkParameters
import net.corda.core.node.NodeInfo
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.utilities.millis
import net.corda.node.services.api.NetworkMapCacheInternal
import net.corda.nodeapi.internal.SignedNodeInfo
import net.corda.nodeapi.internal.createDevNetworkMapCa
import net.corda.nodeapi.internal.crypto.CertificateAndKeyPair
import net.corda.nodeapi.internal.network.NETWORK_PARAMS_UPDATE_FILE_NAME
import net.corda.nodeapi.internal.network.NetworkMap
import net.corda.nodeapi.internal.network.ParametersUpdate
import net.corda.nodeapi.internal.network.verifiedNetworkMapCert
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.*
import net.corda.testing.internal.TestNodeInfoBuilder
import net.corda.testing.internal.createNodeInfoAndSigned
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Rule
import org.junit.Test
import rx.schedulers.TestScheduler
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals

class NetworkMapUpdaterTest {
    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule(true)

    private val fs = Jimfs.newFileSystem(unix())
    private val baseDir = fs.getPath("/node")
    private val networkMapCache = createMockNetworkMapCache()
    private val nodeInfoMap = ConcurrentHashMap<SecureHash, SignedNodeInfo>()
    private val networkParamsMap = HashMap<SecureHash, NetworkParameters>()
    private val networkMapCa: CertificateAndKeyPair = createDevNetworkMapCa()
    private val cacheExpiryMs = 100
    private val networkMapClient = createMockNetworkMapClient()
    private val scheduler = TestScheduler()
    private val networkParametersHash = SecureHash.randomSHA256()
    private val fileWatcher = NodeInfoWatcher(baseDir, scheduler)
    private val updater = NetworkMapUpdater(networkMapCache, fileWatcher, networkMapClient, networkParametersHash, baseDir)
    private val nodeInfoBuilder = TestNodeInfoBuilder()
    private var parametersUpdate: ParametersUpdate? = null

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

    @Test
    fun `emit new parameters update info on parameters update from network map`() {
        val paramsFeed = updater.track()
        val snapshot = paramsFeed.snapshot
        val updates = paramsFeed.updates.bufferUntilSubscribed()
        assertEquals(null, snapshot)
        val newParameters = testNetworkParameters(emptyList(), epoch = 2)
        val updateDeadline = Instant.now().plus(1, ChronoUnit.DAYS)
        scheduleParametersUpdate(newParameters, "Test update", updateDeadline)
        updater.subscribeToNetworkMap()
        updates.expectEvents(isStrict = false) {
            sequence(
                    expect { update: ParametersUpdateInfo ->
                        assertThat(update.updateDeadline == updateDeadline)
                        assertThat(update.description == "Test update")
                        assertThat(update.hash == newParameters.serialize().hash)
                        assertThat(update.parameters == newParameters)
                    }
            )
        }
    }

    @Test
    fun `ack network parameters update`() {
        val newParameters = testNetworkParameters(emptyList(), epoch = 314)
        scheduleParametersUpdate(newParameters, "Test update", Instant.MIN)
        updater.subscribeToNetworkMap()
        // TODO: Remove sleep in unit test.
        Thread.sleep(2L * cacheExpiryMs)
        val newHash = newParameters.serialize().hash
        val keyPair = Crypto.generateKeyPair()
        updater.acceptNewNetworkParameters(newHash, { hash -> hash.serialize().sign(keyPair)})
        verify(networkMapClient).ackNetworkParametersUpdate(any())
        val updateFile = baseDir / NETWORK_PARAMS_UPDATE_FILE_NAME
        val signedNetworkParams = updateFile.readAll().deserialize<SignedDataWithCert<NetworkParameters>>()
        val paramsFromFile = signedNetworkParams.verifiedNetworkMapCert(DEV_ROOT_CA.certificate)
        assertEquals(newParameters, paramsFromFile)
    }

    private fun scheduleParametersUpdate(nextParameters: NetworkParameters, description: String, updateDeadline: Instant) {
        val nextParamsHash = nextParameters.serialize().hash
        networkParamsMap[nextParamsHash] = nextParameters
        parametersUpdate = ParametersUpdate(nextParamsHash, description, updateDeadline)
    }

    private fun createMockNetworkMapClient(): NetworkMapClient {
        return mock {
            on { trustedRoot }.then {
                DEV_ROOT_CA.certificate
            }
            on { publish(any()) }.then {
                val signedNodeInfo: SignedNodeInfo = uncheckedCast(it.arguments[0])
                nodeInfoMap.put(signedNodeInfo.verified().serialize().hash, signedNodeInfo)
            }
            on { getNetworkMap() }.then {
                NetworkMapResponse(NetworkMap(nodeInfoMap.keys.toList(), networkParametersHash, parametersUpdate), cacheExpiryMs.millis)
            }
            on { getNodeInfo(any()) }.then {
                nodeInfoMap[it.arguments[0]]?.verified()
            }
            on { getNetworkParameters(any()) }.then {
                val paramsHash: SecureHash = uncheckedCast(it.arguments[0])
                networkParamsMap[paramsHash]?.signWithCert(networkMapCa.keyPair.private, networkMapCa.certificate)
            }
            on { ackNetworkParametersUpdate(any()) }.then {
                Unit
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