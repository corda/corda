package net.corda.node.services.schema

import net.corda.core.node.CordaPluginRegistry
import net.corda.core.schemas.MappedSchema
import net.corda.testing.node.MockNetwork
import net.corda.testing.schemas.DummyDealStateSchemaV1
import net.corda.testing.schemas.DummyLinearStateSchemaV1
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertTrue

class TestPluginRegistry : CordaPluginRegistry() {
    override val requiredSchemas: Set<MappedSchema> = setOf(
            DummyLinearStateSchemaV1
    )
}

class NodeSchemaServiceTest {

    lateinit var mockNet: MockNetwork
    lateinit var mockNode: MockNetwork.MockNode

    @Before
    fun setUp() {
        mockNet = MockNetwork()
        mockNode = mockNet.createNode()
        mockNet.runNetwork()
    }

    @After
    fun cleanUp() {
        mockNet.stopNodes()
    }

    /**
     * Note: this test requires explicitly defining a CordaPluginRegistry declaring custom schemas (see above),
     *       and an associated plugin configuration referencing this additional configuration under:
     *       corda/node/src/test/resources/META-INF/services/net.corda.core.node.CordaPluginRegistry
     */
    @Test
    fun `loading custom schemas from corda plugin configuration`() {
        val schemaService = mockNode.services.schemaService
        assertTrue(schemaService.schemaOptions.containsKey(DummyLinearStateSchemaV1))
    }

    /**
     * Note: this test requires explicitly passing in the location of our custom schemas as follows:
     *       -Dnet.corda.node.cordapp.scan.package="net.corda.testing.schemas"
     */
    @Test
    fun `auto scanning of custom schemas`() {
        val schemaService = mockNode.services.schemaService
        assertTrue(schemaService.schemaOptions.containsKey(DummyDealStateSchemaV1))
    }
}