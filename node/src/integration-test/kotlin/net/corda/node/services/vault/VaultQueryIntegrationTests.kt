package net.corda.node.services.vault


import net.corda.testing.*
import org.junit.*

class VaultQueryIntegrationTests : VaultQueryTests() {

    private val adapter = object: IntegrationTest() {
    }

    @Before
    override fun setUp() {
        adapter.setUp()
        super.setUp()
    }

    @After
    override fun tearDown() {
        adapter.tearDown()
        super.tearDown()
    }

    companion object {
        @ClassRule @JvmField
        val databaseSchemas = IntegrationTestSchemas(MEGA_CORP.toDatabaseSchemaName())

        @BeforeClass
        @JvmStatic
        fun globalSetUp() {
            IntegrationTest.globalSetUp()
        }

        @AfterClass
        @JvmStatic
        fun globalTearDown() {
            IntegrationTest.globalTearDown()
        }
    }
}
