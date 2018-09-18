package net.corda.core.internal.cordapp

import org.junit.Test
import kotlin.test.assertEquals

class CordappInfoResolverTest {

    @Test()
    fun `The correct cordapp resolver is used after calling withCordappResolution`() {
        val defaultTargetVersion = 111
        val default = object : CordappInfoResolver() {
            override fun invoke(): CordappImpl.Info? {
                return CordappImpl.Info("default", "default", "1", 2, defaultTargetVersion)
            }
        }
        CordappInfoResolver.init(default)
        assertEquals(returnCallingTargetVersion(), defaultTargetVersion)

        val expectedTargetVersion = 555
        CordappInfoResolver.withCordappInfoResolution(object : CordappInfoResolver() {
            override fun invoke(): CordappImpl.Info? {
                return CordappImpl.Info("foo", "bar", "1", 2, expectedTargetVersion)
            }
        }) {
            val actualTargetVersion = returnCallingTargetVersion()
            assertEquals(expectedTargetVersion, actualTargetVersion)
        }
        assertEquals(returnCallingTargetVersion(), defaultTargetVersion)
    }

    private fun returnCallingTargetVersion(): Int {
        return CordappInfoResolver.getCorDappInfo()?.targetPlatformVersion ?: 0
    }
}