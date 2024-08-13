package net.corda.core.internal.cordapp

import net.corda.core.internal.JAVA_17_CLASS_FILE_MAJOR_VERSION
import net.corda.core.internal.JAVA_1_2_CLASS_FILE_MAJOR_VERSION
import net.corda.core.internal.JAVA_8_CLASS_FILE_MAJOR_VERSION

sealed class LanguageVersion {
    /**
     * Returns true if this version is compatible with Corda 4.11 or earlier.
     */
    abstract val isLegacyCompatible: Boolean

    /**
     * Returns true if this version is compatible with Corda 4.12 or later.
     */
    abstract val isNonLegacyCompatible: Boolean

    @Suppress("ConvertObjectToDataObject")  // External verifier uses Kotlin 1.2
    object Data : LanguageVersion() {
        override val isLegacyCompatible: Boolean
            get() = true

        override val isNonLegacyCompatible: Boolean
            get() = true

        override fun toString(): String = "Data"
    }

    data class Bytecode(val classFileMajorVersion: Int, val kotlinMetadataVersion: KotlinMetadataVersion?): LanguageVersion() {
        companion object {
            private val KOTLIN_1_2_VERSION = KotlinVersion(1, 2)
            private val KOTLIN_1_9_VERSION = KotlinVersion(1, 9)
        }

        init {
            require(classFileMajorVersion in JAVA_1_2_CLASS_FILE_MAJOR_VERSION..JAVA_17_CLASS_FILE_MAJOR_VERSION) {
                "Unsupported class file major version $classFileMajorVersion"
            }
            val kotlinVersion = kotlinMetadataVersion?.languageMinorVersion
            require(kotlinVersion == null || kotlinVersion == KOTLIN_1_2_VERSION || kotlinVersion == KOTLIN_1_9_VERSION) {
                "Unsupported Kotlin metadata version $kotlinMetadataVersion"
            }
        }

        override val isLegacyCompatible: Boolean
            get() = when {
                classFileMajorVersion > JAVA_8_CLASS_FILE_MAJOR_VERSION -> false
                kotlinMetadataVersion == null -> true  // Java 8 CorDapp is fine
                else -> kotlinMetadataVersion.languageMinorVersion == KOTLIN_1_2_VERSION
            }

        override val isNonLegacyCompatible: Boolean
            // Java-only CorDapp will always be compatible on 4.12
            get() = if (kotlinMetadataVersion == null) true else kotlinMetadataVersion.languageMinorVersion == KOTLIN_1_9_VERSION
    }
}
