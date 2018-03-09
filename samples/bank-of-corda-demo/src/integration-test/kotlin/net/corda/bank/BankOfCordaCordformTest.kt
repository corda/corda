/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.bank

import net.corda.finance.DOLLARS
import net.corda.finance.POUNDS
import net.corda.testing.internal.IntegrationTest
import net.corda.testing.internal.IntegrationTestSchemas
import net.corda.testing.node.internal.demorun.nodeRunner
import org.junit.ClassRule
import org.junit.Test

class BankOfCordaCordformTest : IntegrationTest() {
    companion object {
        @ClassRule
        @JvmField
        val databaseSchemas = IntegrationTestSchemas("NotaryService", "BankOfCorda", BIGCORP_NAME.organisation)
    }

    @Test
    fun `run demo`() {
        BankOfCordaCordform().nodeRunner().scanPackages(listOf("net.corda.finance")).deployAndRunNodesThen {
            IssueCash.requestWebIssue(30000.POUNDS)
            IssueCash.requestRpcIssue(20000.DOLLARS)
        }
    }
}
