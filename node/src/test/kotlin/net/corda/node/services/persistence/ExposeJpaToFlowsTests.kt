package net.corda.node.services.persistence

import co.paralleluniverse.fibers.Suspendable
import com.esotericsoftware.kryo.KryoException
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowLogic.Companion.sleep
import net.corda.core.identity.CordaX500Name
import net.corda.core.schemas.MappedSchema
import net.corda.core.serialization.CordaSerializable
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockServices
import net.corda.testing.node.makeTestIdentityService
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import java.io.Serializable
import java.time.Duration
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith


class ExposeJpaToFlowsTests {

    object FooSchema

    object FooSchemaV1 : MappedSchema(schemaFamily = FooSchema.javaClass, version = 1, mappedTypes = listOf(PersistentFoo::class.java)) {
        @Entity
        @Table(name = "foos")
        class PersistentFoo(@Id @Column(name = "foo_id") var fooId: String, @Column(name = "foo_data") var fooData: String) : Serializable
    }

    val myself = TestIdentity(CordaX500Name("Me", "London", "GB"))
    val cordapps = listOf("net.corda.node.services.persistence")
    val databaseAndServices = MockServices.makeTestDatabaseAndMockServices(
            cordappPackages = cordapps,
            identityService = makeTestIdentityService(myself.identity),
            initialIdentity = myself,
            networkParameters = testNetworkParameters(minimumPlatformVersion = 4)
    )

    val services: MockServices = databaseAndServices.second
    val database: CordaPersistence = databaseAndServices.first

    @Test
    fun `can persist and query custom entities`() {
        val foo = FooSchemaV1.PersistentFoo(UniqueIdentifier().id.toString(), "Bar")

        // Persist the foo.
        val result: MutableList<FooSchemaV1.PersistentFoo> = database.transaction {
            services.withEntityManager {
                // Persist.
                persist(foo)
                // Query.
                val query = criteriaBuilder.createQuery(FooSchemaV1.PersistentFoo::class.java)
                val type = query.from(FooSchemaV1.PersistentFoo::class.java)
                query.select(type)
                createQuery(query).resultList
            }
        }

        assertEquals("Bar", result.single().fooData)
    }

    @Test
    fun `can't perform suspendable operations inside withEntityManager`() {
        val mockNet = MockNetwork(cordapps)
        val mockNode = mockNet.createNode()
        assertFailsWith(KryoException::class) {
            mockNode.startFlow(object : FlowLogic<Unit>() {
                @Suspendable
                override fun call() {
                    serviceHub.withEntityManager {
                        val session = initiateFlow(myself.party)
                        session.send("Ooohhh eee oooh ah ah ting tang walla walla bing bang!")
                    }
                }
            })
        }
        mockNet.stopNodes()
    }
}