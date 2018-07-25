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
import net.corda.core.serialization.serialize
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.node.VersionInfo
import net.corda.node.internal.schemas.NodeInfoSchemaV1
import net.corda.node.services.config.*
import net.corda.nodeapi.internal.SignedNodeInfo
import net.corda.nodeapi.internal.network.NodeInfoFilesCopier.Companion.NODE_INFO_FILE_NAME_PREFIX
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.internal.createNodeInfoAndSigned
import net.corda.testing.internal.rigorousMock
import net.corda.testing.node.MockServices.Companion.makeTestDataSourceProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Path
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NodeTest {
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
        val configuration = createConfig(ALICE_NAME)
        val info = VersionInfo(789, "3.0", "SNAPSHOT", "R3")
        configureDatabase(configuration.dataSourceProperties, configuration.database, { null }, { null }).use {
            val node = Node(configuration, info, initialiseSerialization = false)
            assertEquals(node.generateNodeInfo(), node.generateNodeInfo())  // Node info doesn't change (including the serial)
        }
    }

    @Test
    fun `clear network map cache works`() {
        val configuration = createConfig(ALICE_NAME)
        val (nodeInfo, _) = createNodeInfoAndSigned(ALICE_NAME)
        configureDatabase(configuration.dataSourceProperties, configuration.database, { null }, { null }).use {
            it.transaction {
                val persistentNodeInfo = NodeInfoSchemaV1.PersistentNodeInfo(
                        id = 0,
                        hash = nodeInfo.serialize().hash.toString(),
                        addresses = nodeInfo.addresses.map { NodeInfoSchemaV1.DBHostAndPort.fromHostAndPort(it) },
                        legalIdentitiesAndCerts = nodeInfo.legalIdentitiesAndCerts.mapIndexed { idx, elem ->
                            NodeInfoSchemaV1.DBPartyAndCertificate(elem, isMain = idx == 0)
                        },
                        platformVersion = nodeInfo.platformVersion,
                        serial = nodeInfo.serial
                )
                // Save some NodeInfo
                session.save(persistentNodeInfo)
            }
            val versionInfo = VersionInfo(10, "3.0", "SNAPSHOT", "R3")
            val node = Node(configuration, versionInfo, initialiseSerialization = false)
            assertThat(getAllInfos(it)).isNotEmpty
            node.clearNetworkMapCache()
            assertThat(getAllInfos(it)).isEmpty()
        }
    }

    @Test
    fun `Node can start with multiple keypairs for its identity`() {
        val configuration = createConfig(ALICE_NAME)
        val (nodeInfo1, _) = createNodeInfoAndSigned(ALICE_NAME)
        val (nodeInfo2, _) = createNodeInfoAndSigned(ALICE_NAME)


        val persistentNodeInfo2 = NodeInfoSchemaV1.PersistentNodeInfo(
                id = 0,
                hash = nodeInfo2.serialize().hash.toString(),
                addresses = nodeInfo2.addresses.map { NodeInfoSchemaV1.DBHostAndPort.fromHostAndPort(it) },
                legalIdentitiesAndCerts = nodeInfo2.legalIdentitiesAndCerts.mapIndexed { idx, elem ->
                    NodeInfoSchemaV1.DBPartyAndCertificate(elem, isMain = idx == 0)
                },
                platformVersion = nodeInfo2.platformVersion,
                serial = nodeInfo2.serial
        )

        val persistentNodeInfo1 = NodeInfoSchemaV1.PersistentNodeInfo(
                id = 0,
                hash = nodeInfo1.serialize().hash.toString(),
                addresses = nodeInfo1.addresses.map { NodeInfoSchemaV1.DBHostAndPort.fromHostAndPort(it) },
                legalIdentitiesAndCerts = nodeInfo1.legalIdentitiesAndCerts.mapIndexed { idx, elem ->
                    NodeInfoSchemaV1.DBPartyAndCertificate(elem, isMain = idx == 0)
                },
                platformVersion = nodeInfo1.platformVersion,
                serial = nodeInfo1.serial
        )

        configureDatabase(configuration.dataSourceProperties, configuration.database, { null }, { null }).use {
            it.transaction {
                session.save(persistentNodeInfo1)
            }
            it.transaction {
                session.save(persistentNodeInfo2)
            }

            val node = Node(configuration, rigorousMock<VersionInfo>().also {
                doReturn(10).whenever(it).platformVersion
                doReturn("test-vendor").whenever(it).vendor
                doReturn("1.0").whenever(it).releaseVersion
            }, initialiseSerialization = false)

            //this throws an exception with old behaviour
            node.generateNodeInfo()
        }
    }

    private fun getAllInfos(database: CordaPersistence): List<NodeInfoSchemaV1.PersistentNodeInfo> {
        return database.transaction {
            val criteria = session.criteriaBuilder.createQuery(NodeInfoSchemaV1.PersistentNodeInfo::class.java)
            criteria.select(criteria.from(NodeInfoSchemaV1.PersistentNodeInfo::class.java))
            session.createQuery(criteria).resultList
        }
    }

    private fun createConfig(nodeName: CordaX500Name): NodeConfiguration {
        val fakeAddress = NetworkHostAndPort("0.1.2.3", 456)
        return NodeConfigurationImpl(
                baseDirectory = temporaryFolder.root.toPath(),
                myLegalName = nodeName,
                devMode = true, // Needed for identity cert.
                emailAddress = "",
                p2pAddress = fakeAddress,
                keyStorePassword = "ksp",
                trustStorePassword = "tsp",
                crlCheckSoftFail = true,
                dataSourceProperties = makeTestDataSourceProperties(),
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
                relay = null
        )
    }
}
