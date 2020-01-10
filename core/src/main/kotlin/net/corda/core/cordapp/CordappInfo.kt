package net.corda.core.cordapp

import net.corda.core.crypto.SecureHash
import net.corda.core.serialization.CordaSerializable

/**
 * A [CordappInfo] describes a single CorDapp currently installed on the node
 *
 * @param type A description of what sort of CorDapp this is - either a contract, workflow, or a combination.
 * @param name The name of the JAR file that defines the CorDapp
 * @param shortName The name of the CorDapp
 * @param minimumPlatformVersion The minimum platform version the node must be at for the CorDapp to run
 * @param targetPlatformVersion The target platform version this CorDapp has been tested against
 * @param version The version of this CorDapp
 * @param vendor The vendor of this CorDapp
 * @param licence The name of the licence this CorDapp is released under
 * @param jarHash The hash of the JAR file that defines this CorDapp
 */
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