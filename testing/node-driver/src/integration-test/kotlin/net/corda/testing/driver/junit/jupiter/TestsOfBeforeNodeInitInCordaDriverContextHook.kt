package net.corda.testing.driver.junit.jupiter

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import kotlin.test.assertNull

class TestsOfBeforeNodeInitInCordaDriverContextHook : JunitJupiterIntegrationTestTemplate() {

    companion object {

        @JvmField
        @RegisterExtension
        val cordaDriverJunitJupiterStatic = cordaDriverJunitJupiterTestConfig.buildDriver()
    }

    override val cordaDriverJunitJupiter: CordaDriverJunitJupiter
        get() = cordaDriverJunitJupiterStatic

    @BeforeNodeInitInCordaDriverContext
    fun testBeforeNodeInitInCordaDriverContextBehaviour() {

        assertNull(cordaDriverJunitJupiter.getCordaNodeHandlesOrNull(), "Expect the Corda node handles to be not initialized before the nodes started.")
        assertNull(cordaDriverJunitJupiter.getDefaultCordaNotaryHandleOrNull(), "Expect the Corda default notary handle to be not initialized before the nodes started.")
    }

    @Test
    fun `empty test to test behaviour of 'BeforeNodeInitInCordaDriverContext'`() {
    }
}
