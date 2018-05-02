/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node.services.transactions

import net.corda.core.crypto.DigitalSignature
import net.corda.core.crypto.NullKeys
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.sha256
import net.corda.core.flows.NotarisationRequestSignature
import net.corda.core.flows.NotaryError
import net.corda.core.flows.NotaryInternalException
import net.corda.core.identity.CordaX500Name
import net.corda.node.internal.configureDatabase
import net.corda.node.services.schema.NodeSchemaService
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.core.TestIdentity
import net.corda.testing.core.generateStateRef
import net.corda.testing.internal.LogHelper
import net.corda.testing.internal.rigorousMock
import net.corda.testing.node.MockServices.Companion.makeTestDataSourceProperties
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PersistentUniquenessProviderTests {
    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()
    private val identity = TestIdentity(CordaX500Name("MegaCorp", "London", "GB")).party
    private val txID = SecureHash.randomSHA256()
    private val requestSignature = NotarisationRequestSignature(DigitalSignature.WithKey(NullKeys.NullPublicKey, ByteArray(32)), 0)

    private lateinit var database: CordaPersistence

    @Before
    fun setUp() {
        LogHelper.setLevel(PersistentUniquenessProvider::class)
        database = configureDatabase(makeTestDataSourceProperties(), DatabaseConfig(runMigration = true), rigorousMock(), NodeSchemaService(includeNotarySchemas = true))
    }

    @After
    fun tearDown() {
        database.close()
        LogHelper.reset(PersistentUniquenessProvider::class)
    }

    @Test
    fun `should commit a transaction with unused inputs without exception`() {
        database.transaction {
            val provider = PersistentUniquenessProvider(Clock.systemUTC())
            val inputState = generateStateRef()

            provider.commit(listOf(inputState), txID, identity, requestSignature)
        }
    }

    @Test
    fun `should report a conflict for a transaction with previously used inputs`() {
        database.transaction {
            val provider = PersistentUniquenessProvider(Clock.systemUTC())
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
    }
}
