package net.corda.node.services.schema

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.internal.packageName
import net.corda.core.messaging.startFlow
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.utilities.getOrThrow
import net.corda.node.services.api.ServiceHubInternal
import net.corda.testing.driver.NodeHandle
import net.corda.testing.driver.driver
import net.corda.testing.node.MockNetwork
import net.corda.testing.internal.vault.DummyLinearStateSchemaV1
import org.hibernate.annotations.Cascade
import org.hibernate.annotations.CascadeType
import org.junit.Test
import javax.persistence.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NodeSchemaServiceTest {
    /**
     * Note: this test requires explicitly registering custom contract schemas with a MockNode
     */
    @Test
    fun `registering custom schemas for testing with MockNode`() {
        val mockNet = MockNetwork(cordappPackages = listOf(DummyLinearStateSchemaV1::class.packageName))
        val mockNode = mockNet.createNode()
        val schemaService = mockNode.services.schemaService
        assertTrue(schemaService.schemaOptions.containsKey(DummyLinearStateSchemaV1))

        mockNet.stopNodes()
    }

    /**
     * Note: this test verifies auto-scanning to register identified [MappedSchema] schemas.
     *       By default, Driver uses the caller package for auto-scanning:
     *       System.setProperty("net.corda.node.cordapp.scan.packages", callerPackage)
     */
    @Test
    fun `auto scanning of custom schemas for testing with Driver`() {
        driver(startNodesInProcess = true) {
            val result = defaultNotaryNode.getOrThrow().rpc.startFlow(::MappedSchemasFlow)
            val mappedSchemas = result.returnValue.getOrThrow()
            assertTrue(mappedSchemas.contains(TestSchema.name))
        }
    }

    @Test
    fun `custom schemas are loaded eagerly`() {
        val expected = setOf("PARENTS", "CHILDREN")
        val tables = driver(startNodesInProcess = true) {
            (defaultNotaryNode.getOrThrow() as NodeHandle.InProcess).node.database.transaction {
                session.createNativeQuery("SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES").list()
            }
        }
        assertEquals<Set<*>>(expected, tables.toMutableSet().apply { retainAll(expected) })
    }

    @StartableByRPC
    class MappedSchemasFlow : FlowLogic<List<String>>() {
        @Suspendable
        override fun call(): List<String> {
            // returning MappedSchema's as String'ified family names to avoid whitelist serialization errors
            return (this.serviceHub as ServiceHubInternal).schemaService.schemaOptions.keys.map { it.name }
        }
    }
}

class SchemaFamily

object TestSchema : MappedSchema(SchemaFamily::class.java, 1, setOf(Parent::class.java, Child::class.java)) {
    @Entity
    @Table(name = "Parents")
    class Parent : PersistentState() {
        @OneToMany(fetch = FetchType.LAZY)
        @JoinColumns(JoinColumn(name = "transaction_id", referencedColumnName = "transaction_id"), JoinColumn(name = "output_index", referencedColumnName = "output_index"))
        @OrderColumn
        @Cascade(CascadeType.PERSIST)
        var children: MutableSet<Child> = mutableSetOf()
    }

    @Suppress("unused")
    @Entity
    @Table(name = "Children")
    class Child {
        @Id
        @GeneratedValue
        @Column(name = "child_id", unique = true, nullable = false)
        var childId: Int? = null

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumns(JoinColumn(name = "transaction_id", referencedColumnName = "transaction_id"), JoinColumn(name = "output_index", referencedColumnName = "output_index"))
        var parent: Parent? = null
    }
}