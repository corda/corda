package net.corda.coretests.internal.verification

import net.corda.core.contracts.ComponentGroupEnum
import net.corda.core.internal.JAVA_17_CLASS_FILE_MAJOR_VERSION
import net.corda.core.internal.JAVA_25_CLASS_FILE_MAJOR_VERSION
import net.corda.core.internal.JAVA_8_CLASS_FILE_MAJOR_VERSION
import net.corda.core.internal.PLATFORM_VERSION
import net.corda.core.internal.PlatformVersionSwitches
import net.corda.core.internal.cordapp.KotlinMetadataVersion
import net.corda.core.internal.cordapp.LanguageVersion
import net.corda.core.internal.verification.ContractGeneration
import net.corda.core.internal.verification.ContractGeneration.CORDA_4_11
import net.corda.core.internal.verification.ContractGeneration.CORDA_4_12
import net.corda.core.internal.verification.ContractGeneration.CORDA_4_16
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.Test

class ContractGenerationTest {
    @Test(timeout = 300_000)
    fun `generations are in order`() {
        assertThat(ContractGeneration.values()).containsExactly(CORDA_4_11, CORDA_4_12, CORDA_4_16)
        assertThat(ContractGeneration.CURRENT).isEqualTo(CORDA_4_16)
        assertThat(ContractGeneration.LEGACY).containsExactly(CORDA_4_11, CORDA_4_12)
        assertThat(ContractGeneration.values().map { it.platformVersion }).isSorted.doesNotHaveDuplicates()
        assertThat(ContractGeneration.values().map { it.kotlinVersion }).isSorted.doesNotHaveDuplicates()
        assertThat(ContractGeneration.values().map { it.maxClassFileMajorVersion }).isSorted.doesNotHaveDuplicates()
        assertThat(CORDA_4_11.next).isEqualTo(CORDA_4_12)
        assertThat(CORDA_4_12.next).isEqualTo(CORDA_4_16)
        assertThat(CORDA_4_16.next).isNull()
    }

    @Test(timeout = 300_000)
    fun `the current generation is this node's platform version`() {
        assertThat(ContractGeneration.CURRENT.platformVersion).isLessThanOrEqualTo(PLATFORM_VERSION)
        assertThat(ContractGeneration.CURRENT.kotlinVersion.major).isEqualTo(KotlinVersion.CURRENT.major)
        assertThat(ContractGeneration.CURRENT.kotlinVersion.minor).isEqualTo(KotlinVersion.CURRENT.minor)
        assertThat(ContractGeneration.CURRENT.maxClassFileMajorVersion).isEqualTo(JAVA_25_CLASS_FILE_MAJOR_VERSION)
    }

    @Test(timeout = 300_000)
    fun `each generation has its own attachments group`() {
        assertThat(ContractGeneration.values().map { it.attachmentsGroup }).containsExactly(
                ComponentGroupEnum.ATTACHMENTS_GROUP,
                ComponentGroupEnum.ATTACHMENTS_V2_GROUP,
                ComponentGroupEnum.ATTACHMENTS_V3_GROUP
        )
        // The ordinals of the component groups must never change
        assertThat(ComponentGroupEnum.ATTACHMENTS_GROUP.ordinal).isEqualTo(3)
        assertThat(ComponentGroupEnum.ATTACHMENTS_V2_GROUP.ordinal).isEqualTo(9)
        assertThat(ComponentGroupEnum.ATTACHMENTS_V3_GROUP.ordinal).isEqualTo(12)
        for (generation in ContractGeneration.values()) {
            assertThat(ContractGeneration.forAttachmentsGroup(generation.attachmentsGroup)).isEqualTo(generation)
        }
        assertThat(ContractGeneration.forAttachmentsGroup(ComponentGroupEnum.INPUTS_GROUP)).isNull()
    }

    @Test(timeout = 300_000)
    fun `only legacy generations have legacy directories`() {
        assertThat(CORDA_4_11.legacyContractsDirName).isEqualTo("legacy-contracts")
        assertThat(CORDA_4_11.legacyJarsDirName).isEqualTo("legacy-jars")
        assertThat(CORDA_4_12.legacyContractsDirName).isEqualTo("legacy-contracts1.9")
        assertThat(CORDA_4_12.legacyJarsDirName).isEqualTo("legacy-jars1.9")
        assertThat(ContractGeneration.CURRENT.legacyContractsDirName).isNull()
        assertThat(ContractGeneration.CURRENT.legacyJarsDirName).isNull()
        assertThat(ContractGeneration.LEGACY.map { it.legacyContractsDirName }).doesNotHaveDuplicates()
        assertThat(ContractGeneration.LEGACY.map { it.legacyJarsDirName }).doesNotHaveDuplicates()
        assertThat(ContractGeneration.LEGACY.map { it.kotlinVersionString }).containsExactly("1.2", "1.9")
    }

