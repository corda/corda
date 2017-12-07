package net.corda.bank

import net.corda.finance.DOLLARS
import net.corda.finance.POUNDS
import net.corda.testing.*
import net.corda.testing.internal.demorun.deployNodesThen
import org.junit.ClassRule
import org.junit.Test

class BankOfCordaCordformTest : IntegrationTest() {
    companion object {
        @ClassRule @JvmField
        val databaseSchemas = IntegrationTestSchemas("NotaryService", "BankOfCorda", BIGCORP_NAME.organisation)
    }

    @Test
    fun `run demo`() {
        BankOfCordaCordform().deployNodesThen {
            IssueCash.requestWebIssue(30000.POUNDS)
            IssueCash.requestRpcIssue(20000.DOLLARS)
        }
    }
}
