package net.corda.core.internal.cordapp

import org.junit.Test
import kotlin.test.assertEquals

class CordappInfoResolverTest {

    @Test()
    fun `The target version of the calling cordapp can be detected`() {
        val expectedTargetVersion = 5000
        CordappInfoResolver.withCordappInfoResolution(object : CordappInfoResolver() {
            override fun invoke(): CordappImpl.Info? {
                return CordappImpl.Info("foo", "bar", "1", 2, expectedTargetVersion)
            }
        }) {
            val actualTargetVersion = returnCallingTargetVersion()
            assertEquals(expectedTargetVersion, actualTargetVersion)
        }
    }

    private fun returnCallingTargetVersion(): Int {
        return CordappInfoResolver.getCorDappInfo()?.targetPlatformVersion ?: 0
    }
}