package net.corda.core.internal.cordapp

data class KotlinMetadataVersion(val major: Int, val minor: Int, val patch: Int = 0) : Comparable<KotlinMetadataVersion> {
    companion object {
        fun from(versionArray: IntArray): KotlinMetadataVersion {
            val (major, minor, patch) = versionArray
            return KotlinMetadataVersion(major, minor, patch)
        }
    }

    init {
        require(major >= 0) { "Major version should be not less than 0" }
        require(minor >= 0) { "Minor version should be not less than 0" }
        require(patch >= 0) { "Patch version should be not less than 0" }
    }

    /**
     * Returns the equivalent [KotlinVersion] without the patch.
     */
    val languageMinorVersion: KotlinVersion
        // See `kotlinx.metadata.jvm.JvmMetadataVersion`
        get() = if (major == 1 && minor == 1) KotlinVersion(1, 2) else KotlinVersion(major, minor)

    override fun compareTo(other: KotlinMetadataVersion): Int {
        val majors = this.major.compareTo(other.major)
        if (majors != 0) return majors
        val minors = this.minor.compareTo(other.minor)
        return if (minors != 0) minors else this.patch.compareTo(other.patch)
    }

    override fun toString(): String = "$major.$minor.$patch"
}
