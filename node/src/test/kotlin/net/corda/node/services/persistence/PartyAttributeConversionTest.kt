package net.corda.node.services.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.node.services.schema.NodeSchemaService
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.TestIdentity
import net.corda.testing.internal.configureDatabase
import net.corda.testing.node.MockServices.Companion.makeTestDataSourceProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * An [AbstractParty] attribute is converted using the identity service. Hibernate must treat it as immutable, otherwise it converts the
 * value a second time (to and from the database representation) to make a copy of it for dirty checking.
 */
class PartyAttributeConversionTest {
    @Entity
    @jakarta.persistence.Table(name = "party_attribute_test")
    class Row(@Id @Column(name = "id") var id: Int = 0, @Column(name = "owner") var owner: AbstractParty? = null)

    object TestSchema : MappedSchema(PartyAttributeConversionTest::class.java, 1, listOf(Row::class.java))

    @Test(timeout = 300_000)
    fun `loading an entity converts a party attribute only once`() {
        val alice = TestIdentity(ALICE_NAME, 70).party
        val lookups = AtomicInteger()
        configureDatabase(
                makeTestDataSourceProperties(),
                DatabaseConfig(),
                { name: CordaX500Name -> lookups.incrementAndGet(); if (name == alice.name) alice else null },
                { party: AbstractParty -> party as? Party },
                NodeSchemaService(extraSchemas = setOf(TestSchema))
        ).use { database ->
            database.transaction { session.persist(Row(1, alice)) }
            val lookupsBeforeLoad = lookups.get()
            database.transaction {
                assertThat(session.find(Row::class.java, 1).owner).isEqualTo(alice)
                session.flush()
            }
            assertThat(lookups.get() - lookupsBeforeLoad).isEqualTo(1)
        }
    }
}
