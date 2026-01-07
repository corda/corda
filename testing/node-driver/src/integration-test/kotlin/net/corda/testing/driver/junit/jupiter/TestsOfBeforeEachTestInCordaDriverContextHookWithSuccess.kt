package net.corda.testing.driver.junit.jupiter

import net.corda.finance.DOLLARS
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals

@Timeout(300_000, unit = TimeUnit.MILLISECONDS)
class TestsOfBeforeEachTestInCordaDriverContextHookWithSuccess : JunitJupiterIntegrationTestTemplate() {

    companion object {

        @JvmField
        @RegisterExtension
        val cordaDriverJunitJupiterStatic = cordaDriverJunitJupiterTestConfig.buildDriver()
    }

    override val cordaDriverJunitJupiter: CordaDriverJunitJupiter
        get() = cordaDriverJunitJupiterStatic

    @BeforeEachTestInCordaDriverContext
    fun testFunctionalityOfBeforeEachTestInCordaDriverContext() {

        assertDoesNotThrow {
            cordaDriverJunitJupiter.getCordaNodeHandlesOrThrow()
        }

        assertEquals(
                cordaDriverJunitJupiterTestConfig.parametersForNodes.size,
                cordaDriverJunitJupiter.getCordaNodeHandlesOrThrow().size
        )

        assertEquals(
                cordaDriverJunitJupiterTestConfig.parametersForNodes.map { it.providedName },
                cordaDriverJunitJupiter.cordaX500Names,
                "The Node handles `CordaX500Name` attributes should match the provided names of the `NodeParameter` instances of the `CordaDriverJunitJupiterTestConfig`."
        )

        CashFlowsForTesting(cordaDriverJunitJupiter).apply {
            bankOfCordaIssues(2000.DOLLARS)
            paySomeCash()
        }
    }

    @Test
    fun `empty test to test not throwing behaviour of 'BeforeEachTestInCordaDriverContext'`() {
    }
}
