package net.corda.node.services.vault


import net.corda.core.identity.CordaX500Name
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
        val MEGA_CORP = TestIdentity(CordaX500Name("MegaCorp", "London", "GB")).name
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
