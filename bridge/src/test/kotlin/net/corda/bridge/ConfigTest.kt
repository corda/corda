package net.corda.bridge

import com.typesafe.config.ConfigException
import net.corda.bridge.services.api.FirewallMode
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.div
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.nodeapi.internal.protonwrapper.netty.SocksProxyVersion
import net.corda.testing.core.SerializationEnvironmentRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Paths
import kotlin.test.assertFailsWith

class ConfigTest {
    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    @Rule
    @JvmField
    val serializationEnvironment = SerializationEnvironmentRule()

    @Test
    fun `Load simple config`() {
        val configResource = "/net/corda/bridge/singleprocess/firewall.conf"
        val config = createAndLoadConfigFromResource(tempFolder.root.toPath(), configResource)
        assertEquals(FirewallMode.SenderReceiver, config.firewallMode)
        assertEquals(NetworkHostAndPort("localhost", 11005), config.outboundConfig!!.artemisBrokerAddress)
        assertEquals(NetworkHostAndPort("0.0.0.0", 10005), config.inboundConfig!!.listeningAddress)
        assertNull(config.bridgeInnerConfig)
        assertNull(config.floatOuterConfig)
    }

    @Test
    fun `Load simple bridge config`() {
        val configResource = "/net/corda/bridge/withfloat/bridge/firewall.conf"
        val config = createAndLoadConfigFromResource(tempFolder.root.toPath(), configResource)
        assertEquals(FirewallMode.BridgeInner, config.firewallMode)
        assertEquals(NetworkHostAndPort("localhost", 11005), config.outboundConfig!!.artemisBrokerAddress)
        assertNull(config.inboundConfig)
        assertEquals(listOf(NetworkHostAndPort("localhost", 12005)), config.bridgeInnerConfig!!.floatAddresses)
        assertEquals(CordaX500Name.parse("O=Bank A, L=London, C=GB"), config.bridgeInnerConfig!!.expectedCertificateSubject)
        assertNull(config.floatOuterConfig)
    }

    @Test
    fun `Load simple float config`() {
        val configResource = "/net/corda/bridge/withfloat/float/firewall.conf"
        val config = createAndLoadConfigFromResource(tempFolder.root.toPath(), configResource)
        assertEquals(FirewallMode.FloatOuter, config.firewallMode)
        assertNull(config.outboundConfig)
        assertEquals(NetworkHostAndPort("0.0.0.0", 10005), config.inboundConfig!!.listeningAddress)
        assertNull(config.bridgeInnerConfig)
        assertEquals(NetworkHostAndPort("localhost", 12005), config.floatOuterConfig!!.floatAddress)
        assertEquals(CordaX500Name.parse("O=Bank A, L=London, C=GB"), config.floatOuterConfig!!.expectedCertificateSubject)
    }

    @Test
    fun `Load overridden cert config`() {
        val configResource = "/net/corda/bridge/custombasecerts/firewall.conf"
        val config = createAndLoadConfigFromResource(tempFolder.root.toPath(), configResource)
        assertEquals(Paths.get("customcerts/mysslkeystore.jks"), config.p2pSslOptions.keyStore.path)
        assertEquals(Paths.get("customcerts/mytruststore.jks"), config.p2pSslOptions.trustStore.path)
    }

    @Test
    fun `Load custom inner certificate config`() {
        val configResource = "/net/corda/bridge/separatedwithcustomcerts/bridge/firewall.conf"
        val config = createAndLoadConfigFromResource(tempFolder.root.toPath(), configResource)
        assertEquals(Paths.get("outboundcerts/outboundkeys.jks"), config.outboundConfig!!.customSSLConfiguration!!.keyStore.path)
        assertEquals(Paths.get("outboundcerts/outboundtrust.jks"), config.outboundConfig!!.customSSLConfiguration!!.trustStore.path)
        assertEquals("outboundkeypassword", config.outboundConfig!!.customSSLConfiguration!!.keyStore.storePassword)
        assertEquals("outboundtrustpassword", config.outboundConfig!!.customSSLConfiguration!!.trustStore.storePassword)
        assertNull(config.inboundConfig)
        assertEquals(Paths.get("tunnelcerts/tunnelkeys.jks"), config.bridgeInnerConfig!!.customSSLConfiguration!!.keyStore.path)
        assertEquals(Paths.get("tunnelcerts/tunneltrust.jks"), config.bridgeInnerConfig!!.customSSLConfiguration!!.trustStore.path)
        assertEquals("tunnelkeypassword", config.bridgeInnerConfig!!.customSSLConfiguration!!.keyStore.storePassword)
        assertEquals("tunneltrustpassword", config.bridgeInnerConfig!!.customSSLConfiguration!!.trustStore.storePassword)
        assertNull(config.floatOuterConfig)
    }

