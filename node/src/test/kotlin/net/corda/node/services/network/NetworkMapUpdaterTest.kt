package net.corda.node.services.network

import com.google.common.jimfs.Configuration.unix
import com.google.common.jimfs.Jimfs
import com.nhaarman.mockito_kotlin.*
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.generateKeyPair
import net.corda.core.crypto.sign
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.*
import net.corda.core.internal.concurrent.openFuture
import net.corda.core.messaging.ParametersUpdateInfo
import net.corda.core.node.NetworkParameters
import net.corda.core.node.NodeInfo
import net.corda.core.node.services.AttachmentId
import net.corda.core.serialization.serialize
import net.corda.core.utilities.millis
import net.corda.node.VersionInfo
import net.corda.node.services.api.NetworkMapCacheInternal
import net.corda.node.services.config.NetworkParameterAcceptanceSettings
import net.corda.nodeapi.internal.NodeInfoAndSigned
import net.corda.nodeapi.internal.SignedNodeInfo
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.network.NETWORK_PARAMS_UPDATE_FILE_NAME
import net.corda.nodeapi.internal.network.NodeInfoFilesCopier
import net.corda.nodeapi.internal.network.SignedNetworkParameters
import net.corda.nodeapi.internal.network.verifiedNetworkParametersCert
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.*
import net.corda.testing.internal.DEV_ROOT_CA
import net.corda.testing.internal.TestNodeInfoBuilder
import net.corda.testing.internal.createNodeInfoAndSigned
import net.corda.testing.node.internal.MockKeyManagementService
import net.corda.testing.node.internal.MockPublicKeyToOwningIdentityCache
import net.corda.testing.node.internal.network.NetworkMapServer
import net.corda.testing.node.makeTestIdentityService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.hamcrest.collection.IsIterableContainingInAnyOrder
import org.junit.*
import rx.schedulers.TestScheduler
import java.io.IOException
import java.net.URL
import java.security.KeyPair
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NetworkMapUpdaterTest {
    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule(true)

    private val cacheExpiryMs = 1000
    private val privateNetUUID = UUID.randomUUID()
    private val fs = Jimfs.newFileSystem(unix())
    private val baseDir = fs.getPath("/node")
    private val nodeInfoDir = baseDir / NODE_INFO_DIRECTORY
    private val scheduler = TestScheduler()
    private val fileWatcher = NodeInfoWatcher(baseDir, scheduler)
    private val nodeReadyFuture = openFuture<Void?>()
    private val networkMapCache = createMockNetworkMapCache()
    private lateinit var ourKeyPair: KeyPair
    private lateinit var ourNodeInfo: SignedNodeInfo
    private val networkParametersStorage: NetworkParametersStorage = mock()
    private lateinit var server: NetworkMapServer
    private lateinit var networkMapClient: NetworkMapClient
    private var updater: NetworkMapUpdater? = null

    @Before
    fun setUp() {
        ourKeyPair = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
        ourNodeInfo = createNodeInfoAndSigned("Our info", ourKeyPair).signed
        server = NetworkMapServer(cacheExpiryMs.millis)
        val address = server.start()
        networkMapClient = NetworkMapClient(URL("http://$address"),
                VersionInfo(1, "TEST", "TEST", "TEST")).apply { start(DEV_ROOT_CA.certificate) }
    }

    @After
    fun cleanUp() {
        updater?.close()
        fs.close()
        server.close()
    }

    private fun setUpdater(extraNetworkMapKeys: List<UUID> = emptyList(), netMapClient: NetworkMapClient? = networkMapClient) {
        updater = NetworkMapUpdater(networkMapCache, fileWatcher, netMapClient, baseDir, extraNetworkMapKeys, networkParametersStorage)
    }

    private fun startUpdater(ourNodeInfo: SignedNodeInfo = this.ourNodeInfo,
                             networkParameters: NetworkParameters = server.networkParameters,
                             autoAcceptNetworkParameters: Boolean = true,
                             excludedAutoAcceptNetworkParameters: Set<String> = emptySet()) {
        updater!!.start(DEV_ROOT_CA.certificate,
                server.networkParameters.serialize().hash,
                ourNodeInfo,
                networkParameters,
                MockKeyManagementService(makeTestIdentityService(), ourKeyPair, pkToIdCache = MockPublicKeyToOwningIdentityCache()),
                NetworkParameterAcceptanceSettings(autoAcceptNetworkParameters, excludedAutoAcceptNetworkParameters))
    }

    @Test
    fun `process add node updates from network map, with additional node infos from dir`() {
        setUpdater()
        val (nodeInfo1, signedNodeInfo1) = createNodeInfoAndSigned("Info 1")
        val (nodeInfo2, signedNodeInfo2) = createNodeInfoAndSigned("Info 2")
        val (nodeInfo3, signedNodeInfo3) = createNodeInfoAndSigned("Info 3")
        val (nodeInfo4, signedNodeInfo4) = createNodeInfoAndSigned("Info 4")
        val fileNodeInfoAndSigned = createNodeInfoAndSigned("Info from file")

        //Test adding new node.
        networkMapClient.publish(signedNodeInfo1)
        //Not subscribed yet.
        verify(networkMapCache, times(0)).addNode(any())

        startUpdater()
        networkMapClient.publish(signedNodeInfo2)

        assertThat(nodeReadyFuture).isNotDone()
        //TODO: Remove sleep in unit test.
        Thread.sleep(2L * cacheExpiryMs)

        Assert.assertThat(networkMapCache.allNodeHashes, IsIterableContainingInAnyOrder.containsInAnyOrder(signedNodeInfo1.raw.hash, signedNodeInfo2.raw.hash))

        assertThat(nodeReadyFuture).isDone()

        NodeInfoWatcher.saveToFile(nodeInfoDir, fileNodeInfoAndSigned)
        networkMapClient.publish(signedNodeInfo3)
        networkMapClient.publish(signedNodeInfo4)
        advanceTime()
        //TODO: Remove sleep in unit test.
        Thread.sleep(2L * cacheExpiryMs)
        //4 node info from network map, and 1 from file.

        Assert.assertThat(networkMapCache.allNodeHashes, IsIterableContainingInAnyOrder.containsInAnyOrder(
                signedNodeInfo1.raw.hash,
                signedNodeInfo2.raw.hash,
                signedNodeInfo3.raw.hash,
                signedNodeInfo4.raw.hash,
                fileNodeInfoAndSigned.signed.raw.hash
        ))
    }

    @Test
    fun `process remove node updates from network map, with additional node infos from dir`() {
        setUpdater()
        val (nodeInfo1, signedNodeInfo1) = createNodeInfoAndSigned("Info 1")
        val (nodeInfo2, signedNodeInfo2) = createNodeInfoAndSigned("Info 2")
        val (nodeInfo3, signedNodeInfo3) = createNodeInfoAndSigned("Info 3")
        val (nodeInfo4, signedNodeInfo4) = createNodeInfoAndSigned("Info 4")
        val fileNodeInfoAndSigned = createNodeInfoAndSigned("Info from file")

        //Add all nodes.
        NodeInfoWatcher.saveToFile(nodeInfoDir, fileNodeInfoAndSigned)
        networkMapClient.publish(signedNodeInfo1)
        networkMapClient.publish(signedNodeInfo2)
        networkMapClient.publish(signedNodeInfo3)
        networkMapClient.publish(signedNodeInfo4)

        startUpdater()
        advanceTime()
        //TODO: Remove sleep in unit test.
        Thread.sleep(2L * cacheExpiryMs)


        Assert.assertThat(networkMapCache.allNodeHashes, IsIterableContainingInAnyOrder.containsInAnyOrder(
                signedNodeInfo1.raw.hash,
                signedNodeInfo2.raw.hash,
                signedNodeInfo3.raw.hash,
                signedNodeInfo4.raw.hash,
                fileNodeInfoAndSigned.signed.raw.hash
        ))

        //Test remove node.
        listOf(nodeInfo1, nodeInfo2, nodeInfo3, nodeInfo4).forEach {
            server.removeNodeInfo(it)
        }
        //TODO: Remove sleep in unit test.
        Thread.sleep(2L * cacheExpiryMs)
        verify(networkMapCache, times(4)).removeNode(any())
        verify(networkMapCache, times(1)).removeNode(nodeInfo1)
        verify(networkMapCache, times(1)).removeNode(nodeInfo2)
        verify(networkMapCache, times(1)).removeNode(nodeInfo3)
        verify(networkMapCache, times(1)).removeNode(nodeInfo4)

        //Node info from file should not be deleted
        assertThat(networkMapCache.allNodeHashes).containsOnly(fileNodeInfoAndSigned.nodeInfo.serialize().hash)
    }

    @Test
    fun `receive node infos from directory, without a network map`() {
        setUpdater(netMapClient = null)
        val fileNodeInfoAndSigned = createNodeInfoAndSigned("Info from file")

        //Not subscribed yet.
        verify(networkMapCache, times(0)).addNode(any())

        startUpdater()

        NodeInfoWatcher.saveToFile(nodeInfoDir, fileNodeInfoAndSigned)
        assertThat(nodeReadyFuture).isNotDone()
        advanceTime()

        verify(networkMapCache, times(1)).addNode(any())
        verify(networkMapCache, times(1)).addNode(fileNodeInfoAndSigned.nodeInfo)
        assertThat(nodeReadyFuture).isDone()

        assertThat(networkMapCache.allNodeHashes).containsOnly(fileNodeInfoAndSigned.nodeInfo.serialize().hash)
    }

    @Test
    fun `emit new parameters update info on parameters update from network map`() {
        setUpdater()
        val paramsFeed = updater!!.trackParametersUpdate()
        val snapshot = paramsFeed.snapshot
        val updates = paramsFeed.updates.bufferUntilSubscribed()
        assertEquals(null, snapshot)
        val newParameters = testNetworkParameters(epoch = 2, maxMessageSize = 10485761)
        val updateDeadline = Instant.now().plus(1, ChronoUnit.DAYS)
        server.scheduleParametersUpdate(newParameters, "Test update", updateDeadline)
        startUpdater()
        updates.expectEvents(isStrict = false) {
            sequence(
                    expect { update: ParametersUpdateInfo ->
                        assertEquals(update.updateDeadline, updateDeadline)
                        assertEquals(update.description, "Test update")
                        assertEquals(update.hash, newParameters.serialize().hash)
                        assertEquals(update.parameters, newParameters)
                    }
            )
        }
    }

    @Test
    fun `ack network parameters update`() {
        setUpdater()
        val newParameters = testNetworkParameters(epoch = 314, maxMessageSize = 10485761)
        server.scheduleParametersUpdate(newParameters, "Test update", Instant.MIN)
        startUpdater()
        //TODO: Remove sleep in unit test.
        Thread.sleep(2L * cacheExpiryMs)
        val newHash = newParameters.serialize().hash
        val updateFile = baseDir / NETWORK_PARAMS_UPDATE_FILE_NAME
        assert(!updateFile.exists()) { "network parameters should not be auto accepted" }
        updater!!.acceptNewNetworkParameters(newHash) { it.serialize().sign(ourKeyPair) }
        verify(networkParametersStorage, times(1)).saveParameters(any())
        val signedNetworkParams = updateFile.readObject<SignedNetworkParameters>()
        val paramsFromFile = signedNetworkParams.verifiedNetworkParametersCert(DEV_ROOT_CA.certificate)
        assertEquals(newParameters, paramsFromFile)
        assertEquals(newHash, server.latestParametersAccepted(ourKeyPair.public))
    }

    @Test
    fun `network parameters auto-accepted when update only changes whitelist`() {
        setUpdater()
        val newParameters = testNetworkParameters(
                epoch = 314,
                whitelistedContractImplementations = mapOf("key" to listOf(SecureHash.randomSHA256())))
        server.scheduleParametersUpdate(newParameters, "Test update", Instant.MIN)
        startUpdater()
        //TODO: Remove sleep in unit test.
        Thread.sleep(2L * cacheExpiryMs)
        val newHash = newParameters.serialize().hash
        val updateFile = baseDir / NETWORK_PARAMS_UPDATE_FILE_NAME
        val signedNetworkParams = updateFile.readObject<SignedNetworkParameters>()
        val paramsFromFile = signedNetworkParams.verifiedNetworkParametersCert(DEV_ROOT_CA.certificate)
        assertEquals(newParameters, paramsFromFile)
        assertEquals(newHash, server.latestParametersAccepted(ourKeyPair.public))
    }

    @Test
    fun `network parameters not auto-accepted when update only changes whitelist but parameter included in exclusion`() {
        setUpdater()
        val newParameters = testNetworkParameters(
                epoch = 314,
                whitelistedContractImplementations = mapOf("key" to listOf(SecureHash.randomSHA256())))
        server.scheduleParametersUpdate(newParameters, "Test update", Instant.MIN)
        startUpdater(excludedAutoAcceptNetworkParameters = setOf("whitelistedContractImplementations"))
        //TODO: Remove sleep in unit test.
        Thread.sleep(2L * cacheExpiryMs)
        val updateFile = baseDir / NETWORK_PARAMS_UPDATE_FILE_NAME
        assert(!updateFile.exists()) { "network parameters should not be auto accepted" }
    }

    @Test
    fun `network parameters not auto-accepted when update only changes whitelist but auto accept configured to be false`() {
        setUpdater()
        val newParameters = testNetworkParameters(
                epoch = 314,
                whitelistedContractImplementations = mapOf("key" to listOf(SecureHash.randomSHA256())))
        server.scheduleParametersUpdate(newParameters, "Test update", Instant.MIN)
        startUpdater(autoAcceptNetworkParameters = false)
        //TODO: Remove sleep in unit test.
        Thread.sleep(2L * cacheExpiryMs)
        val updateFile = baseDir / NETWORK_PARAMS_UPDATE_FILE_NAME
        assert(!updateFile.exists()) { "network parameters should not be auto accepted" }
    }

    @Test
    fun `fetch nodes from private network`() {
        setUpdater(extraNetworkMapKeys = listOf(privateNetUUID))
        server.addNodesToPrivateNetwork(privateNetUUID, listOf(ALICE_NAME))
        assertThatThrownBy { networkMapClient.getNetworkMap(privateNetUUID).payload.nodeInfoHashes }
                .isInstanceOf(IOException::class.java)
                .hasMessageContaining("Response Code 404")
        val (aliceInfo, signedAliceInfo) = createNodeInfoAndSigned(ALICE_NAME) //Goes to private network map
        val aliceHash = aliceInfo.serialize().hash
        val (bobInfo, signedBobInfo) = createNodeInfoAndSigned(BOB_NAME) //Goes to global network map
        networkMapClient.publish(signedAliceInfo)
        networkMapClient.publish(signedBobInfo)
        assertThat(networkMapClient.getNetworkMap().payload.nodeInfoHashes).containsExactly(bobInfo.serialize().hash)
        assertThat(networkMapClient.getNetworkMap(privateNetUUID).payload.nodeInfoHashes).containsExactly(aliceHash)
        assertEquals(aliceInfo, networkMapClient.getNodeInfo(aliceHash))
    }

    @Test
    fun `remove node from filesystem deletes it from network map cache`() {
        setUpdater(netMapClient = null)
        val fileNodeInfoAndSigned1 = createNodeInfoAndSigned("Info from file 1")
        val fileNodeInfoAndSigned2 = createNodeInfoAndSigned("Info from file 2")
        startUpdater()

        NodeInfoWatcher.saveToFile(nodeInfoDir, fileNodeInfoAndSigned1)
        NodeInfoWatcher.saveToFile(nodeInfoDir, fileNodeInfoAndSigned2)
        advanceTime()
        verify(networkMapCache, times(2)).addNode(any())
        verify(networkMapCache, times(1)).addNode(fileNodeInfoAndSigned1.nodeInfo)
        verify(networkMapCache, times(1)).addNode(fileNodeInfoAndSigned2.nodeInfo)
        assertThat(networkMapCache.allNodeHashes).containsExactlyInAnyOrder(fileNodeInfoAndSigned1.signed.raw.hash, fileNodeInfoAndSigned2.signed.raw.hash)
        //Remove one of the nodes
        val fileName1 = "${NodeInfoFilesCopier.NODE_INFO_FILE_NAME_PREFIX}${fileNodeInfoAndSigned1.nodeInfo.legalIdentities[0].name.serialize().hash}"
        (nodeInfoDir / fileName1).delete()
        advanceTime()
        verify(networkMapCache, times(1)).removeNode(any())
        verify(networkMapCache, times(1)).removeNode(fileNodeInfoAndSigned1.nodeInfo)
        assertThat(networkMapCache.allNodeHashes).containsOnly(fileNodeInfoAndSigned2.signed.raw.hash)
    }

    @Test
    fun `remove node info file, but node in network map server`() {
        setUpdater()
        val nodeInfoBuilder = TestNodeInfoBuilder()
        val (_, key) = nodeInfoBuilder.addLegalIdentity(CordaX500Name("Info", "London", "GB"))
        val (serverNodeInfo, serverSignedNodeInfo) = nodeInfoBuilder.buildWithSigned(1, 1)
        //Construct node for exactly same identity, but different serial. This one will go to additional-node-infos only.
        val localNodeInfo = serverNodeInfo.copy(serial = 17)
        val localSignedNodeInfo = NodeInfoAndSigned(localNodeInfo) { _, serialised ->
            key.sign(serialised.bytes)
        }
        //The one with higher serial goes to additional-node-infos.
        NodeInfoWatcher.saveToFile(nodeInfoDir, localSignedNodeInfo)
        //Publish to network map the one with lower serial.
        networkMapClient.publish(serverSignedNodeInfo)
        startUpdater()
        advanceTime()
        verify(networkMapCache, times(1)).addNode(localNodeInfo)
        Thread.sleep(2L * cacheExpiryMs)
        //Node from file has higher serial than the one from NetworkMapServer
        assertThat(networkMapCache.allNodeHashes).containsOnly(localSignedNodeInfo.signed.raw.hash)
        val fileName = "${NodeInfoFilesCopier.NODE_INFO_FILE_NAME_PREFIX}${localNodeInfo.legalIdentities[0].name.serialize().hash}"
        (nodeInfoDir / fileName).delete()
        advanceTime()
        verify(networkMapCache, times(1)).removeNode(any())
        verify(networkMapCache).removeNode(localNodeInfo)
        Thread.sleep(2L * cacheExpiryMs)
        //Instead of node from file we should have now the one from NetworkMapServer
        assertThat(networkMapCache.allNodeHashes).containsOnly(serverSignedNodeInfo.raw.hash)
    }

    //Test fix for ENT-1882
    //This scenario can happen when signing of network map server is performed much longer after the node joined the network.
    //Network map will advertise hashes without that node.
    @Test
    fun `not remove own node info when it is not in network map yet`() {
        val (myInfo, signedMyInfo) = createNodeInfoAndSigned("My node info")
        val (_, signedOtherInfo) = createNodeInfoAndSigned("Other info")
        setUpdater()
        networkMapCache.addNode(myInfo) //Simulate behaviour on node startup when our node info is added to cache
        networkMapClient.publish(signedOtherInfo)
        startUpdater(ourNodeInfo = signedMyInfo)
        Thread.sleep(2L * cacheExpiryMs)
        verify(networkMapCache, never()).removeNode(myInfo)
        assertThat(server.networkMapHashes()).containsOnly(signedOtherInfo.raw.hash)
        assertThat(networkMapCache.allNodeHashes).containsExactlyInAnyOrder(signedMyInfo.raw.hash, signedOtherInfo.raw.hash)
    }

    @Test
    fun `network map updater removes the correct node info after node info changes`() {
        setUpdater()

        val builder = TestNodeInfoBuilder()

        builder.addLegalIdentity(CordaX500Name("Test", "London", "GB"))

        val signedNodeInfo1 = builder.buildWithSigned(1).signed
        val signedNodeInfo2 = builder.buildWithSigned(2).signed

        //Test adding new node.
        networkMapClient.publish(signedNodeInfo1)
        //Not subscribed yet.
        verify(networkMapCache, times(0)).addNode(any())

        startUpdater()

        //TODO: Remove sleep in unit test.
        Thread.sleep(2L * cacheExpiryMs)
        assert(networkMapCache.allNodeHashes.size == 1)
        networkMapClient.publish(signedNodeInfo2)
        Thread.sleep(2L * cacheExpiryMs)
        advanceTime()

        verify(networkMapCache, times(1)).removeNode(signedNodeInfo1.verified())
        assert(networkMapCache.allNodeHashes.size == 1)
    }

    @Test
    fun `auto acceptance checks are correct`() {
        val packageOwnership = mapOf(
                "com.example1" to generateKeyPair().public,
                "com.example2" to generateKeyPair().public
        )
        val whitelistedContractImplementations = mapOf(
                "example1" to listOf(AttachmentId.randomSHA256()),
                "example2" to listOf(AttachmentId.randomSHA256())
        )

        val netParams = testNetworkParameters()
        val netParamsAutoAcceptable = netParams.copy(
                packageOwnership = packageOwnership,
                whitelistedContractImplementations = whitelistedContractImplementations
        )
        val netParamsNotAutoAcceptable = netParamsAutoAcceptable.copy(maxMessageSize = netParams.maxMessageSize + 1)

        assertTrue(netParams.canAutoAccept(netParams, emptySet()), "auto-acceptable if identical")
        assertTrue(netParams.canAutoAccept(netParams, autoAcceptablePropertyNames), "auto acceptable if identical regardless of exclusions")
        assertTrue(netParams.canAutoAccept(netParamsAutoAcceptable, emptySet()), "auto-acceptable if only AutoAcceptable params have changed")
        assertTrue(netParams.canAutoAccept(netParamsAutoAcceptable, setOf("modifiedTime")), "auto-acceptable if only AutoAcceptable params have changed and excluded param has not changed")
        assertFalse(netParams.canAutoAccept(netParamsNotAutoAcceptable, emptySet()), "not auto-acceptable if non-AutoAcceptable param has changed")
        assertFalse(netParams.canAutoAccept(netParamsAutoAcceptable, setOf("whitelistedContractImplementations")), "not auto-acceptable if only AutoAcceptable params have changed but one has been added to the exclusion set")
    }

    private fun createMockNetworkMapCache(): NetworkMapCacheInternal {

        fun addNodeToMockCache(nodeInfo: NodeInfo, data: ConcurrentHashMap<Party, NodeInfo>) {
            val party = nodeInfo.legalIdentities[0]
            data.compute(party) { _, current ->
                if (current == null || current.serial < nodeInfo.serial) nodeInfo else current
            }
        }

        return mock {
            on { nodeReady }.thenReturn(nodeReadyFuture)
            val data = ConcurrentHashMap<Party, NodeInfo>()
            on { addNode(any()) }.then {
                val nodeInfo = it.arguments[0] as NodeInfo
                val party = nodeInfo.legalIdentities[0]
                data.compute(party) { _, current ->
                    if (current == null || current.serial < nodeInfo.serial) nodeInfo else current
                }
            }

            on { addNodes(any<List<NodeInfo>>()) }.then {
                val nodeInfos = it.arguments[0] as List<NodeInfo>
                nodeInfos.forEach { nodeInfo ->
                    addNodeToMockCache(nodeInfo, data)
                }
            }

            on { removeNode(any()) }.then { data.remove((it.arguments[0] as NodeInfo).legalIdentities[0]) }
            on { getNodeByLegalIdentity(any()) }.then { data[it.arguments[0]] }
            on { allNodeHashes }.then { data.values.map { it.serialize().hash } }
            on { getNodeByHash(any()) }.then { mock -> data.values.singleOrNull { it.serialize().hash == mock.arguments[0] } }
        }
    }

    private fun createNodeInfoAndSigned(org: String, keyPair: KeyPair = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)): NodeInfoAndSigned {
        val serial: Long = 1
        val platformVersion = 1
        val nodeInfoBuilder = TestNodeInfoBuilder()
        nodeInfoBuilder.addLegalIdentity(CordaX500Name(org, "London", "GB"), keyPair, keyPair)
        return nodeInfoBuilder.buildWithSigned(serial, platformVersion)
    }

    private fun advanceTime() {
        scheduler.advanceTimeBy(10, TimeUnit.SECONDS)
    }
}
