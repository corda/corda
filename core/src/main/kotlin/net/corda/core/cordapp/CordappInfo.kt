package net.corda.core.cordapp

import net.corda.core.crypto.SecureHash
import net.corda.core.serialization.CordaSerializable

@CordaSerializable
data class CordappInfo(val type: String,
                       val name: String,
                       val shortName: String,
                       val minimumPlatformVersion: Int,
                       val targetPlatformVersion: Int,
                       val version: String,
                       val vendor: String,
                       val licence: String,
                       val jarHash: SecureHash.SHA256)