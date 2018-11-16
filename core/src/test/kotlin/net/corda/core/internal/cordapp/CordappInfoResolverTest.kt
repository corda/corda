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

        CordappInfoResolver.register(listOf(javaClass.name), CordappImpl.Info("test", "test", "2", 3, defaultTargetVersion))
        assertEquals(defaultTargetVersion, CordappInfoResolver.currentTargetVersion)

        val expectedTargetVersion = 555
        CordappInfoResolver.withCordappInfo(targetPlatformVersion = expectedTargetVersion) {
            val actualTargetVersion = CordappInfoResolver.currentTargetVersion
            assertEquals(expectedTargetVersion, actualTargetVersion)
        }
        assertEquals(defaultTargetVersion, CordappInfoResolver.currentTargetVersion)
    }

    @Test
    fun `when more than one cordapp is registered for the same class, the resolver returns null`() {
        CordappInfoResolver.register(listOf(javaClass.name), CordappImpl.Info("test", "test", "2", 3, 222))
        CordappInfoResolver.register(listOf(javaClass.name), CordappImpl.Info("test1", "test1", "1", 2, 456))
        assertThat(CordappInfoResolver.currentCordappInfo).isNull()
    }
}
