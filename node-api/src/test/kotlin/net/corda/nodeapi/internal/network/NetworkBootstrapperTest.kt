package net.corda.nodeapi.internal.network

import com.typesafe.config.ConfigFactory
import net.corda.core.crypto.secureRandomBytes
import net.corda.core.crypto.sha256
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.*
import net.corda.core.node.NetworkParameters
import net.corda.core.node.NodeInfo
import net.corda.core.serialization.serialize
import net.corda.node.services.config.NotaryConfig
import net.corda.nodeapi.internal.DEV_ROOT_CA
import net.corda.nodeapi.internal.NODE_INFO_DIRECTORY
import net.corda.nodeapi.internal.SignedNodeInfo
import net.corda.nodeapi.internal.config.parseAs
import net.corda.nodeapi.internal.config.toConfig
import net.corda.nodeapi.internal.network.NodeInfoFilesCopier.Companion.NODE_INFO_FILE_NAME_PREFIX
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.internal.createNodeInfoAndSigned
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Path
import kotlin.streams.toList

class NetworkBootstrapperTest {
    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    private val fakeEmbeddedCordaJar = fakeFileBytes()

    private val contractsJars = HashMap<Path, TestContractsJar>()

    private val bootstrapper = NetworkBootstrapper(
            initSerEnv = false,
            embeddedCordaJar = fakeEmbeddedCordaJar::inputStream,
            nodeInfosGenerator = { nodeDirs ->
                nodeDirs.map { nodeDir ->
                    val name = nodeDir.fakeNodeConfig.myLegalName
                    val file = nodeDir / "$NODE_INFO_FILE_NAME_PREFIX${name.serialize().hash}"
                    if (!file.exists()) {
                        createNodeInfoAndSigned(name).signed.serialize().open().copyTo(file)
                    }
                    file
                }
            },
            contractsJarConverter = { contractsJars[it]!! }
    )

    private val aliceConfig = FakeNodeConfig(ALICE_NAME)
    private val bobConfig = FakeNodeConfig(BOB_NAME)
    private val notaryConfig = FakeNodeConfig(DUMMY_NOTARY_NAME, NotaryConfig(validating = true))

    private var providedCordaJar: ByteArray? = null
    private val configFiles = HashMap<Path, String>()

    @After
    fun `check config files are preserved`() {
        configFiles.forEach { file, text ->
            assertThat(file).hasContent(text)
        }
    }

    @After
    fun `check provided corda jar is preserved`() {
        if (providedCordaJar == null) {
            // Make sure we clean up if we used the embedded jar
            assertThat(rootDir / "corda.jar").doesNotExist()
        } else {
            // Make sure we don't delete it if it was provided by the user
            assertThat(rootDir / "corda.jar").hasBinaryContent(providedCordaJar)
        }
    }

    @Test
    fun `empty dir`() {
        assertThatThrownBy {
            bootstrap()
        }.hasMessage("No nodes found")
    }

    @Test
    fun `single node conf file`() {
        createNodeConfFile("node1", bobConfig)
        bootstrap()
        val networkParameters = assertBootstrappedNetwork(fakeEmbeddedCordaJar, "node1" to bobConfig)
        networkParameters.run {
            assertThat(epoch).isEqualTo(1)
            assertThat(notaries).isEmpty()
            assertThat(whitelistedContractImplementations).isEmpty()
        }
    }

    @Test
    fun `node conf file and corda jar`() {
        createNodeConfFile("node1", bobConfig)
        val fakeCordaJar = fakeFileBytes(rootDir / "corda.jar")
        bootstrap()
        assertBootstrappedNetwork(fakeCordaJar, "node1" to bobConfig)
    }

    @Test
    fun `single node directory with just node conf file`() {
        createNodeDir("bob", bobConfig)
        bootstrap()
        assertBootstrappedNetwork(fakeEmbeddedCordaJar, "bob" to bobConfig)
    }

