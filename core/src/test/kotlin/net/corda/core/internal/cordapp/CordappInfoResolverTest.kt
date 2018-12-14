package net.corda.core.internal.cordapp

import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class CordappInfoResolverTest {
    @Before
    @After
    fun clearCordappInfoResolver() {
        CordappInfoResolver.clear()
    }

    @Test
    fun `the correct cordapp resolver is used after calling withCordappInfo`() {
        val defaultTargetVersion = 222

        CordappInfoResolver.register(CordappImpl.TEST_INSTANCE.copy(
                contractClassNames = listOf(javaClass.name),
                minimumPlatformVersion = 3,
                targetPlatformVersion = defaultTargetVersion
        ))
        assertEquals(defaultTargetVersion, CordappInfoResolver.currentTargetVersion)

        val expectedTargetVersion = 555
        CordappInfoResolver.withCordapp(targetPlatformVersion = expectedTargetVersion) {
            val actualTargetVersion = CordappInfoResolver.currentTargetVersion
            assertEquals(expectedTargetVersion, actualTargetVersion)
        }
        assertEquals(defaultTargetVersion, CordappInfoResolver.currentTargetVersion)
    }

    @Test
    fun `when more than one cordapp is registered for the same class, the resolver returns null`() {
        CordappInfoResolver.register(CordappImpl.TEST_INSTANCE.copy(
                contractClassNames = listOf(javaClass.name),
                minimumPlatformVersion = 3,
                targetPlatformVersion = 222
        ))
        CordappInfoResolver.register(CordappImpl.TEST_INSTANCE.copy(
                contractClassNames = listOf(javaClass.name),
                minimumPlatformVersion = 2,
                targetPlatformVersion = 456
        ))
        assertThat(CordappInfoResolver.currentCordapp).isNull()
    }
}
