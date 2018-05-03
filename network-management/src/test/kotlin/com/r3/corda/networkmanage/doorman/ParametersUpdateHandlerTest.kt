package com.r3.corda.networkmanage.doorman

import com.nhaarman.mockito_kotlin.mock
import com.r3.corda.networkmanage.common.persistence.*
import com.typesafe.config.*
import net.corda.core.internal.*
import net.corda.core.serialization.serialize
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.internal.signWith
import net.corda.testing.node.MockServices
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Path
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

    @Before
    fun init() {
        persistence = configureDatabase(MockServices.makeTestDataSourceProperties(), DatabaseConfig(runMigration = true))
        csrStorage = PersistentCertificateSigningRequestStorage(persistence)
        netParamsUpdateHandler = ParametersUpdateHandler(csrStorage, mock())
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
