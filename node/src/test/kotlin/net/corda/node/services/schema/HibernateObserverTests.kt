/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node.services.schema

import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TransactionState
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.node.services.Vault
import net.corda.core.schemas.CommonSchemaV1
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import net.corda.node.services.api.SchemaService
import net.corda.node.internal.configureDatabase
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.testing.internal.LogHelper
import net.corda.testing.core.TestIdentity
import net.corda.testing.contracts.DummyContract
import net.corda.testing.node.MockServices.Companion.makeTestDataSourceProperties
import org.junit.After
import org.junit.Before
import org.junit.Test
import rx.subjects.PublishSubject
import kotlin.test.assertEquals

class HibernateObserverTests {
    @Before
    fun setUp() {
        LogHelper.setLevel(HibernateObserver::class)
    }

    @After
    fun cleanUp() {
        LogHelper.reset(HibernateObserver::class)
    }

    class TestState : QueryableState {
        override fun supportedSchemas(): Iterable<MappedSchema> {
            throw UnsupportedOperationException()
        }

        override fun generateMappedObject(schema: MappedSchema): PersistentState {
            throw UnsupportedOperationException()
        }

        override val participants: List<AbstractParty>
            get() = throw UnsupportedOperationException()
    }

    @Test
    fun `test child objects are persisted`() {
        val testSchema = TestSchema
        val rawUpdatesPublisher = PublishSubject.create<Vault.Update<ContractState>>()
        val schemaService = object : SchemaService {
            override val schemaOptions: Map<MappedSchema, SchemaService.SchemaOptions> = mapOf(
                    CommonSchemaV1 to SchemaService.SchemaOptions(),
                    testSchema to SchemaService.SchemaOptions())

            override fun selectSchemas(state: ContractState): Iterable<MappedSchema> = setOf(testSchema)

            override fun generateMappedObject(state: ContractState, schema: MappedSchema): PersistentState {
                val parent = TestSchema.Parent()
                parent.children.add(TestSchema.Child())
                parent.children.add(TestSchema.Child())
                return parent
            }
        }
        val database = configureDatabase(makeTestDataSourceProperties(), DatabaseConfig(runMigration = true), { null }, { null }, schemaService)
        HibernateObserver.install(rawUpdatesPublisher, database.hibernateConfig, schemaService)
        database.transaction {
            val MEGA_CORP = TestIdentity(CordaX500Name("MegaCorp", "London", "GB")).party
            rawUpdatesPublisher.onNext(Vault.Update(emptySet(), setOf(StateAndRef(TransactionState(TestState(), DummyContract.PROGRAM_ID, MEGA_CORP), StateRef(SecureHash.sha256("dummy"), 0)))))
            val parentRowCountResult = connection.prepareStatement("select count(*) from Parents").executeQuery()
            parentRowCountResult.next()
            val parentRows = parentRowCountResult.getInt(1)
            parentRowCountResult.close()
            val childrenRowCountResult = connection.prepareStatement("select count(*) from Children").executeQuery()
            childrenRowCountResult.next()
            val childrenRows = childrenRowCountResult.getInt(1)
            childrenRowCountResult.close()
            assertEquals(1, parentRows, "Expected one parent")
            assertEquals(2, childrenRows, "Expected two children")
        }

        database.close()
    }
}