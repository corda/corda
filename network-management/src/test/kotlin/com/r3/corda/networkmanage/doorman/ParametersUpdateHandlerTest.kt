package com.r3.corda.networkmanage.doorman

import com.nhaarman.mockito_kotlin.mock
import com.r3.corda.networkmanage.common.persistence.*
import com.r3.corda.networkmanage.toCmd
import com.typesafe.config.*
import net.corda.core.crypto.SecureHash
import net.corda.core.internal.*
import net.corda.core.node.NetworkParameters
import net.corda.core.serialization.serialize
import net.corda.core.utilities.days
import net.corda.nodeapi.internal.createDevNetworkMapCa
import net.corda.nodeapi.internal.network.NetworkMap
import net.corda.nodeapi.internal.network.NetworkMapAndSigned
import net.corda.nodeapi.internal.network.ParametersUpdate
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.internal.createDevIntermediateCaCertPath
import net.corda.testing.internal.signWith
import net.corda.testing.node.MockServices
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Path
import java.time.Instant
import java.util.*

class ParametersUpdateHandlerTest {
    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()
    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    private lateinit var persistence: CordaPersistence
    private lateinit var csrStorage: CertificateSigningRequestStorage
    private lateinit var netParamsUpdateHandler: ParametersUpdateHandler
    private lateinit var networkMapStorage: PersistentNetworkMapStorage

    @Before
    fun init() {
        persistence = configureDatabase(MockServices.makeTestDataSourceProperties(), DatabaseConfig(runMigration = true))
        csrStorage = PersistentCertificateSigningRequestStorage(persistence)
        networkMapStorage = PersistentNetworkMapStorage(persistence)
        netParamsUpdateHandler = ParametersUpdateHandler(csrStorage, networkMapStorage)
    }

    @After
    fun cleanUp() {
        persistence.close()
    }

    @Test
    fun `load parameters from file and check notaries registered`() {
        // Create identities and put them into CertificateSigningStorage
        val (nodeInfo1, keys1) = createValidNodeInfo(csrStorage, CertRole.NODE_CA to "Alice",  CertRole.SERVICE_IDENTITY to "Alice Notary")
        val (nodeInfo2, keys2) = createValidNodeInfo(csrStorage, CertRole.NODE_CA to "Bob Notary")
        val signedNodeInfo1 = nodeInfo1.signWith(keys1)
        val signedNodeInfo2 = nodeInfo2.signWith(keys2)
        val notaryFile1 = tempFolder.root.toPath() / UUID.randomUUID().toString()
        val notaryFile2 = tempFolder.root.toPath() / UUID.randomUUID().toString()
        signedNodeInfo1.serialize().open().copyTo(notaryFile1)
        signedNodeInfo2.serialize().open().copyTo(notaryFile2)
        val configFile = tempFolder.root.toPath() / UUID.randomUUID().toString()
        saveConfig(configFile, listOf(notaryFile1, notaryFile2))
        val cmd = netParamsUpdateHandler.loadParametersFromFile(configFile)
        assertThat(cmd.notaries.map { it.identity }).containsExactly(nodeInfo1.legalIdentities.last(), nodeInfo2.legalIdentities.last())
    }

    @Test
    fun `notaries not registered`() {
        // Create notary NodeInfo with SERVICE_IDENTITY role but don't put in CertificateSigningStorage
        val (nodeInfo, keys) = createValidNodeInfo(mock(), CertRole.NODE_CA to "Alice",  CertRole.SERVICE_IDENTITY to "Alice Notary")
        val signedNodeInfo = nodeInfo.signWith(keys)
        val notaryFile = tempFolder.root.toPath() / UUID.randomUUID().toString()
        signedNodeInfo.serialize().open().copyTo(notaryFile)
        val configFile = tempFolder.root.toPath() / UUID.randomUUID().toString()
        saveConfig(configFile, listOf(notaryFile))
        Assertions.assertThatThrownBy {netParamsUpdateHandler.loadParametersFromFile(configFile)}
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("is not registered with the doorman")
    }

