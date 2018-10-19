package net.corda.notary.jpa

import net.corda.core.crypto.DigitalSignature
import net.corda.core.crypto.NullKeys
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.sha256
import net.corda.core.flows.NotarisationRequestSignature
import net.corda.core.flows.NotaryError
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.notary.NotaryInternalException
import net.corda.node.services.config.NotaryConfig
import net.corda.node.services.schema.NodeSchemaService
import net.corda.notary.jpa.JPAUniquenessProvider.Companion.decodeStateRef
import net.corda.notary.jpa.JPAUniquenessProvider.Companion.encodeStateRef
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.DatabaseConfig
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
import kotlin.test.assertFailsWith

class JPAUniquenessProviderTests {
    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule(inheritable = true)
    private val identity = TestIdentity(CordaX500Name("MegaCorp", "London", "GB")).party
    private val txID = SecureHash.randomSHA256()
    private val requestSignature = NotarisationRequestSignature(DigitalSignature.WithKey(NullKeys.NullPublicKey, ByteArray(32)), 0)
    private val notaryConfig = NotaryConfig(validating=false, maxInputStates = 10)

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
    fun `should commit a transaction with unused inputs without exception`() {
            val provider = JPAUniquenessProvider(Clock.systemUTC(), database, notaryConfig)
            val inputState = generateStateRef()

            provider.commit(listOf(inputState), txID, identity, requestSignature)
    }

    @Test
    fun `should report a conflict for a transaction with previously used inputs`() {
        val provider = JPAUniquenessProvider(Clock.systemUTC(), database, notaryConfig)
        val inputState = generateStateRef()

        val inputs = listOf(inputState)
        val firstTxId = txID
        provider.commit(inputs, firstTxId, identity, requestSignature)

        val secondTxId = SecureHash.randomSHA256()
        val ex = assertFailsWith<NotaryInternalException> {
            provider.commit(inputs, secondTxId, identity, requestSignature)
        }
        val error = ex.error as NotaryError.Conflict

        val conflictCause = error.consumedStates[inputState]!!
        assertEquals(conflictCause.hashOfTransactionId, firstTxId.sha256())
    }

    @Test
    fun `serializes and deserializes state ref`() {
        val stateRef = generateStateRef()
        assertEquals(stateRef, decodeStateRef(encodeStateRef(stateRef)))
    }

    @Test
    fun `all conflicts are found with batching`() {
        val nrStates = notaryConfig.maxInputStates + notaryConfig.maxInputStates/2
        val stateRefs = (1..nrStates).map { generateStateRef() }
        println(stateRefs.size)
        val firstTxId = SecureHash.randomSHA256()
        val provider = JPAUniquenessProvider(Clock.systemUTC(), database, notaryConfig)
        provider.commit(stateRefs, firstTxId, identity, requestSignature)
        val secondTxId = SecureHash.randomSHA256()
        val ex = assertFailsWith<NotaryInternalException> {
            provider.commit(stateRefs, secondTxId, identity, requestSignature)
        }
        val error = ex.error as NotaryError.Conflict
        assertEquals(nrStates, error.consumedStates.size)
    }
}
