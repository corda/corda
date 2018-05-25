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
import net.corda.testing.internal.GlobalDatabaseRule
import net.corda.testing.internal.toDatabaseSchemaName
import org.junit.ClassRule
import org.junit.Rule
import org.junit.rules.RuleChain

class VaultQueryIntegrationTests : VaultQueryTestsBase(), VaultQueryParties by vaultQueryTestRule {

    companion object {
        val MEGA_CORP = TestIdentity(CordaX500Name("MegaCorp", "London", "GB")).name
        val databaseSchemas = listOf(Companion.MEGA_CORP.toDatabaseSchemaName())

        val globalDatabaseRule = GlobalDatabaseRule(databaseSchemas)
        val vaultQueryTestRule = VaultQueryTestRule()

        @ClassRule @JvmField
        val ruleChain = RuleChain.outerRule(globalDatabaseRule).around(vaultQueryTestRule)
    }

    @Suppress("LeakingThis")
    @Rule
    @JvmField
    val transactionRule = VaultQueryRollbackRule(this)
}
