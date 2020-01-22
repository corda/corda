package net.corda.nodeapi.internal.network

import net.corda.core.contracts.ContractClassName
import net.corda.core.crypto.SecureHash
import net.corda.core.node.services.AttachmentId
import net.corda.testing.common.internal.testNetworkParameters
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.Test

class WhitelistGeneratorTest {
    @Test(timeout=300_000)
	fun `no jars against empty whitelist`() {
        val whitelist = generateWhitelist(emptyMap(), emptyList(), emptyList())
        assertThat(whitelist).isEmpty()
    }

    @Test(timeout=300_000)
	fun `no jars against single whitelist`() {
        val existingWhitelist = mapOf("class1" to listOf(SecureHash.randomSHA256()))
        val newWhitelist = generateWhitelist(existingWhitelist, emptyList(), emptyList())
        assertThat(newWhitelist).isEqualTo(existingWhitelist)
    }

    @Test(timeout=300_000)
	fun `empty jar against empty whitelist`() {
        val whitelist = generateWhitelist(emptyMap(), emptyList(), listOf(TestContractsJar(contractClassNames = emptyList())))
        assertThat(whitelist).isEmpty()
    }

    @Test(timeout=300_000)
	fun `empty jar against single whitelist`() {
        val existingWhitelist = mapOf("class1" to listOf(SecureHash.randomSHA256()))
        val newWhitelist = generateWhitelist(existingWhitelist, emptyList(), listOf(TestContractsJar(contractClassNames = emptyList())))
        assertThat(newWhitelist).isEqualTo(existingWhitelist)
    }

    @Test(timeout=300_000)
	fun `jar with single contract against empty whitelist`() {
        val jar = TestContractsJar(contractClassNames = listOf("class1"))
        val whitelist = generateWhitelist(emptyMap(), emptyList(), listOf(jar))
        assertThat(whitelist).isEqualTo(mapOf(
                "class1" to listOf(jar.hash)
        ))
    }

    @Test(timeout=300_000)
	fun `single contract jar against single whitelist of different contract`() {
        val class1JarHash = SecureHash.randomSHA256()
        val existingWhitelist = mapOf("class1" to listOf(class1JarHash))
        val jar = TestContractsJar(contractClassNames = listOf("class2"))
        val whitelist = generateWhitelist(existingWhitelist, emptyList(), listOf(jar))
        assertThat(whitelist).isEqualTo(mapOf(
                "class1" to listOf(class1JarHash),
                "class2" to listOf(jar.hash)
        ))
    }

    @Test(timeout=300_000)
	fun `same jar with single contract`() {
        val jarHash = SecureHash.randomSHA256()
        val existingWhitelist = mapOf("class1" to listOf(jarHash))
        val jar = TestContractsJar(hash = jarHash, contractClassNames = listOf("class1"))
        val newWhitelist = generateWhitelist(existingWhitelist, emptyList(), listOf(jar))
        assertThat(newWhitelist).isEqualTo(existingWhitelist)
    }

    @Test(timeout=300_000)
	fun `jar with updated contract`() {
        val previousJarHash = SecureHash.randomSHA256()
        val existingWhitelist = mapOf("class1" to listOf(previousJarHash))
        val newContractsJar = TestContractsJar(contractClassNames = listOf("class1"))
        val newWhitelist = generateWhitelist(existingWhitelist, emptyList(), listOf(newContractsJar))
        assertThat(newWhitelist).isEqualTo(mapOf(
                "class1" to listOf(previousJarHash, newContractsJar.hash)
        ))
    }

    @Test(timeout=300_000)
	fun `jar with one existing contract and one new one`() {
        val previousJarHash = SecureHash.randomSHA256()
        val existingWhitelist = mapOf("class1" to listOf(previousJarHash))
        val newContractsJar = TestContractsJar(contractClassNames = listOf("class1", "class2"))
        val newWhitelist = generateWhitelist(existingWhitelist, emptyList(), listOf(newContractsJar))
        assertThat(newWhitelist).isEqualTo(mapOf(
                "class1" to listOf(previousJarHash, newContractsJar.hash),
                "class2" to listOf(newContractsJar.hash)
        ))
    }

    @Test(timeout=300_000)
	fun `two versions of the same contract`() {
        val version1Jar = TestContractsJar(contractClassNames = listOf("class1"))
        val version2Jar = TestContractsJar(contractClassNames = listOf("class1"))
        val newWhitelist = generateWhitelist(emptyMap(), emptyList(), listOf(version1Jar, version2Jar))
        assertThat(newWhitelist).isEqualTo(mapOf(
                "class1" to listOf(version1Jar.hash, version2Jar.hash)
        ))
    }

    @Test(timeout=300_000)
	fun `jar with single new contract that's excluded`() {
        val jar = TestContractsJar(contractClassNames = listOf("class1"))
        val whitelist = generateWhitelist(emptyMap(), listOf("class1"), listOf(jar))
        assertThat(whitelist).isEmpty()
    }

    @Test(timeout=300_000)
	fun `jar with two new contracts, one of which is excluded`() {
        val jar = TestContractsJar(contractClassNames = listOf("class1", "class2"))
        val whitelist = generateWhitelist(emptyMap(), listOf("class1"), listOf(jar))
        assertThat(whitelist).isEqualTo(mapOf(
                "class2" to listOf(jar.hash)
        ))
    }

    @Test(timeout=300_000)
	fun `jar with updated contract but it's excluded`() {
        val existingWhitelist = mapOf("class1" to listOf(SecureHash.randomSHA256()))
        val jar = TestContractsJar(contractClassNames = listOf("class1"))
        assertThatIllegalArgumentException().isThrownBy {
            generateWhitelist(existingWhitelist, listOf("class1"), listOf(jar))
        }
    }

    private fun generateWhitelist(existingWhitelist: Map<String, List<AttachmentId>>,
                                  excludeContracts: List<ContractClassName>,
                                  contractJars: List<TestContractsJar>): Map<String, List<AttachmentId>> {
        return generateWhitelist(
                testNetworkParameters(whitelistedContractImplementations = existingWhitelist),
                excludeContracts,
                contractJars,
                emptyList(),
                emptyList()
        )
    }
}
