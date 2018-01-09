package net.corda.bank

import net.corda.finance.DOLLARS
import net.corda.finance.POUNDS
import net.corda.testing.node.internal.demorun.deployNodesThen
import org.junit.Test

class BankOfCordaCordformTest {
    @Test
    fun `run demo`() {
        BankOfCordaCordform().deployNodesThen {
            IssueCash.requestWebIssue(30000.POUNDS)
            IssueCash.requestRpcIssue(20000.DOLLARS)
        }
    }
}