    @Test(timeout = 300_000)
    fun `legacy generations are only required whilst older nodes may be on the network`() {
        assertThat(generationsRequiredFor(PlatformVersionSwitches.FIRST_VERSION)).containsExactly(CORDA_4_11, CORDA_4_12, CORDA_4_16)
        assertThat(generationsRequiredFor(PlatformVersionSwitches.LEGACY_ATTACHMENTS - 1)).containsExactly(CORDA_4_11, CORDA_4_12, CORDA_4_16)
        assertThat(generationsRequiredFor(PlatformVersionSwitches.LEGACY_ATTACHMENTS)).containsExactly(CORDA_4_12, CORDA_4_16)
        assertThat(generationsRequiredFor(PlatformVersionSwitches.ATTACHMENTS_V3 - 1)).containsExactly(CORDA_4_12, CORDA_4_16)
        assertThat(generationsRequiredFor(PlatformVersionSwitches.ATTACHMENTS_V3)).containsExactly(CORDA_4_16)
        assertThat(generationsRequiredFor(Int.MAX_VALUE)).containsExactly(CORDA_4_16)
    }

    @Test(timeout = 300_000)
    fun `language version compatibility`() {
        val kotlin12 = LanguageVersion.Bytecode(JAVA_8_CLASS_FILE_MAJOR_VERSION, KotlinMetadataVersion(1, 1, 18))
        val kotlin19 = LanguageVersion.Bytecode(JAVA_17_CLASS_FILE_MAJOR_VERSION, KotlinMetadataVersion(1, 9, 0))
        val kotlin24 = LanguageVersion.Bytecode(JAVA_25_CLASS_FILE_MAJOR_VERSION, KotlinMetadataVersion(2, 4, 0))
        val java8 = LanguageVersion.Bytecode(JAVA_8_CLASS_FILE_MAJOR_VERSION, null)
        val java17 = LanguageVersion.Bytecode(JAVA_17_CLASS_FILE_MAJOR_VERSION, null)
        val java25 = LanguageVersion.Bytecode(JAVA_25_CLASS_FILE_MAJOR_VERSION, null)

        assertThat(compatibleGenerations(kotlin12)).containsExactly(CORDA_4_11)
        assertThat(compatibleGenerations(kotlin19)).containsExactly(CORDA_4_12)
        assertThat(compatibleGenerations(kotlin24)).containsExactly(CORDA_4_16)
        // Java-only CorDapps are compatible with every generation which supports their bytecode
        assertThat(compatibleGenerations(java8)).containsExactly(CORDA_4_11, CORDA_4_12, CORDA_4_16)
        assertThat(compatibleGenerations(java17)).containsExactly(CORDA_4_12, CORDA_4_16)
        assertThat(compatibleGenerations(java25)).containsExactly(CORDA_4_16)
        assertThat(compatibleGenerations(LanguageVersion.Data)).containsExactly(CORDA_4_11, CORDA_4_12, CORDA_4_16)
        // Kotlin 2.4 compiled to an older bytecode target is still only compatible with the current generation
        assertThat(compatibleGenerations(LanguageVersion.Bytecode(JAVA_17_CLASS_FILE_MAJOR_VERSION, KotlinMetadataVersion(2, 4, 0))))
                .containsExactly(CORDA_4_16)

        assertThat(kotlin12.isLegacyCompatible).isTrue()
        assertThat(kotlin12.isNonLegacyCompatible).isFalse()
        assertThat(kotlin24.isLegacyCompatible).isFalse()
        assertThat(kotlin24.isNonLegacyCompatible).isTrue()
    }

    @Test(timeout = 300_000)
    fun `unsupported language versions`() {
        assertThatIllegalArgumentException().isThrownBy {
            LanguageVersion.Bytecode(JAVA_25_CLASS_FILE_MAJOR_VERSION + 1, null)
        }
        // Kotlin versions not belonging to any generation
        assertThatIllegalArgumentException().isThrownBy {
            LanguageVersion.Bytecode(JAVA_17_CLASS_FILE_MAJOR_VERSION, KotlinMetadataVersion(2, 0, 0))
        }
        assertThatIllegalArgumentException().isThrownBy {
            LanguageVersion.Bytecode(JAVA_25_CLASS_FILE_MAJOR_VERSION, KotlinMetadataVersion(2, 5, 0))
        }
    }

    private fun generationsRequiredFor(minimumPlatformVersion: Int): List<ContractGeneration> {
        return ContractGeneration.values().filter { it.isRequiredFor(minimumPlatformVersion) }
    }

    private fun compatibleGenerations(languageVersion: LanguageVersion): List<ContractGeneration> {
        return ContractGeneration.values().filter(languageVersion::isCompatibleWith)
    }
}
