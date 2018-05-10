/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node.internal

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.delete
import net.corda.core.internal.list
import net.corda.core.internal.readObject
import net.corda.core.node.NodeInfo
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.node.VersionInfo
import net.corda.node.services.config.NodeConfiguration
import net.corda.nodeapi.internal.SignedNodeInfo
import net.corda.nodeapi.internal.network.NodeInfoFilesCopier.Companion.NODE_INFO_FILE_NAME_PREFIX
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.internal.rigorousMock
import net.corda.testing.node.MockServices.Companion.makeTestDataSourceProperties
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NodeTest {
    private abstract class AbstractNodeConfiguration : NodeConfiguration

    @Rule
    @JvmField
    val temporaryFolder = TemporaryFolder()
    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    private fun nodeInfoFile(): Path? {
        return temporaryFolder.root.toPath().list { paths ->
            paths.filter { it.fileName.toString().startsWith(NODE_INFO_FILE_NAME_PREFIX) }.findAny().orElse(null)
        }
    }

    private fun AbstractNode.generateNodeInfo(): NodeInfo {
        assertNull(nodeInfoFile())
        generateAndSaveNodeInfo()
        val path = nodeInfoFile()!!
        try {
            return path.readObject<SignedNodeInfo>().verified()
        } finally {
            path.delete()
        }
    }

    @Test
    fun `generateAndSaveNodeInfo works`() {
        val nodeAddress = NetworkHostAndPort("0.1.2.3", 456)
        val nodeName = CordaX500Name("Manx Blockchain Corp", "Douglas", "IM")
        val info = VersionInfo(789, "3.0", "SNAPSHOT", "R3")
        val dataSourceProperties = makeTestDataSourceProperties()
        val databaseConfig = DatabaseConfig()
        val configuration = rigorousMock<AbstractNodeConfiguration>().also {
            doReturn(null).whenever(it).relay
            doReturn(nodeAddress).whenever(it).p2pAddress
            doReturn(nodeName).whenever(it).myLegalName
            doReturn(null).whenever(it).notary // Don't add notary identity.
            doReturn(dataSourceProperties).whenever(it).dataSourceProperties
            doReturn(databaseConfig).whenever(it).database
            doReturn(temporaryFolder.root.toPath()).whenever(it).baseDirectory
            doReturn(true).whenever(it).devMode // Needed for identity cert.
            doReturn("tsp").whenever(it).trustStorePassword
            doReturn("ksp").whenever(it).keyStorePassword
        }
        configureDatabase(dataSourceProperties, databaseConfig, rigorousMock()).use { _ ->
            val node = Node(configuration, info, initialiseSerialization = false)
            assertEquals(node.generateNodeInfo(), node.generateNodeInfo())  // Node info doesn't change (including the serial)
        }
    }
}
