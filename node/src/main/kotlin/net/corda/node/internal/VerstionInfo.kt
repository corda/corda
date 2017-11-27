package net.corda.node.internal

import com.google.inject.AbstractModule
import com.jcabi.manifests.Manifests
import net.corda.node.VersionInfo

private fun getVersionInfo(): VersionInfo {
    // Manifest properties are only available if running from the corda jar
    fun manifestValue(name: String): String? = if (Manifests.exists(name)) Manifests.read(name) else null

    return VersionInfo(
            manifestValue("Corda-Platform-Version")?.toInt() ?: 1,
            manifestValue("Corda-Release-Version") ?: "Unknown",
            manifestValue("Corda-Revision") ?: "Unknown",
            manifestValue("Corda-Vendor") ?: "Unknown"
    )
}

class VersionInfoModule : AbstractModule() {
    override fun configure() {
        bind(VersionInfo::class.java).to(getVersionInfo())
    }
}