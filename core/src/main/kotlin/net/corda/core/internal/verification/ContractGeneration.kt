package net.corda.core.internal.verification

import net.corda.core.contracts.ComponentGroupEnum
import net.corda.core.contracts.ComponentGroupEnum.ATTACHMENTS_GROUP
import net.corda.core.contracts.ComponentGroupEnum.ATTACHMENTS_V2_GROUP
import net.corda.core.contracts.ComponentGroupEnum.ATTACHMENTS_V3_GROUP
import net.corda.core.internal.JAVA_17_CLASS_FILE_MAJOR_VERSION
import net.corda.core.internal.JAVA_25_CLASS_FILE_MAJOR_VERSION
import net.corda.core.internal.JAVA_8_CLASS_FILE_MAJOR_VERSION
import net.corda.core.internal.PlatformVersionSwitches

/**
 * Each Corda release which moves CorDapps onto a newer, incompatible, version of Kotlin and/or Java starts a new contract generation.
 *
 * The contract attachments of each generation are stored in their own attachments component group in the transaction. A transaction can
 * contain the attachments of several generations, which makes it verifiable by the nodes of each of those generations. This is needed for
 * as long as the network has nodes of an older generation, i.e. whilst the network's minimum platform version is below the
 * [platformVersion] of the next generation (see [isRequiredFor]).
 *
 * The generations are ordered oldest first. The last one is the [CURRENT] generation, which the node verifies in-process. The older
 * (legacy) generations are each verified by their own external verifier process, which is built with that generation's Kotlin version.
 * The node has a [legacyContractsDirName] directory for the contract CorDapps of each legacy generation, and an optional [legacyJarsDirName]
 * directory for any extra third-party JARs that generation's external verifier needs.
 *
 * To add a new generation (e.g. when Corda moves to a newer Kotlin and JDK):
 *  1. Add a new attachments component group to the end of [ComponentGroupEnum].
 *  2. Add a new platform version switch for the release.
 *  3. Add a new constant to the end of this enum, and give the previously current generation its legacy directory names.
 *  4. Build an external verifier for the previously current Kotlin version: add it to `LegacyKotlinVersion` in `buildSrc`, add its Kotlin
 *     version to the list in `settings.gradle` (which creates the `core-<version>`, `serialization-<version>` and `verifier-<version>`
 *     modules) and embed its verifier in the node (`node/build.gradle`).
 *
 * This is internal and its values are not stored anywhere (only the component group ordinals are), so it's safe to change as needed.
 */
enum class ContractGeneration(
        /** The component group containing this generation's attachments. */
        val attachmentsGroup: ComponentGroupEnum,
        /** The (major.minor) version of Kotlin this generation's CorDapps are compiled with. */
        val kotlinVersion: KotlinVersion,
        /** The newest Java class file version this generation supports. */
        val maxClassFileMajorVersion: Int,
        /** The platform version of the first release of this generation. */
        val platformVersion: Int,
        /** The Corda releases which make up this generation, used in messages. */
        val releases: String,
        /** The node directory for contract CorDapps of this generation, if it's a legacy generation. */
        val legacyContractsDirName: String?,
        /** The node directory for extra third-party JARs needed by this generation's external verifier, if it's a legacy generation. */
        val legacyJarsDirName: String?
) {
    CORDA_4_11(
            ATTACHMENTS_GROUP,
            KotlinVersion(1, 2),
            JAVA_8_CLASS_FILE_MAJOR_VERSION,
            PlatformVersionSwitches.FIRST_VERSION,
            "4.11 or earlier",
            "legacy-contracts",
            "legacy-jars"
    ),
    CORDA_4_12(
            ATTACHMENTS_V2_GROUP,
            KotlinVersion(1, 9),
            JAVA_17_CLASS_FILE_MAJOR_VERSION,
            PlatformVersionSwitches.LEGACY_ATTACHMENTS,
            "4.12 to 4.15",
            "legacy-contracts1.9",
            "legacy-jars1.9"
    ),
    CORDA_4_16(
            ATTACHMENTS_V3_GROUP,
            KotlinVersion(2, 4),
            JAVA_25_CLASS_FILE_MAJOR_VERSION,
            PlatformVersionSwitches.ATTACHMENTS_V3,
            "4.16 or later",
            null,
            null
    );

    /** The Kotlin version as "major.minor", which is also used to name this generation's external verifier. */
    val kotlinVersionString: String
        get() = "${kotlinVersion.major}.${kotlinVersion.minor}"

    val isCurrent: Boolean
        get() = this == CURRENT

    /** The generation after this one, or null if this is the [CURRENT] one. */
    val next: ContractGeneration?
        get() = if (isCurrent) null else values()[ordinal + 1]

    /**
     * Returns true if transactions on a network with the given minimum platform version need to include this generation's attachments,
     * i.e. there may be nodes on the network which are of this generation.
     */
    fun isRequiredFor(minimumPlatformVersion: Int): Boolean {
        val next = next ?: return true
        return minimumPlatformVersion < next.platformVersion
    }

    override fun toString(): String = "$name (Corda $releases, Kotlin $kotlinVersionString)"

    companion object {
        /** The generation of this node. */
        @JvmField
        val CURRENT: ContractGeneration = values().last()

        /** The generations older than [CURRENT], oldest first, which are verified by an external verifier. */
        @JvmField
        val LEGACY: List<ContractGeneration> = values().filter { it != CURRENT }

        /** Returns the generation which uses the given attachments component group, or null if it's not an attachments group. */
        @JvmStatic
        fun forAttachmentsGroup(group: ComponentGroupEnum): ContractGeneration? = values().firstOrNull { it.attachmentsGroup == group }
    }
}
