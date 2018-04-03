/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.bridge

import net.corda.bridge.services.api.BridgeMode
import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.testing.core.SerializationEnvironmentRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Paths

class ConfigTest {
    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    @Rule
    @JvmField
    val serializationEnvironment = SerializationEnvironmentRule()

    @Test
    fun `Load simple config`() {
        val configResource = "/net/corda/bridge/singleprocess/bridge.conf"
        val config = createAndLoadConfigFromResource(tempFolder.root.toPath(), configResource)
        assertEquals(BridgeMode.SenderReceiver, config.bridgeMode)
        assertEquals(NetworkHostAndPort("localhost", 11005), config.outboundConfig!!.artemisBrokerAddress)
        assertEquals(NetworkHostAndPort("0.0.0.0", 10005), config.inboundConfig!!.listeningAddress)
        assertNull(config.floatInnerConfig)
        assertNull(config.floatOuterConfig)
    }

    @Test
    fun `Load simple bridge config`() {
        val configResource = "/net/corda/bridge/withfloat/bridge/bridge.conf"
        val config = createAndLoadConfigFromResource(tempFolder.root.toPath(), configResource)
        assertEquals(BridgeMode.FloatInner, config.bridgeMode)
        assertEquals(NetworkHostAndPort("localhost", 11005), config.outboundConfig!!.artemisBrokerAddress)
        assertNull(config.inboundConfig)
        assertEquals(listOf(NetworkHostAndPort("localhost", 12005)), config.floatInnerConfig!!.floatAddresses)
        assertEquals(CordaX500Name.parse("O=Bank A, L=London, C=GB"), config.floatInnerConfig!!.expectedCertificateSubject)
        assertNull(config.floatOuterConfig)
    }

    @Test
    fun `Load simple float config`() {
        val configResource = "/net/corda/bridge/withfloat/float/bridge.conf"
        val config = createAndLoadConfigFromResource(tempFolder.root.toPath(), configResource)
        assertEquals(BridgeMode.FloatOuter, config.bridgeMode)
        assertNull(config.outboundConfig)
        assertEquals(NetworkHostAndPort("0.0.0.0", 10005), config.inboundConfig!!.listeningAddress)
        assertNull(config.floatInnerConfig)
        assertEquals(NetworkHostAndPort("localhost", 12005), config.floatOuterConfig!!.floatAddress)
        assertEquals(CordaX500Name.parse("O=Bank A, L=London, C=GB"), config.floatOuterConfig!!.expectedCertificateSubject)
    }

    @Test
    fun `Load overridden cert config`() {
        val configResource = "/net/corda/bridge/custombasecerts/bridge.conf"
        val config = createAndLoadConfigFromResource(tempFolder.root.toPath(), configResource)
        assertEquals(Paths.get("customcerts/mysslkeystore.jks"), config.sslKeystore)
        assertEquals(Paths.get("customcerts/mytruststore.jks"), config.trustStoreFile)
    }

    @Test
    fun `Load custom inner certificate config`() {
        val configResource = "/net/corda/bridge/separatedwithcustomcerts/bridge/bridge.conf"
        val config = createAndLoadConfigFromResource(tempFolder.root.toPath(), configResource)
        assertEquals(Paths.get("outboundcerts/outboundkeys.jks"), config.outboundConfig!!.customSSLConfiguration!!.sslKeystore)
        assertEquals(Paths.get("outboundcerts/outboundtrust.jks"), config.outboundConfig!!.customSSLConfiguration!!.trustStoreFile)
        assertEquals("outboundkeypassword", config.outboundConfig!!.customSSLConfiguration!!.keyStorePassword)
        assertEquals("outboundtrustpassword", config.outboundConfig!!.customSSLConfiguration!!.trustStorePassword)
        assertNull(config.inboundConfig)
        assertEquals(Paths.get("tunnelcerts/tunnelkeys.jks"), config.floatInnerConfig!!.customSSLConfiguration!!.sslKeystore)
        assertEquals(Paths.get("tunnelcerts/tunneltrust.jks"), config.floatInnerConfig!!.customSSLConfiguration!!.trustStoreFile)
        assertEquals("tunnelkeypassword", config.floatInnerConfig!!.customSSLConfiguration!!.keyStorePassword)
        assertEquals("tunneltrustpassword", config.floatInnerConfig!!.customSSLConfiguration!!.trustStorePassword)
        assertNull(config.floatOuterConfig)
    }

    @Test
    fun `Load custom outer certificate config`() {
        val configResource = "/net/corda/bridge/separatedwithcustomcerts/float/bridge.conf"
        val config = createAndLoadConfigFromResource(tempFolder.root.toPath(), configResource)
        assertEquals(Paths.get("inboundcerts/inboundkeys.jks"), config.inboundConfig!!.customSSLConfiguration!!.sslKeystore)
        assertEquals(Paths.get("inboundcerts/inboundtrust.jks"), config.inboundConfig!!.customSSLConfiguration!!.trustStoreFile)
        assertEquals("inboundkeypassword", config.inboundConfig!!.customSSLConfiguration!!.keyStorePassword)
        assertEquals("inboundtrustpassword", config.inboundConfig!!.customSSLConfiguration!!.trustStorePassword)
        assertNull(config.outboundConfig)
        assertEquals(Paths.get("tunnelcerts/tunnelkeys.jks"), config.floatOuterConfig!!.customSSLConfiguration!!.sslKeystore)
        assertEquals(Paths.get("tunnelcerts/tunneltrust.jks"), config.floatOuterConfig!!.customSSLConfiguration!!.trustStoreFile)
        assertEquals("tunnelkeypassword", config.floatOuterConfig!!.customSSLConfiguration!!.keyStorePassword)
        assertEquals("tunneltrustpassword", config.floatOuterConfig!!.customSSLConfiguration!!.trustStorePassword)
        assertNull(config.floatInnerConfig)
    }
}