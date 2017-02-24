package net.corda.demobench.model

import java.nio.file.Path

/**
 * Temporary NodeConfig object that can be deleted completely from the filesystem.
 */
class TempConfig(
    val baseDir: Path,
    legalName: String,
    artemisPort: Int,
    nearestCity: String,
    webPort: Int,
    h2Port: Int,
    extraServices: List<String>,
    users: List<User>
) : NodeConfig(baseDir, legalName, artemisPort, nearestCity, webPort, h2Port, extraServices, users) {

    fun deleteBaseDir(): Boolean = baseDir.toFile().deleteRecursively()

}
