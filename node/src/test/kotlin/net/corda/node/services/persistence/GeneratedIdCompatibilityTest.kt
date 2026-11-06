package net.corda.node.services.persistence

import net.corda.core.schemas.MappedSchema
import net.corda.node.internal.schemas.NodeInfoSchemaV1
import net.corda.node.services.schema.NodeSchemaService
import net.corda.node.services.vault.VaultSchemaV1
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.node.services.transactions.PersistentUniquenessProvider
import net.corda.notary.experimental.bftsmart.BFTSmartNotarySchemaV1
import net.corda.notary.experimental.raft.RaftNotarySchemaV1
import net.corda.notary.jpa.JPANotarySchemaV1
import net.corda.testing.internal.configureDatabase
import net.corda.testing.node.MockServices.Companion.makeTestDataSourceProperties
import org.hibernate.engine.spi.SessionFactoryImplementor
import org.hibernate.id.CompositeNestedGeneratedValueGenerator
import org.hibernate.id.enhanced.NoopOptimizer
import org.hibernate.id.enhanced.SequenceStyleGenerator
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Hibernate 5 gave every entity with a plain `@GeneratedValue` the single "hibernate_sequence", with an increment of 1 and no
 * in-memory pooling of ids. Existing databases depend on that: Liquibase created the sequence with an increment of 1, and its values
 * are already in use. Hibernate 6 and later default to a sequence per entity with an increment of 50, which HibernateCompatibility
 * turns off. This test catches a change to those settings, or a Hibernate release that changes what they do, before it reaches a
 * database.
 */
class GeneratedIdCompatibilityTest {
    private val schemaSets: List<Set<MappedSchema>> = listOf(
            NodeSchemaService().schemas,
            setOf(JPANotarySchemaV1),
            setOf(RaftNotarySchemaV1),
            setOf(BFTSmartNotarySchemaV1)
    )

    private val database: CordaPersistence = configureDatabase(makeTestDataSourceProperties(), DatabaseConfig(), { null }, { null },
            NodeSchemaService(schemaSets.flatten().toSet()))

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test(timeout = 300_000)
    fun `every generated id uses hibernate_sequence with an increment of 1, as with Hibernate 5`() {
        val generators = mutableMapOf<String, Any>()
        for (schemas in schemaSets) {
            val sessionFactory = database.hibernateConfig.sessionFactoryForSchemas(schemas).unwrap(SessionFactoryImplementor::class.java)
            sessionFactory.mappingMetamodel.forEachEntityDescriptor { persister ->
                val generator = persister.generator
                // An @EmbeddedId has a composite generator whose values are assigned by the application.
                val assigned = generator is CompositeNestedGeneratedValueGenerator && generator.hasAssignedValues()
                if (generator.generatesOnInsert() && !assigned) {
                    generators[persister.entityName] = generator
                }
            }
        }
        val expected = listOf(
                NodeInfoSchemaV1.PersistentNodeInfo::class.java,
                NodeInfoSchemaV1.DBHostAndPort::class.java,
                VaultSchemaV1.VaultTxnNote::class.java,
                PersistentUniquenessProvider.Request::class.java
        ).map { it.name }
        assertTrue(generators.keys.containsAll(expected), "Entities with generated ids: ${generators.keys}")
        generators.forEach { (entity, generator) ->
            assertTrue(generator is SequenceStyleGenerator, "$entity uses $generator")
            assertEquals("hibernate_sequence", generator.databaseStructure.physicalName.objectName.text, entity)
            assertEquals(1, generator.databaseStructure.incrementSize, entity)
            assertTrue(generator.optimizer is NoopOptimizer, "$entity uses ${generator.optimizer}")
        }
    }
}
