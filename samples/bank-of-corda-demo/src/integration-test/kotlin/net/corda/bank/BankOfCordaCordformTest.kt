package net.corda.bank

import net.corda.finance.DOLLARS
import net.corda.finance.POUNDS
import net.corda.testing.node.internal.demorun.nodeRunner
import org.junit.Test

class BankOfCordaCordformTest {
    @Test
    fun `run demo`() {
        BankOfCordaCordform().nodeRunner().scanPackages(listOf("net.corda.finance")).deployAndRunNodesThen {
            IssueCash.requestWebIssue(30000.POUNDS)
            IssueCash.requestRpcIssue(20000.DOLLARS)
        }
    }
}
