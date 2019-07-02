package net.corda.core.cordapp

import net.corda.core.serialization.CordaSerializable

@CordaSerializable
data class CordappDiagnosticInfo(val name: String,
                                 val shortName: String,
                                 val minimumPlatformVersion: Int,
                                 val targetPlatformVersion: Int,
                                 val version: String,
                                 val vendor: String,
                                 val licence: String)