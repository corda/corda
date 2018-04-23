/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node.services.network

import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import net.corda.core.internal.createDirectories
import net.corda.core.internal.div
import net.corda.core.internal.exists
import net.corda.core.internal.readObject
import net.corda.core.utilities.seconds
import net.corda.node.internal.NetworkParametersReader
import net.corda.nodeapi.internal.network.*
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.driver.PortAllocation
import net.corda.testing.internal.DEV_ROOT_CA
import net.corda.testing.node.internal.network.NetworkMapServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.net.URL
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class NetworkParametersReaderTest {
    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule(true)

    private val cacheTimeout = 100000.seconds

    private lateinit var server: NetworkMapServer
    private lateinit var networkMapClient: NetworkMapClient

    @Before
    fun setUp() {
        server = NetworkMapServer(cacheTimeout, PortAllocation.Incremental(10000).nextHostAndPort())
        val hostAndPort = server.start()
        networkMapClient = NetworkMapClient(URL("http://${hostAndPort.host}:${hostAndPort.port}"), DEV_ROOT_CA.certificate)
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `read correct set of parameters from file`() {
        val fs = Jimfs.newFileSystem(Configuration.unix())
        val baseDirectory = fs.getPath("/node").createDirectories()
        val oldParameters = testNetworkParameters(epoch = 1)
        NetworkParametersCopier(oldParameters).install(baseDirectory)
        NetworkParametersCopier(server.networkParameters, update = true).install(baseDirectory) // Parameters update file.
        val parameters = NetworkParametersReader(DEV_ROOT_CA.certificate, networkMapClient, baseDirectory).networkParameters
        assertFalse((baseDirectory / NETWORK_PARAMS_UPDATE_FILE_NAME).exists())
        assertEquals(server.networkParameters, parameters)
        // Parameters from update should be moved to `network-parameters` file.
        val parametersFromFile = (baseDirectory / NETWORK_PARAMS_FILE_NAME)
                .readObject<SignedNetworkParameters>()
                .verifiedNetworkMapCert(DEV_ROOT_CA.certificate)
        assertEquals(server.networkParameters, parametersFromFile)
    }
}