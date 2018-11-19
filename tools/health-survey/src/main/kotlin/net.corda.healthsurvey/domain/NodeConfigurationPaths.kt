package net.corda.healthsurvey.domain

import java.nio.file.Path
import java.nio.file.Paths

class NodeConfigurationPaths(val baseDirectory: Path, nodeConfigurationFileArg: Path? = null) {

    companion object {
        const val nodeConfigurationDefaultPath = "node.conf"
        const val driversDefaultPath = "drivers"
        const val additionalNodeInfosDefaultPath = "additional-node-infos"
        const val cordappsDefaultPath = "cordapps"
        const val networkParametersDefaultPath = "network-parameters"
        const val logsDefaultPath = "logs"
    }

    val nodeConfigurationFile = nodeConfigurationFileArg ?: baseDirectory / nodeConfigurationDefaultPath
    val drivers = baseDirectory / driversDefaultPath
    val additionalNodeInfos = baseDirectory / additionalNodeInfosDefaultPath
    val cordapps = baseDirectory / cordappsDefaultPath
    val networkParameters = baseDirectory / networkParametersDefaultPath
    val logs = baseDirectory / logsDefaultPath

    val requiredExisting = setOf(baseDirectory, nodeConfigurationFile)
}

private operator fun Path.div(other: Path): Path = resolve(other)

private operator fun Path.div(other: String): Path = resolve(Paths.get(other))
