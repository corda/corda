package net.corda.core.internal.cordapp

import net.corda.core.crypto.SecureHash
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class CordappResolverTest {
    @Before
    @After
    fun clearCordappInfoResolver() {
        CordappResolver.clear()
    }

    @Test
    fun `the correct cordapp resolver is used after calling withCordappInfo`() {
        val defaultTargetVersion = 222

        CordappResolver.register(CordappImpl.TEST_INSTANCE.copy(
                contractClassNames = listOf(javaClass.name),
                minimumPlatformVersion = 3,
                targetPlatformVersion = defaultTargetVersion
        ))
        assertEquals(defaultTargetVersion, CordappResolver.currentTargetVersion)

        val expectedTargetVersion = 555
        CordappResolver.withCordapp(targetPlatformVersion = expectedTargetVersion) {
            val actualTargetVersion = CordappResolver.currentTargetVersion
            assertEquals(expectedTargetVersion, actualTargetVersion)
        }
        assertEquals(defaultTargetVersion, CordappResolver.currentTargetVersion)
    }

    @Test
    fun `when the same cordapp is registered for the same class multiple times, the resolver deduplicates and returns it as the current one`() {
        CordappResolver.register(CordappImpl.TEST_INSTANCE.copy(
                contractClassNames = listOf(javaClass.name),
                minimumPlatformVersion = 3,
                targetPlatformVersion = 222
        ))
        CordappResolver.register(CordappImpl.TEST_INSTANCE.copy(
                contractClassNames = listOf(javaClass.name),
                minimumPlatformVersion = 2,
                targetPlatformVersion = 456
        ))
        assertThat(CordappResolver.currentCordapp).isNotNull()
    }

    @Test
    fun `when different cordapps are registered for the same class, the resolver returns null`() {
        CordappResolver.register(CordappImpl.TEST_INSTANCE.copy(
                contractClassNames = listOf(javaClass.name),
                minimumPlatformVersion = 3,
                targetPlatformVersion = 222,
                jarHash = SecureHash.randomSHA256()
        ))
        CordappResolver.register(CordappImpl.TEST_INSTANCE.copy(
                contractClassNames = listOf(javaClass.name),
                minimumPlatformVersion = 2,
                targetPlatformVersion = 456,
                jarHash = SecureHash.randomSHA256()
        ))
        assertThat(CordappResolver.currentCordapp).isNull()
    }
}
