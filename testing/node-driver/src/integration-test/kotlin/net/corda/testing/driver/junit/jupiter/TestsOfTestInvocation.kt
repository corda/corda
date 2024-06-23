package net.corda.testing.driver.junit.jupiter

import net.corda.finance.DOLLARS
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension
import rx.exceptions.OnErrorNotImplementedException
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals

@Timeout(300_000, unit = TimeUnit.MILLISECONDS)
class TestsOfTestInvocation: JunitJupiterIntegrationTestTemplate() {

    companion object {

        @JvmField
        @RegisterExtension
        val cordaDriverJunitJupiterStatic = cordaDriverJunitJupiterTestConfig.buildDriver()

    }

    override val cordaDriverJunitJupiter: CordaDriverJunitJupiter
        get() = cordaDriverJunitJupiterStatic

    @Test
    fun `all Corda NodeHandles are available and do not throw`() {

        assertDoesNotThrow {
            cordaDriverJunitJupiter.getCordaNodeHandlesOrThrow()
        }
        
        assertEquals(
                cordaDriverJunitJupiterTestConfig.parametersForNodes.size,
                cordaDriverJunitJupiter.getCordaNodeHandlesOrThrow().size
        )
    }

    @Test
    fun `get expected CordaX500Name of nodes`() {

        assertEquals(
            cordaDriverJunitJupiterTestConfig.parametersForNodes.map { it.providedName },
            cordaDriverJunitJupiter.cordaX500Names,
            "The Node handles `CordaX500Name` attributes should match the provided names of the `NodeParameter` instances of the `CordaDriverJunitJupiterTestConfig`."
        )
    }
    
    @Test
    fun `successfully issue and pay cash within a test`() {

        CashFlowsForTesting(cordaDriverJunitJupiter).apply {
            bankOfCordaIssues(2000.DOLLARS)
            paySomeCash()
        }
    }

    @Test
    fun `issue and pay cash and correctly throw forced exception`() {
        assertThrows<OnErrorNotImplementedException> {
            CashFlowsForTesting(cordaDriverJunitJupiter).apply {
                bankOfCordaIssues(2000.DOLLARS)
                paySomeCash(forceException = true)
            }
        }
    }
}
