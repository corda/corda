package net.corda.node.internal

import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.node.VersionInfo
import net.corda.node.services.config.*
import net.corda.node.services.keys.BasicHSMKeyManagementService
import net.corda.nodeapi.internal.cryptoservice.CryptoServiceFactory
import net.corda.nodeapi.internal.cryptoservice.SupportedCryptoServices
import net.corda.nodeapi.internal.cryptoservice.WrappingMode
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.testing.node.MockServices
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Duration

class EnterpriseNodeTest {
    @Rule
    @JvmField
    val tempFolder: TemporaryFolder = TemporaryFolder()

    companion object {
        val nodeName = CordaX500Name("BOC", "London", "GB")
        val versionInfo = VersionInfo.UNKNOWN
    }

    @Test
    fun `Check sanitizing of graphite names`() {
        checkReplacement("abc", "abc")
        checkReplacement("abc.1.2", "abc_1_2")
        checkReplacement("abc", "foo__bar_", "foo (bar)")

    }

    @Test
    fun `when freshIdentitiesConfiguration is missing, key management service is initialised without wrapping`() {
        val nodeConfig = createConfig(null)
        val node = EnterpriseNode(nodeConfig, versionInfo)

        val keyManagementService = node.keyManagementService as BasicHSMKeyManagementService
        assertThat(keyManagementService.wrappingEnabled()).isFalse()
    }

    @Test
    fun `when freshIdentitiesConfiguration contains setting for bouncy castle, key management service is initialised with wrapping`() {
        val freshIdentitiesConfiguration = FreshIdentitiesConfiguration(
                WrappingMode.DEGRADED_WRAPPED,
                CryptoServiceConfiguration(SupportedCryptoServices.BC_SIMPLE, null),
                NodeConfigurationImpl.Defaults.masterKeyAlias)
        val nodeConfig = createConfig(freshIdentitiesConfiguration)
        val node = EnterpriseNode(nodeConfig, versionInfo)

        val keyManagementService = node.keyManagementService as BasicHSMKeyManagementService
        assertThat(keyManagementService.wrappingEnabled()).isTrue()
    }

    @Test
    fun `when freshIdentitiesConfiguration contains wrapping mode that is not supported for the configured crypto service, node exits`() {
        val freshIdentitiesConfiguration = FreshIdentitiesConfiguration(
                WrappingMode.WRAPPED,
                CryptoServiceConfiguration(SupportedCryptoServices.BC_SIMPLE, null),
                NodeConfigurationImpl.Defaults.masterKeyAlias)
        val nodeConfig = createConfig(freshIdentitiesConfiguration)

        assertThatThrownBy { EnterpriseNode(nodeConfig, versionInfo) }
                .isInstanceOf(ConfigurationException::class.java)
                .hasMessageContaining("The crypto service configured for fresh identities (BC_SIMPLE) supports the DEGRADED_WRAPPED mode, but the node is configured to use WRAPPED")
    }

    fun checkReplacement(orgname: String, expectedName: String, custom: String? = null) {
        val nodeConfig = mock<NodeConfiguration>() {
            whenever(it.myLegalName).thenReturn(CordaX500Name(orgname, "London", "GB"))
            whenever(it.graphiteOptions).thenReturn(GraphiteOptions("server", 12345, custom))
        }

        val expectedPattern = if (custom == null) "${expectedName}_London_GB_\\d+_\\d+_\\d+_\\d+" else expectedName
        val createdName = EnterpriseNode.getGraphitePrefix(nodeConfig)
        require(Regex(expectedPattern).matches(createdName), { "${createdName} did not match ${expectedPattern}" })
    }

    private fun createConfig(freshIdentitiesConfiguration: FreshIdentitiesConfiguration?): NodeConfigurationImpl {
        val fakeAddress = NetworkHostAndPort("0.1.2.3", 456)
        return NodeConfigurationImpl(
                baseDirectory = tempFolder.root.toPath(),
                myLegalName = nodeName,
                devMode = true, // Needed for identity cert.
                emailAddress = "",
                p2pAddress = fakeAddress,
                keyStorePassword = "ksp",
                trustStorePassword = "tsp",
                crlCheckSoftFail = true,
                dataSourceProperties = MockServices.makeTestDataSourceProperties(),
                database = DatabaseConfig(),
                rpcUsers = emptyList(),
                verifierType = VerifierType.InMemory,
                flowTimeout = FlowTimeoutConfiguration(timeout = Duration.ZERO, backoffBase = 1.0, maxRestartCount = 1),
                rpcSettings = NodeRpcSettings(address = fakeAddress, adminAddress = null, ssl = null),
                messagingServerAddress = null,
                notary = null,
                enterpriseConfiguration = EnterpriseConfiguration(
                        mutualExclusionConfiguration = MutualExclusionConfiguration(updateInterval = 0, waitInterval = 0)
                ),
                relay = null,
                flowOverrides = FlowOverrideConfig(listOf()),
                cryptoServiceName = SupportedCryptoServices.BC_SIMPLE,
                freshIdentitiesConfiguration = freshIdentitiesConfiguration
        )
    }
}