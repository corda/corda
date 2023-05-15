package net.corda.node.services.network

import net.corda.core.node.NodeInfo
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.node.services.identity.InMemoryIdentityService
import net.corda.node.services.network.PersistentNetworkMapCacheTest.Companion.ALICE
import net.corda.node.services.network.PersistentNetworkMapCacheTest.Companion.BOB
import net.corda.node.services.network.PersistentNetworkMapCacheTest.Companion.CHARLIE
import net.corda.nodeapi.internal.DEV_ROOT_CA
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.core.TestIdentity
import net.corda.testing.internal.TestingNamedCacheFactory
import net.corda.testing.internal.configureDatabase
import net.corda.testing.node.MockServices.Companion.makeTestDataSourceProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test

class PersistentPartyInfoCacheTest {

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    private var portCounter = 1000
    private val database = configureDatabase(makeTestDataSourceProperties(), DatabaseConfig(), { null }, { null })
    private val charlieNetMapCache = PersistentNetworkMapCache(TestingNamedCacheFactory(), database, InMemoryIdentityService(trustRoot = DEV_ROOT_CA.certificate))

    @Test(timeout=300_000)
	fun `get party id from CordaX500Name sourced from NetworkMapCache`() {
        charlieNetMapCache.addOrUpdateNodes(listOf(
                createNodeInfo(listOf(ALICE)),
                createNodeInfo(listOf(BOB)),
                createNodeInfo(listOf(CHARLIE))))
        val partyInfoCache = PersistentPartyInfoCache(charlieNetMapCache, TestingNamedCacheFactory(), database)
        partyInfoCache.start()
        assertThat(partyInfoCache.getPartyIdByCordaX500Name(ALICE.name)).isEqualTo(ALICE.name.hashCode().toLong())
        assertThat(partyInfoCache.getPartyIdByCordaX500Name(BOB.name)).isEqualTo(BOB.name.hashCode().toLong())
        assertThat(partyInfoCache.getPartyIdByCordaX500Name(CHARLIE.name)).isEqualTo(CHARLIE.name.hashCode().toLong())
    }

    @Test(timeout=300_000)
    fun `get party id from CordaX500Name sourced from backing database`() {
        charlieNetMapCache.addOrUpdateNodes(listOf(
                createNodeInfo(listOf(ALICE)),
                createNodeInfo(listOf(BOB)),
                createNodeInfo(listOf(CHARLIE))))
        PersistentPartyInfoCache(charlieNetMapCache, TestingNamedCacheFactory(), database).start()
        // clear network map cache & bootstrap another PersistentInfoCache
        charlieNetMapCache.clearNetworkMapCache()
        val partyInfoCache = PersistentPartyInfoCache(charlieNetMapCache, TestingNamedCacheFactory(), database)
        assertThat(partyInfoCache.getPartyIdByCordaX500Name(ALICE.name)).isEqualTo(ALICE.name.hashCode().toLong())
        assertThat(partyInfoCache.getPartyIdByCordaX500Name(BOB.name)).isEqualTo(BOB.name.hashCode().toLong())
        assertThat(partyInfoCache.getPartyIdByCordaX500Name(CHARLIE.name)).isEqualTo(CHARLIE.name.hashCode().toLong())
    }

    @Test(timeout=300_000)
    fun `get party CordaX500Name from id sourced from NetworkMapCache`() {
        charlieNetMapCache.addOrUpdateNodes(listOf(
                createNodeInfo(listOf(ALICE)),
                createNodeInfo(listOf(BOB)),
                createNodeInfo(listOf(CHARLIE))))
        val partyInfoCache = PersistentPartyInfoCache(charlieNetMapCache, TestingNamedCacheFactory(), database)
        partyInfoCache.start()
        assertThat(partyInfoCache.getCordaX500NameByPartyId(ALICE.name.hashCode().toLong())).isEqualTo(ALICE.name)
        assertThat(partyInfoCache.getCordaX500NameByPartyId(BOB.name.hashCode().toLong())).isEqualTo(BOB.name)
        assertThat(partyInfoCache.getCordaX500NameByPartyId(CHARLIE.name.hashCode().toLong())).isEqualTo(CHARLIE.name)
    }

    @Test(timeout=300_000)
    fun `get party CordaX500Name from id sourced from backing database`() {
        charlieNetMapCache.addOrUpdateNodes(listOf(
                createNodeInfo(listOf(ALICE)),
                createNodeInfo(listOf(BOB)),
                createNodeInfo(listOf(CHARLIE))))
        PersistentPartyInfoCache(charlieNetMapCache, TestingNamedCacheFactory(), database).start()
        // clear network map cache & bootstrap another PersistentInfoCache
        charlieNetMapCache.clearNetworkMapCache()
        val partyInfoCache = PersistentPartyInfoCache(charlieNetMapCache, TestingNamedCacheFactory(), database)
        assertThat(partyInfoCache.getCordaX500NameByPartyId(ALICE.name.hashCode().toLong())).isEqualTo(ALICE.name)
        assertThat(partyInfoCache.getCordaX500NameByPartyId(BOB.name.hashCode().toLong())).isEqualTo(BOB.name)
        assertThat(partyInfoCache.getCordaX500NameByPartyId(CHARLIE.name.hashCode().toLong())).isEqualTo(CHARLIE.name)
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
}
