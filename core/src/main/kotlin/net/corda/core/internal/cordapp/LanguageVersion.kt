package net.corda.core.internal.cordapp

import net.corda.core.internal.JAVA_1_2_CLASS_FILE_MAJOR_VERSION
import net.corda.core.internal.verification.ContractGeneration

sealed class LanguageVersion {
    /**
     * Returns true if this version is compatible with the given [ContractGeneration], i.e. it can be loaded by nodes (or the external
     * verifier) of that generation.
     */
    abstract fun isCompatibleWith(generation: ContractGeneration): Boolean

    /**
     * Returns true if this version is compatible with Corda 4.11 or earlier.
     */
    val isLegacyCompatible: Boolean
        get() = isCompatibleWith(ContractGeneration.CORDA_4_11)

    /**
     * Returns true if this version is compatible with the current version of Corda.
     */
    val isNonLegacyCompatible: Boolean
        get() = isCompatibleWith(ContractGeneration.CURRENT)

    @Suppress("ConvertObjectToDataObject")  // External verifier uses Kotlin 1.2
    object Data : LanguageVersion() {
        override fun isCompatibleWith(generation: ContractGeneration): Boolean = true

        override fun toString(): String = "Data"
    }

    data class Bytecode(val classFileMajorVersion: Int, val kotlinMetadataVersion: KotlinMetadataVersion?): LanguageVersion() {
        init {
            val maxClassFileMajorVersion = ContractGeneration.CURRENT.maxClassFileMajorVersion
            require(classFileMajorVersion in JAVA_1_2_CLASS_FILE_MAJOR_VERSION..maxClassFileMajorVersion) {
                "Unsupported class file major version $classFileMajorVersion"
            }
            val kotlinVersion = kotlinMetadataVersion?.languageMinorVersion
            require(kotlinVersion == null || ContractGeneration.values().any { it.kotlinVersion == kotlinVersion }) {
                "Unsupported Kotlin metadata version $kotlinMetadataVersion"
            }
        }

        override fun isCompatibleWith(generation: ContractGeneration): Boolean {
            return when {
                classFileMajorVersion > generation.maxClassFileMajorVersion -> false
                kotlinMetadataVersion == null -> true  // Java-only CorDapp is fine
                else -> kotlinMetadataVersion.languageMinorVersion == generation.kotlinVersion
            }
        }
    }
}