    @Test
    fun `set twice initial parameters`() {
        val testParams = testNetworkParameters()
        val setInitialCmd = testParams.toCmd()
        netParamsUpdateHandler.processNetworkParameters(setInitialCmd)
        val testParams2 = testParams.copy(minimumPlatformVersion = 7)
        netParamsUpdateHandler.processNetworkParameters(testParams2.toCmd())
        assertThat(networkMapStorage.getLatestNetworkParameters()!!.networkParameters.minimumPlatformVersion).isEqualTo(7)
        assertThatThrownBy { netParamsUpdateHandler.processNetworkParameters(
                testParams2.toCmd(parametersUpdate = ParametersUpdateConfig("It needs changing. Now!", updateDeadline = Instant.now())))
        }.hasMessageContaining("'parametersUpdate' specified in network parameters file but there are no network parameters to update")
    }

    @Test
    fun `cancel parameters and then set the same`() {
        loadInitialParams()
        val paramsForUpdate = testNetworkParameters(minimumPlatformVersion = 101)
        val updateDeadline1 = Instant.now() + 10.days
        val updateParamsCmd = paramsForUpdate.toCmd(ParametersUpdateConfig("Update", updateDeadline1))
        netParamsUpdateHandler.processNetworkParameters(updateParamsCmd)
        val cancelCmd = NetworkParametersCmd.CancelUpdate
        netParamsUpdateHandler.processNetworkParameters(cancelCmd)
        // Use just cancelled parameters
        netParamsUpdateHandler.processNetworkParameters(updateParamsCmd)
    }

    @Test
    fun `set parameters update and then change update deadline`() {
        loadInitialParams()
        val paramsForUpdate = testNetworkParameters(minimumPlatformVersion = 101)
        val updateDeadline1 = Instant.now() + 10.days
        val updateParamsCmd = paramsForUpdate.toCmd(ParametersUpdateConfig("Update", updateDeadline1))
        netParamsUpdateHandler.processNetworkParameters(updateParamsCmd)
        // Just change description
        netParamsUpdateHandler.processNetworkParameters(paramsForUpdate.toCmd(ParametersUpdateConfig("Update again", updateDeadline1)))
        // Just change update deadline
        netParamsUpdateHandler.processNetworkParameters(paramsForUpdate.toCmd(ParametersUpdateConfig("Update again", updateDeadline1 + 10.days)))
    }

    @Test
    fun `try to change active update`() {
        val paramsForUpdate = testNetworkParameters(minimumPlatformVersion = 101)
        val updateDeadline = Instant.now() + 10.days
        val description = "Update"
        val paramsUpdate = ParametersUpdate(paramsForUpdate.serialize().hash, description, updateDeadline)
        loadInitialParams(parametersUpdate = paramsUpdate)
        networkMapStorage.saveNetworkParameters(paramsForUpdate, null)
        val updateParamsCmd = paramsForUpdate.toCmd(ParametersUpdateConfig(description, updateDeadline))
        assertThatThrownBy { netParamsUpdateHandler.processNetworkParameters(updateParamsCmd) }
                .hasMessageContaining("New network parameters are the same as the latest ones")
    }

    // Load initial parameters to db and sign them, set them to be in active network map.
    private fun loadInitialParams(params: NetworkParameters = testNetworkParameters(), parametersUpdate: ParametersUpdate? = null) {
        val (rootCa) = createDevIntermediateCaCertPath()
        val networkMapCertAndKeyPair = createDevNetworkMapCa(rootCa)
        val networkParametersSig = networkMapCertAndKeyPair.sign(params).sig
        val networkParametersHash = networkMapStorage.saveNetworkParameters(params, networkParametersSig).hash
        val networkMap = NetworkMap(emptyList(), SecureHash.parse(networkParametersHash), parametersUpdate)
        val networkMapAndSigned = NetworkMapAndSigned(networkMap) { networkMapCertAndKeyPair.sign(networkMap).sig }
        networkMapStorage.saveNewNetworkMap(networkMapAndSigned = networkMapAndSigned)
    }

    private fun saveConfig(configFile: Path, notaryFiles: List<Path>) {
        val config = ConfigValueFactory.fromMap(
                mapOf("minimumPlatformVersion" to 1,
                        "maxMessageSize" to 10485760,
                        "maxTransactionSize" to 10485760,
                        "notaries" to notaryFiles.map { mapOf("notaryNodeInfoFile" to it.toString(), "validating" to true) }
                )

        ).toConfig()
        val configString = config.root().render(ConfigRenderOptions.defaults())
        configFile.writeText(configString)
    }
}
