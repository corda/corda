/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node.services.vault


import net.corda.core.identity.CordaX500Name
import net.corda.testing.core.TestIdentity
import net.corda.testing.internal.IntegrationTest
import net.corda.testing.internal.IntegrationTestSchemas
import net.corda.testing.internal.toDatabaseSchemaName
import org.junit.*

@Ignore // TODO - refactor VaultQuery integration tests with external junit resource
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
        @ClassRule
        @JvmField
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
