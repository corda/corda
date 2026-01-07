package net.corda.testing.driver.junit.jupiter

import net.corda.finance.DOLLARS
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension
import rx.exceptions.OnErrorNotImplementedException
import java.util.concurrent.TimeUnit

@Timeout(300_000, unit = TimeUnit.MILLISECONDS)
class TestsOfAfterEachTestInCordaDriverContextHookThrowsCorrectly : JunitJupiterIntegrationTestTemplate() {

    companion object {

        @JvmField
        @RegisterExtension
        val cordaDriverJunitJupiterStatic = cordaDriverJunitJupiterTestConfig.buildDriver()
    }

    override val cordaDriverJunitJupiter: CordaDriverJunitJupiter
        get() = cordaDriverJunitJupiterStatic

    @AfterEachTestInCordaDriverContext
    fun testAfterEachTestInCordaDriverContextThrowsCorrectly() {
        assertThrows<OnErrorNotImplementedException> {
            CashFlowsForTesting(cordaDriverJunitJupiter).apply {
                bankOfCordaIssues(2000.DOLLARS)
                paySomeCash(forceException = true)
            }
        }
    }

    @Test
    fun `empty test to test correct thowing behaviour of 'AfterEachTestInCordaDriverContext'`() {
    }
}
