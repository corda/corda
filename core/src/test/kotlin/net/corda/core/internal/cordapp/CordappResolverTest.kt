package net.corda.core.internal.cordapp

import net.corda.core.crypto.SecureHash
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.lang.IllegalStateException
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
        CordappResolver.withTestCordapp(targetPlatformVersion = expectedTargetVersion) {
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
    fun `when different cordapps are registered for the same (non-contract) class, the resolver returns null`() {
        CordappResolver.register(CordappImpl.TEST_INSTANCE.copy(
                contractClassNames = listOf("ContractClass1"),
                minimumPlatformVersion = 3,
                targetPlatformVersion = 222,
                jarHash = SecureHash.randomSHA256()
        ))
        CordappResolver.register(CordappImpl.TEST_INSTANCE.copy(
                contractClassNames = listOf("ContractClass2"),
                minimumPlatformVersion = 2,
                targetPlatformVersion = 456,
                jarHash = SecureHash.randomSHA256()
        ))
        assertThat(CordappResolver.currentCordapp).isNull()
    }

    @Test
    fun `when different cordapps are registered for the same (contract) class, the resolver throws an exception`() {
        val firstCordapp = CordappImpl.TEST_INSTANCE.copy(
            contractClassNames = listOf(javaClass.name),
            minimumPlatformVersion = 3,
            targetPlatformVersion = 222,
            jarHash = SecureHash.randomSHA256()
        )
        val secondCordapp = CordappImpl.TEST_INSTANCE.copy(
            contractClassNames = listOf(javaClass.name),
            minimumPlatformVersion = 2,
            targetPlatformVersion = 456,
            jarHash = SecureHash.randomSHA256()
        )

        CordappResolver.register(firstCordapp)
        assertThatThrownBy { CordappResolver.register(secondCordapp) }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("More than one CorDapp installed on the node for contract ${javaClass.name}. " +
                        "Please remove the previous version when upgrading to a new version.")
    }

}
