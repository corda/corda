package net.corda.node.services.transactions

import net.corda.core.crypto.SecureHash
import net.corda.core.identity.CordaX500Name
import net.corda.core.node.services.UniquenessException
import net.corda.node.internal.configureDatabase
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.testing.*
import net.corda.testing.internal.LogHelper
import net.corda.testing.internal.rigorousMock
import net.corda.testing.node.MockServices.Companion.makeTestDataSourceProperties
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PersistentUniquenessProviderTests {
    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()
    private val identity = TestIdentity(CordaX500Name("MegaCorp", "London", "GB")).party
    private val txID = SecureHash.randomSHA256()

    private lateinit var database: CordaPersistence

    @Before
    fun setUp() {
        LogHelper.setLevel(PersistentUniquenessProvider::class)
        database = configureDatabase(makeTestDataSourceProperties(), DatabaseConfig(), rigorousMock())
    }

    @After
    fun tearDown() {
        database.close()
        LogHelper.reset(PersistentUniquenessProvider::class)
    }

    @Test
    fun `should commit a transaction with unused inputs without exception`() {
        database.transaction {
            val provider = PersistentUniquenessProvider()
            val inputState = generateStateRef()

            provider.commit(listOf(inputState), txID, identity)
        }
    }

    @Test
    fun `should report a conflict for a transaction with previously used inputs`() {
        database.transaction {
            val provider = PersistentUniquenessProvider()
            val inputState = generateStateRef()

            val inputs = listOf(inputState)
            provider.commit(inputs, txID, identity)

            val ex = assertFailsWith<UniquenessException> { provider.commit(inputs, txID, identity) }

            val consumingTx = ex.error.stateHistory[inputState]!!
            assertEquals(consumingTx.id, txID)
            assertEquals(consumingTx.inputIndex, inputs.indexOf(inputState))
            assertEquals(consumingTx.requestingParty, identity)
        }
    }
}