    @Test
    fun `single node directory with node conf file and corda jar`() {
        val nodeDir = createNodeDir("bob", bobConfig)
        val fakeCordaJar = fakeFileBytes(nodeDir / "corda.jar")
        bootstrap()
        assertBootstrappedNetwork(fakeCordaJar, "bob" to bobConfig)
    }

    @Test
    fun `single node directory with just corda jar`() {
        val nodeCordaJar = (rootDir / "alice").createDirectories() / "corda.jar"
        val fakeCordaJar = fakeFileBytes(nodeCordaJar)
        assertThatThrownBy {
            bootstrap()
        }.hasMessageStartingWith("Missing node.conf in node directory alice")
        assertThat(nodeCordaJar).hasBinaryContent(fakeCordaJar)  // Make sure the corda.jar is left untouched
    }

    @Test
    fun `two node conf files, one of which is a notary`() {
        createNodeConfFile("alice", aliceConfig)
        createNodeConfFile("notary", notaryConfig)
        bootstrap()
        val networkParameters = assertBootstrappedNetwork(fakeEmbeddedCordaJar, "alice" to aliceConfig, "notary" to notaryConfig)
        networkParameters.assertContainsNotary("notary")
    }

    @Test
    fun `two node conf files with the same legal name`() {
        createNodeConfFile("node1", aliceConfig)
        createNodeConfFile("node2", aliceConfig)
        assertThatThrownBy {
            bootstrap()
        }.hasMessageContaining("Nodes must have unique legal names")
    }

    @Test
    fun `one node directory and one node conf file`() {
        createNodeConfFile("alice", aliceConfig)
        createNodeDir("bob", bobConfig)
        bootstrap()
        assertBootstrappedNetwork(fakeEmbeddedCordaJar, "alice" to aliceConfig, "bob" to bobConfig)
    }

    @Test
    fun `node conf file and CorDapp jar`() {
        createNodeConfFile("alice", aliceConfig)
        val cordappBytes = createFakeCordappJar("sample-app", listOf("contract.class"))
        bootstrap()
        val networkParameters = assertBootstrappedNetwork(fakeEmbeddedCordaJar, "alice" to aliceConfig)
        assertThat(rootDir / "alice" / "cordapps" / "sample-app.jar").hasBinaryContent(cordappBytes)
        assertThat(networkParameters.whitelistedContractImplementations).isEqualTo(mapOf(
                "contract.class" to listOf(cordappBytes.sha256())
        ))
    }

    @Test
    fun `no copy CorDapps`() {
        createNodeConfFile("alice", aliceConfig)
        val cordappBytes = createFakeCordappJar("sample-app", listOf("contract.class"))
        bootstrap(copyCordapps = false)
        val networkParameters = assertBootstrappedNetwork(fakeEmbeddedCordaJar, "alice" to aliceConfig)
        assertThat(rootDir / "alice" / "cordapps" / "sample-app.jar").doesNotExist()
        assertThat(networkParameters.whitelistedContractImplementations).isEqualTo(mapOf(
                "contract.class" to listOf(cordappBytes.sha256())
        ))
    }

    @Test
    fun `add node to existing network`() {
        createNodeConfFile("alice", aliceConfig)
        bootstrap()
        val networkParameters1 = (rootDir / "alice").networkParameters
        createNodeConfFile("bob", bobConfig)
        bootstrap()
        val networkParameters2 = assertBootstrappedNetwork(fakeEmbeddedCordaJar, "alice" to aliceConfig, "bob" to bobConfig)
        assertThat(networkParameters1).isEqualTo(networkParameters2)
    }

    @Test
    fun `add notary to existing network`() {
        createNodeConfFile("alice", aliceConfig)
        bootstrap()
        createNodeConfFile("notary", notaryConfig)
        bootstrap()
        val networkParameters = assertBootstrappedNetwork(fakeEmbeddedCordaJar, "alice" to aliceConfig, "notary" to notaryConfig)
        networkParameters.assertContainsNotary("notary")
        assertThat(networkParameters.epoch).isEqualTo(2)
    }

