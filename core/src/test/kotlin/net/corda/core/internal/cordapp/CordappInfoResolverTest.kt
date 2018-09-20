package net.corda.core.internal.cordapp

import org.junit.Test
import kotlin.test.assertEquals

class CordappInfoResolverTest {

    @Test()
    fun `The correct cordapp resolver is used after calling withCordappResolution`() {
        val defaultTargetVersion = 222

        CordappInfoResolver.register(listOf(javaClass.name), CordappImpl.Info("test", "test", "2", 3, defaultTargetVersion))
        assertEquals(defaultTargetVersion, returnCallingTargetVersion())

        val expectedTargetVersion = 555
        CordappInfoResolver.withCordappInfoResolution( { CordappImpl.Info("foo", "bar", "1", 2, expectedTargetVersion) })
        {
            val actualTargetVersion = returnCallingTargetVersion()
            assertEquals(expectedTargetVersion, actualTargetVersion)
        }
        assertEquals(defaultTargetVersion, returnCallingTargetVersion())
    }

    private fun returnCallingTargetVersion(): Int {
        return CordappInfoResolver.getCorDappInfo()?.targetPlatformVersion ?: 0
    }
}