    @Test
    fun `Load custom outer certificate config`() {
        val configResource = "/net/corda/bridge/separatedwithcustomcerts/float/firewall.conf"
        val config = createAndLoadConfigFromResource(tempFolder.root.toPath(), configResource)
        assertEquals(Paths.get("inboundcerts/inboundkeys.jks"), config.inboundConfig!!.customSSLConfiguration!!.keyStore.path)
        assertEquals(Paths.get("inboundcerts/inboundtrust.jks"), config.inboundConfig!!.customSSLConfiguration!!.trustStore.path)
        assertEquals("inboundkeypassword", config.inboundConfig!!.customSSLConfiguration!!.keyStore.storePassword)
        assertEquals("inboundtrustpassword", config.inboundConfig!!.customSSLConfiguration!!.trustStore.storePassword)
        assertNull(config.outboundConfig)
        assertEquals(Paths.get("tunnelcerts/tunnelkeys.jks"), config.floatOuterConfig!!.customSSLConfiguration!!.keyStore.path)
        assertEquals(Paths.get("tunnelcerts/tunneltrust.jks"), config.floatOuterConfig!!.customSSLConfiguration!!.trustStore.path)
        assertEquals("tunnelkeypassword", config.floatOuterConfig!!.customSSLConfiguration!!.keyStore.storePassword)
        assertEquals("tunneltrustpassword", config.floatOuterConfig!!.customSSLConfiguration!!.trustStore.storePassword)
        assertNull(config.bridgeInnerConfig)
    }

    @Test
    fun `Load config withsocks support`() {
        val configResource = "/net/corda/bridge/withsocks/firewall.conf"
        val config = createAndLoadConfigFromResource(tempFolder.root.toPath(), configResource)
        assertEquals(SocksProxyVersion.SOCKS5, config.outboundConfig!!.socksProxyConfig!!.version)
        assertEquals(NetworkHostAndPort("localhost", 12345), config.outboundConfig!!.socksProxyConfig!!.proxyAddress)
        assertEquals("proxyUser", config.outboundConfig!!.socksProxyConfig!!.userName)
        assertEquals("pwd", config.outboundConfig!!.socksProxyConfig!!.password)
        val badConfigResource4 = "/net/corda/bridge/withsocks/badconfig/badsocksversion4.conf"
        assertFailsWith<IllegalArgumentException> {
            createAndLoadConfigFromResource(tempFolder.root.toPath() / "4", badConfigResource4)
        }
        val badConfigResource5 = "/net/corda/bridge/withsocks/badconfig/badsocksversion5.conf"
        assertFailsWith<IllegalArgumentException> {
            createAndLoadConfigFromResource(tempFolder.root.toPath() / "5", badConfigResource5)
        }
        val badConfigResource = "/net/corda/bridge/withsocks/badconfig/socks4passwordsillegal.conf"
        assertFailsWith<IllegalArgumentException> {
            createAndLoadConfigFromResource(tempFolder.root.toPath() / "bad", badConfigResource)
        }

        assertEquals(60, config.auditServiceConfiguration.loggingIntervalSec)
    }

    @Test
    fun `Load audit service config`() {
        val configResource = "/net/corda/bridge/withaudit/firewall.conf"
        val config = createAndLoadConfigFromResource(tempFolder.root.toPath(), configResource)
        assertEquals(34, config.auditServiceConfiguration.loggingIntervalSec)

        assertFailsWith<ConfigException.WrongType> {
            createAndLoadConfigFromResource(tempFolder.root.toPath() / "err1", "/net/corda/bridge/withaudit/badconfig/badInterval.conf")
        }
    }
}