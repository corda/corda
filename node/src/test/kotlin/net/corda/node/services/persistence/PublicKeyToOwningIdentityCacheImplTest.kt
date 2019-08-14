package net.corda.node.services.persistence

import junit.framework.TestCase.assertEquals
import net.corda.core.crypto.generateKeyPair
import net.corda.core.node.services.KeyManagementService
import net.corda.core.utilities.getOrThrow
import net.corda.nodeapi.internal.KeyOwningIdentity
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.withoutDatabaseAccess
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.TestIdentity
import net.corda.testing.internal.TestingNamedCacheFactory
import net.corda.testing.node.MockServices
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.security.PublicKey
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class PublicKeyToOwningIdentityCacheImplTest {

    private lateinit var database: CordaPersistence
    private lateinit var testCache: PublicKeyToOwningIdentityCacheImpl
    private lateinit var keyManagementService: KeyManagementService
    private val testKeys = mutableListOf<Pair<KeyOwningIdentity, PublicKey>>()
    private val alice = TestIdentity(ALICE_NAME, 20)
    private lateinit var executor: ExecutorService

    @Before
    fun setUp() {
        val databaseAndServices = MockServices.makeTestDatabaseAndPersistentServices(
                listOf(),
                alice,
                testNetworkParameters(),
                emptySet(),
                emptySet()
        )
        database = databaseAndServices.first
        testCache = PublicKeyToOwningIdentityCacheImpl(database, TestingNamedCacheFactory())
        keyManagementService = databaseAndServices.second.keyManagementService
        createTestKeys()
        executor = Executors.newFixedThreadPool(2)
    }

    @After
    fun tearDown() {
        database.close()
        executor.shutdown()
    }

    private fun createTestKeys() {
        val duplicatedUUID = UUID.randomUUID()
        val uuids = listOf(UUID.randomUUID(), UUID.randomUUID(), null, null, duplicatedUUID, duplicatedUUID)
        uuids.forEach {
            val key = if (it != null) {
                keyManagementService.freshKey(it)
            } else {
                keyManagementService.freshKey()
            }
            testKeys.add(Pair(KeyOwningIdentity.fromUUID(it), key))
        }
    }

    private fun performTestRun() {
        for ((keyOwningIdentity, key) in testKeys) {
            assertEquals(keyOwningIdentity, testCache[key])
        }
    }

    @Test
    fun `cache returns right key for each UUID`() {
        performTestRun()
    }

    @Test
    fun `querying for key twice does not go to database the second time`() {
        performTestRun()

        withoutDatabaseAccess {
            performTestRun()
        }
    }

    @Test
    fun `entries can be fetched if cache invalidated`() {
        testCache = PublicKeyToOwningIdentityCacheImpl(database, TestingNamedCacheFactory(sizeOverride = 0))

        performTestRun()
    }

    @Test
    fun `cache access is thread safe`() {
        val executor = Executors.newFixedThreadPool(2)
        val f1 = executor.submit { performTestRun() }
        val f2 = executor.submit { performTestRun() }
        f2.getOrThrow()
        f1.getOrThrow()
    }

    private fun createAndAddKeys() {
        keyManagementService.freshKey(UUID.randomUUID())
    }

    @Test
    fun `can set multiple keys across threads`() {
        val executor = Executors.newFixedThreadPool(2)
        val f1 = executor.submit { repeat(5) { createAndAddKeys() } }
        val f2 = executor.submit { repeat(5) { createAndAddKeys() } }
        f2.getOrThrow()
        f1.getOrThrow()
    }

    @Test
    fun `requesting a key unknown to the node returns null`() {
        val keys = generateKeyPair()
        assertEquals(null, testCache[keys.public])
    }
}