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
import net.corda.core.crypto.sign
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.*
import net.corda.core.messaging.ParametersUpdateInfo
import net.corda.core.node.NetworkParameters
import net.corda.core.node.NodeInfo
import net.corda.core.serialization.serialize
import net.corda.core.utilities.millis
import net.corda.node.services.api.NetworkMapCacheInternal
import net.corda.nodeapi.internal.NodeInfoAndSigned
import net.corda.nodeapi.internal.SignedNodeInfo
import net.corda.nodeapi.internal.createDevNetworkMapCa
import net.corda.nodeapi.internal.crypto.CertificateAndKeyPair
import net.corda.nodeapi.internal.network.*
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.core.expect
import net.corda.testing.core.expectEvents
import net.corda.testing.core.sequence
import net.corda.testing.internal.DEV_ROOT_CA
import net.corda.testing.internal.TestNodeInfoBuilder
import net.corda.testing.internal.createNodeInfoAndSigned
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Rule
import org.junit.Test
import rx.schedulers.TestScheduler
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals

fun Path.delete(): Unit = Files.delete(this)

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
    private val nodeInfoDir = baseDir / NODE_INFO_DIRECTORY
    private val scheduler = TestScheduler()
    private val networkParametersHash = SecureHash.randomSHA256()
    private val fileWatcher = NodeInfoWatcher(baseDir, scheduler)
    private val updater = NetworkMapUpdater(networkMapCache, fileWatcher, networkMapClient, networkParametersHash, baseDir)
    private var parametersUpdate: ParametersUpdate? = null

    @After
    fun cleanUp() {
        updater.close()
        fs.close()
    }

    @Test
    fun `process add node updates from network map, with additional node infos from dir`() {
        val (nodeInfo1, signedNodeInfo1) = createNodeInfoAndSigned("Info 1")
        val (nodeInfo2, signedNodeInfo2) = createNodeInfoAndSigned("Info 2")
        val (nodeInfo3, signedNodeInfo3) = createNodeInfoAndSigned("Info 3")
        val (nodeInfo4, signedNodeInfo4) = createNodeInfoAndSigned("Info 4")
        val fileNodeInfoAndSigned = createNodeInfoAndSigned("Info from file")

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

        NodeInfoWatcher.saveToFile(nodeInfoDir, fileNodeInfoAndSigned)
        networkMapClient.publish(signedNodeInfo3)
        networkMapClient.publish(signedNodeInfo4)

        scheduler.advanceTimeBy(10, TimeUnit.SECONDS)
        // TODO: Remove sleep in unit test.
        Thread.sleep(2L * cacheExpiryMs)

        // 4 node info from network map, and 1 from file.
        verify(networkMapCache, times(5)).addNode(any())
        verify(networkMapCache, times(1)).addNode(nodeInfo3)
        verify(networkMapCache, times(1)).addNode(nodeInfo4)
        verify(networkMapCache, times(1)).addNode(fileNodeInfoAndSigned.nodeInfo)
    }

    @Test
    fun `process remove node updates from network map, with additional node infos from dir`() {
        val (nodeInfo1, signedNodeInfo1) = createNodeInfoAndSigned("Info 1")
        val (nodeInfo2, signedNodeInfo2) = createNodeInfoAndSigned("Info 2")
        val (nodeInfo3, signedNodeInfo3) = createNodeInfoAndSigned("Info 3")
        val (nodeInfo4, signedNodeInfo4) = createNodeInfoAndSigned("Info 4")
        val fileNodeInfoAndSigned = createNodeInfoAndSigned("Info from file")

        // Add all nodes.
        NodeInfoWatcher.saveToFile(nodeInfoDir, fileNodeInfoAndSigned)
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
        verify(networkMapCache, times(1)).addNode(fileNodeInfoAndSigned.nodeInfo)

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
        assertThat(networkMapCache.allNodeHashes).containsOnly(fileNodeInfoAndSigned.nodeInfo.serialize().hash)
    }

    @Test
    fun `receive node infos from directory, without a network map`() {
        val fileNodeInfoAndSigned = createNodeInfoAndSigned("Info from file")

        // Not subscribed yet.
        verify(networkMapCache, times(0)).addNode(any())

        updater.subscribeToNetworkMap()

        NodeInfoWatcher.saveToFile(nodeInfoDir, fileNodeInfoAndSigned)
        scheduler.advanceTimeBy(10, TimeUnit.SECONDS)

        verify(networkMapCache, times(1)).addNode(any())
        verify(networkMapCache, times(1)).addNode(fileNodeInfoAndSigned.nodeInfo)

        assertThat(networkMapCache.allNodeHashes).containsOnly(fileNodeInfoAndSigned.nodeInfo.serialize().hash)
    }

    @Test
    fun `emit new parameters update info on parameters update from network map`() {
        val paramsFeed = updater.trackParametersUpdate()
        val snapshot = paramsFeed.snapshot
        val updates = paramsFeed.updates.bufferUntilSubscribed()
        assertEquals(null, snapshot)
        val newParameters = testNetworkParameters(epoch = 2)
        val updateDeadline = Instant.now().plus(1, ChronoUnit.DAYS)
        scheduleParametersUpdate(newParameters, "Test update", updateDeadline)
        updater.subscribeToNetworkMap()
        updates.expectEvents(isStrict = false) {
            sequence(
                    expect { update: ParametersUpdateInfo ->
                        assertEquals(update.updateDeadline, updateDeadline)
                        assertEquals(update.description,"Test update")
                        assertEquals(update.hash, newParameters.serialize().hash)
                        assertEquals(update.parameters, newParameters)
                    }
            )
        }
    }

    @Test
    fun `ack network parameters update`() {
        val newParameters = testNetworkParameters(epoch = 314)
        scheduleParametersUpdate(newParameters, "Test update", Instant.MIN)
        updater.subscribeToNetworkMap()
        // TODO: Remove sleep in unit test.
        Thread.sleep(2L * cacheExpiryMs)
        val newHash = newParameters.serialize().hash
        val keyPair = Crypto.generateKeyPair()
        updater.acceptNewNetworkParameters(newHash, { hash -> hash.serialize().sign(keyPair)})
        verify(networkMapClient).ackNetworkParametersUpdate(any())
        val updateFile = baseDir / NETWORK_PARAMS_UPDATE_FILE_NAME
        val signedNetworkParams = updateFile.readObject<SignedNetworkParameters>()
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

    @Test
    fun `remove node from filesystem deletes it from network map cache`() {
        val fileNodeInfoAndSigned1 = createNodeInfoAndSigned("Info from file 1")
        val fileNodeInfoAndSigned2 = createNodeInfoAndSigned("Info from file 2")
        updater.subscribeToNetworkMap()

        NodeInfoWatcher.saveToFile(nodeInfoDir, fileNodeInfoAndSigned1)
        NodeInfoWatcher.saveToFile(nodeInfoDir, fileNodeInfoAndSigned2)
        scheduler.advanceTimeBy(10, TimeUnit.SECONDS)
        verify(networkMapCache, times(2)).addNode(any())
        verify(networkMapCache, times(1)).addNode(fileNodeInfoAndSigned1.nodeInfo)
        verify(networkMapCache, times(1)).addNode(fileNodeInfoAndSigned2.nodeInfo)
        assertThat(networkMapCache.allNodeHashes).containsExactlyInAnyOrder(fileNodeInfoAndSigned1.signed.raw.hash, fileNodeInfoAndSigned2.signed.raw.hash)
        // Remove one of the nodes
        val fileName1 = "${NodeInfoFilesCopier.NODE_INFO_FILE_NAME_PREFIX}${fileNodeInfoAndSigned1.nodeInfo.legalIdentities[0].name.serialize().hash}"
        (nodeInfoDir / fileName1).delete()
        scheduler.advanceTimeBy(10, TimeUnit.SECONDS)
        verify(networkMapCache, times(1)).removeNode(any())
        verify(networkMapCache, times(1)).removeNode(fileNodeInfoAndSigned1.nodeInfo)
        assertThat(networkMapCache.allNodeHashes).containsOnly(fileNodeInfoAndSigned2.signed.raw.hash)
    }

    @Test
    fun `remove node info file, but node in network map server`() {
        val nodeInfoBuilder = TestNodeInfoBuilder()
        val (_, key) = nodeInfoBuilder.addIdentity(CordaX500Name("Info", "London", "GB"))
        val (serverNodeInfo, serverSignedNodeInfo) = nodeInfoBuilder.buildWithSigned(1, 1)
        // Construct node for exactly same identity, but different serial. This one will go to additional-node-infos only.
        val localNodeInfo = serverNodeInfo.copy(serial = 17)
        val localSignedNodeInfo = NodeInfoAndSigned(localNodeInfo) { _, serialised ->
            key.sign(serialised.bytes)
        }
        // The one with higher serial goes to additional-node-infos.
        NodeInfoWatcher.saveToFile(nodeInfoDir, localSignedNodeInfo)
        // Publish to network map the one with lower serial.
        networkMapClient.publish(serverSignedNodeInfo)
        updater.subscribeToNetworkMap()
        scheduler.advanceTimeBy(10, TimeUnit.SECONDS)
        verify(networkMapCache, times(1)).addNode(localNodeInfo)
        Thread.sleep(2L * cacheExpiryMs)
        // Node from file has higher serial than the one from NetworkMapServer
        assertThat(networkMapCache.allNodeHashes).containsOnly(localSignedNodeInfo.signed.raw.hash)
        val fileName = "${NodeInfoFilesCopier.NODE_INFO_FILE_NAME_PREFIX}${localNodeInfo.legalIdentities[0].name.serialize().hash}"
        (nodeInfoDir / fileName).delete()
        scheduler.advanceTimeBy(10, TimeUnit.SECONDS)
        verify(networkMapCache, times(1)).removeNode(any())
        verify(networkMapCache).removeNode(localNodeInfo)
        Thread.sleep(2L * cacheExpiryMs)
        // Instead of node from file we should have now the one from NetworkMapServer
        assertThat(networkMapCache.allNodeHashes).containsOnly(serverSignedNodeInfo.raw.hash)
    }

    private fun createMockNetworkMapCache(): NetworkMapCacheInternal {
        return mock {
            val data = ConcurrentHashMap<Party, NodeInfo>()
            on { addNode(any()) }.then {
                val nodeInfo = it.arguments[0] as NodeInfo
                val party = nodeInfo.legalIdentities[0]
                data.compute(party) { _, current ->
                    if (current == null || current.serial < nodeInfo.serial) nodeInfo else current
                }
            }
            on { removeNode(any()) }.then { data.remove((it.arguments[0] as NodeInfo).legalIdentities[0]) }
            on { getNodeByLegalIdentity(any()) }.then { data[it.arguments[0]] }
            on { allNodeHashes }.then { data.values.map { it.serialize().hash } }
            on { getNodeByHash(any()) }.then { mock -> data.values.singleOrNull { it.serialize().hash == mock.arguments[0] } }
        }
    }

    private fun createNodeInfoAndSigned(org: String): NodeInfoAndSigned {
        return createNodeInfoAndSigned(CordaX500Name(org, "London", "GB"))
    }
}