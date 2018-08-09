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

import net.corda.core.node.NodeInfo
import net.corda.core.serialization.serialize
import net.corda.core.utilities.NetworkHostAndPort

import net.corda.node.internal.configureDatabase
import net.corda.node.internal.schemas.NodeInfoSchemaV1
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.testing.core.*
import net.corda.testing.node.MockServices.Companion.makeTestDataSourceProperties
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.After
import org.junit.Rule
import org.junit.Test

class PersistentNetworkMapCacheTest  {
    private companion object {
        val ALICE = TestIdentity(ALICE_NAME, 70)
        val BOB = TestIdentity(BOB_NAME, 80)
        val CHARLIE = TestIdentity(CHARLIE_NAME, 90)
    }

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    private var portCounter = 1000
    private val database = configureDatabase(makeTestDataSourceProperties(), DatabaseConfig(), { null }, { null })
    private val charlieNetMapCache = PersistentNetworkMapCache(database, CHARLIE.name)

    @After
    fun cleanUp() {
        database.close()
    }

    @Test
    fun addNode() {
        val alice = createNodeInfo(listOf(ALICE))
        charlieNetMapCache.addNode(alice)
        assertThat(charlieNetMapCache.nodeReady).isDone()
        val fromDb = database.transaction {
            session.createQuery(
                    "from ${NodeInfoSchemaV1.PersistentNodeInfo::class.java.name}",
                    NodeInfoSchemaV1.PersistentNodeInfo::class.java
            ).resultList.map { it.toNodeInfo() }
        }
        assertThat(fromDb).containsOnly(alice)
    }

    @Test
    fun `adding the node's own node-info doesn't complete the nodeReady future`() {
        val charlie = createNodeInfo(listOf(CHARLIE))
        charlieNetMapCache.addNode(charlie)
        assertThat(charlieNetMapCache.nodeReady).isNotDone()
        assertThat(charlieNetMapCache.getNodeByLegalName(CHARLIE.name)).isEqualTo(charlie)
    }

    @Test
    fun `starting with just the node's own node-info in the db`() {
        val charlie = createNodeInfo(listOf(CHARLIE))
        saveNodeInfoIntoDb(charlie)
        assertThat(charlieNetMapCache.allNodes).containsOnly(charlie)
        charlieNetMapCache.start(emptyList())
        assertThat(charlieNetMapCache.nodeReady).isNotDone()
    }

    @Test
    fun `starting with another node-info in the db`() {
        val alice = createNodeInfo(listOf(ALICE))
        saveNodeInfoIntoDb(alice)
        assertThat(charlieNetMapCache.allNodes).containsOnly(alice)
        charlieNetMapCache.start(emptyList())
        assertThat(charlieNetMapCache.nodeReady).isDone()
    }

    @Test
    fun `unknown legal name`() {
        charlieNetMapCache.addNode(createNodeInfo(listOf(ALICE)))
        assertThat(charlieNetMapCache.getNodesByLegalName(DUMMY_NOTARY_NAME)).isEmpty()
        assertThat(charlieNetMapCache.getNodeByLegalName(DUMMY_NOTARY_NAME)).isNull()
        assertThat(charlieNetMapCache.getPeerByLegalName(DUMMY_NOTARY_NAME)).isNull()
        assertThat(charlieNetMapCache.getPeerCertificateByLegalName(DUMMY_NOTARY_NAME)).isNull()
    }

    @Test
    fun `nodes in distributed service`() {
        charlieNetMapCache.addNode(createNodeInfo(listOf(ALICE)))

        val distributedIdentity = TestIdentity(DUMMY_NOTARY_NAME)

        val distServiceNodeInfos = (1..2).map {
            val nodeInfo = createNodeInfo(identities = listOf(TestIdentity.fresh("Org-$it"), distributedIdentity))
            charlieNetMapCache.addNode(nodeInfo)
            nodeInfo
        }

        assertThat(charlieNetMapCache.getNodesByLegalName(DUMMY_NOTARY_NAME)).containsOnlyElementsOf(distServiceNodeInfos)
        assertThatIllegalArgumentException()
                .isThrownBy { charlieNetMapCache.getNodeByLegalName(DUMMY_NOTARY_NAME) }
                .withMessageContaining(DUMMY_NOTARY_NAME.toString())
    }

    @Test
    fun `get nodes by owning key and by name`() {
        val alice = createNodeInfo(listOf(ALICE))
        charlieNetMapCache.addNode(alice)
        assertThat(charlieNetMapCache.getNodesByLegalIdentityKey(ALICE.publicKey)).containsOnly(alice)
        assertThat(charlieNetMapCache.getNodeByLegalName(ALICE.name)).isEqualTo(alice)
    }

    @Test
    fun `get nodes by address`() {
        val alice = createNodeInfo(listOf(ALICE))
        charlieNetMapCache.addNode(alice)
        assertThat(charlieNetMapCache.getNodeByAddress(alice.addresses[0])).isEqualTo(alice)
    }

    @Test
    fun `insert two node infos with the same host and port`() {
        val alice = createNodeInfo(listOf(ALICE))
        charlieNetMapCache.addNode(alice)
        val bob = createNodeInfo(listOf(BOB), address = alice.addresses[0])
        charlieNetMapCache.addNode(bob)
        val nodeInfos = charlieNetMapCache.allNodes.filter { alice.addresses[0] in it.addresses }
        assertThat(nodeInfos).hasSize(2)
    }

    private fun createNodeInfo(identities: List<TestIdentity>,
                               address: NetworkHostAndPort = NetworkHostAndPort("localhost", portCounter++)): NodeInfo {
        return NodeInfo(
                addresses = listOf(address),
                legalIdentitiesAndCerts = identities.map { it.identity },
                platformVersion = 3,
                serial = 1
        )
    }

    private fun saveNodeInfoIntoDb(nodeInfo: NodeInfo) {
        database.transaction {
            session.save(NodeInfoSchemaV1.PersistentNodeInfo(
                    id = 0,
                    hash = nodeInfo.serialize().hash.toString(),
                    addresses = nodeInfo.addresses.map { NodeInfoSchemaV1.DBHostAndPort.fromHostAndPort(it) },
                    legalIdentitiesAndCerts = nodeInfo.legalIdentitiesAndCerts.mapIndexed { idx, elem ->
                        NodeInfoSchemaV1.DBPartyAndCertificate(elem, isMain = idx == 0)
                    },
                    platformVersion = nodeInfo.platformVersion,
                    serial = nodeInfo.serial
            ))
        }
    }
}
