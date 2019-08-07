package net.corda.notary.jpa

import com.codahale.metrics.MetricRegistry
import net.corda.core.crypto.DigitalSignature
import net.corda.core.crypto.NullKeys
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.NotarisationRequestSignature
import net.corda.core.flows.NotaryError
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.notary.UniquenessProvider
import net.corda.node.services.schema.NodeSchemaService
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.notary.jpa.JPAUniquenessProvider.Companion.decodeStateRef
import net.corda.notary.jpa.JPAUniquenessProvider.Companion.encodeStateRef
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.core.TestIdentity
import net.corda.testing.core.generateStateRef
import net.corda.testing.internal.LogHelper
import net.corda.testing.internal.configureDatabase
import net.corda.testing.node.MockServices.Companion.makeTestDataSourceProperties
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import kotlin.test.assertEquals

/**
 * This class contains tests specific to the JPA uniqueness provider implementation.
 * It is also tested as part of the standard test suite in [UniquenessProviderTests].
 */
class JPAUniquenessProviderTests {
    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule(inheritable = true)
    private val identity = TestIdentity(CordaX500Name("MegaCorp", "London", "GB")).party
    private val requestSignature = NotarisationRequestSignature(DigitalSignature.WithKey(NullKeys.NullPublicKey, ByteArray(32)), 0)
    private val notaryConfig = JPANotaryConfiguration(maxInputStates = 10)

    private lateinit var database: CordaPersistence

    @Before
    fun setUp() {
        LogHelper.setLevel(JPAUniquenessProvider::class)
        database = configureDatabase(makeTestDataSourceProperties(), DatabaseConfig(runMigration = true), { null }, { null }, NodeSchemaService(extraSchemas = setOf(JPANotarySchemaV1)))
    }

    @After
    fun tearDown() {
        database.close()
        LogHelper.reset(JPAUniquenessProvider::class)
    }

    @Test
    fun `correctly encodes and decodes state ref`() {
        val stateRef = generateStateRef()
        assertEquals(stateRef, decodeStateRef(encodeStateRef(stateRef)))
    }

    @Test
    fun `all conflicts are found with batching`() {
        val nrStates = notaryConfig.maxInputStates + notaryConfig.maxInputStates / 2
        val stateRefs = (1..nrStates).map { generateStateRef() }
        println(stateRefs.size)

        val firstTxId = SecureHash.randomSHA256()
        val provider = JPAUniquenessProvider(MetricRegistry(), Clock.systemUTC(), database, notaryConfig)
        val firstResult = provider.commit(stateRefs, firstTxId, identity, requestSignature).get()
        assertEquals(UniquenessProvider.Result.Success, firstResult)

        val secondTxId = SecureHash.randomSHA256()
        val secondResult = provider.commit(stateRefs, secondTxId, identity, requestSignature).get()
        val error = (secondResult as UniquenessProvider.Result.Failure).error as NotaryError.Conflict
        assertEquals(nrStates, error.consumedStates.size)
    }
}