    private val rootDir get() = tempFolder.root.toPath()

    private fun fakeFileBytes(writeToFile: Path? = null): ByteArray {
        val bytes = secureRandomBytes(128)
        writeToFile?.write(bytes)
        return bytes
    }

    private fun bootstrap(copyCordapps: Boolean = true) {
        providedCordaJar = (rootDir / "corda.jar").let { if (it.exists()) it.readAll() else null }
        bootstrapper.bootstrap(rootDir, copyCordapps)
    }

    private fun createNodeConfFile(nodeDirName: String, config: FakeNodeConfig) {
        writeNodeConfFile(rootDir / "${nodeDirName}_node.conf", config)
    }

    private fun createNodeDir(nodeDirName: String, config: FakeNodeConfig): Path {
        val nodeDir = (rootDir / nodeDirName).createDirectories()
        writeNodeConfFile(nodeDir / "node.conf", config)
        return nodeDir
    }

    private fun writeNodeConfFile(file: Path, config: FakeNodeConfig) {
        val configText = config.toConfig().root().render()
        file.writeText(configText)
        configFiles[file] = configText
    }

    private fun createFakeCordappJar(cordappName: String, contractClassNames: List<String>): ByteArray {
        val cordappJarFile = rootDir / "$cordappName.jar"
        val cordappBytes = fakeFileBytes(cordappJarFile)
        contractsJars[cordappJarFile] = TestContractsJar(cordappBytes.sha256(), contractClassNames)
        return cordappBytes
    }

    private val Path.networkParameters: NetworkParameters get() {
        return (this / NETWORK_PARAMS_FILE_NAME).readObject<SignedNetworkParameters>().verifiedNetworkMapCert(DEV_ROOT_CA.certificate)
    }

    private val Path.nodeInfoFile: Path get() {
        return list { it.filter { it.fileName.toString().startsWith(NODE_INFO_FILE_NAME_PREFIX) }.toList() }.single()
    }

    private val Path.nodeInfo: NodeInfo get() = nodeInfoFile.readObject<SignedNodeInfo>().verified()

    private val Path.fakeNodeConfig: FakeNodeConfig get() {
        return ConfigFactory.parseFile((this / "node.conf").toFile()).parseAs(FakeNodeConfig::class)
    }

    private fun assertBootstrappedNetwork(cordaJar: ByteArray, vararg nodes: Pair<String, FakeNodeConfig>): NetworkParameters {
        val networkParameters = (rootDir / nodes[0].first).networkParameters
        val allNodeInfoFiles = nodes.map { (rootDir / it.first).nodeInfoFile }.associateBy({ it }, { it.readAll() })

        for ((nodeDirName, config) in nodes) {
            val nodeDir = rootDir / nodeDirName
            assertThat(nodeDir / "corda.jar").hasBinaryContent(cordaJar)
            assertThat(nodeDir.fakeNodeConfig).isEqualTo(config)
            assertThat(nodeDir.networkParameters).isEqualTo(networkParameters)
            // Make sure all the nodes have all of each others' node-info files
            allNodeInfoFiles.forEach { nodeInfoFile, bytes ->
                assertThat(nodeDir / NODE_INFO_DIRECTORY / nodeInfoFile.fileName.toString()).hasBinaryContent(bytes)
            }
        }

        return networkParameters
    }

    private fun NetworkParameters.assertContainsNotary(dirName: String) {
        val notaryParty = (rootDir / dirName).nodeInfo.legalIdentities.single()
        assertThat(notaries).hasSize(1)
        notaries[0].run {
            assertThat(validating).isTrue()
            assertThat(identity.name).isEqualTo(notaryParty.name)
            assertThat(identity.owningKey).isEqualTo(notaryParty.owningKey)
        }
    }

    data class FakeNodeConfig(val myLegalName: CordaX500Name, val notary: NotaryConfig? = null)
}
