package net.corda.node.services.config

data class EnterpriseConfiguration(val mutualExclusionConfiguration: MutualExclusionConfiguration)

data class MutualExclusionConfiguration(val on: Boolean = false, val machineName: String, val updateInterval: Long, val waitInterval: Long)