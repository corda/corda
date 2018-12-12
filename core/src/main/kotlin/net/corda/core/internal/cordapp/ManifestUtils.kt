package net.corda.core.internal.cordapp

import net.corda.core.cordapp.*
import net.corda.core.internal.cordapp.CordappImpl.Companion.CORDAPP_CONTRACT_LICENCE
import net.corda.core.internal.cordapp.CordappImpl.Companion.CORDAPP_CONTRACT_NAME
import net.corda.core.internal.cordapp.CordappImpl.Companion.CORDAPP_CONTRACT_VENDOR
import net.corda.core.internal.cordapp.CordappImpl.Companion.CORDAPP_CONTRACT_VERSION
import net.corda.core.internal.cordapp.CordappImpl.Companion.CORDAPP_WORKFLOW_LICENCE
import net.corda.core.internal.cordapp.CordappImpl.Companion.CORDAPP_WORKFLOW_NAME
import net.corda.core.internal.cordapp.CordappImpl.Companion.CORDAPP_WORKFLOW_VENDOR
import net.corda.core.internal.cordapp.CordappImpl.Companion.CORDAPP_WORKFLOW_VERSION
import net.corda.core.internal.cordapp.CordappImpl.Companion.DEFAULT_CORDAPP_VERSION
import net.corda.core.internal.cordapp.CordappImpl.Companion.MIN_PLATFORM_VERSION
import net.corda.core.internal.cordapp.CordappImpl.Companion.TARGET_PLATFORM_VERSION
import net.corda.core.internal.cordapp.CordappImpl.Companion.UNKNOWN_VALUE
import java.util.jar.Attributes
import java.util.jar.Manifest

operator fun Manifest.set(key: String, value: String): String? {
    return mainAttributes.putValue(key, value)
}

operator fun Manifest.set(key: Attributes.Name, value: String): Any? {
    return mainAttributes.put(key, value)
}

operator fun Manifest.get(key: String): String? = mainAttributes.getValue(key)

val Manifest.targetPlatformVersion: Int
    get() {
        val minPlatformVersion = this[MIN_PLATFORM_VERSION]?.toIntOrNull() ?: 1
        return this[TARGET_PLATFORM_VERSION]?.toIntOrNull() ?: minPlatformVersion
    }

fun Manifest.toCordappInfo(defaultName: String): Cordapp.Info {

    /** Common attributes */
    val minPlatformVersion = this[MIN_PLATFORM_VERSION]?.toIntOrNull() ?: 1
    val targetPlatformVersion = this[TARGET_PLATFORM_VERSION]?.toIntOrNull() ?: minPlatformVersion

    /** new identifiers (Corda 4) */
    // is it a Contract Jar?
    val contractInfo =
        if (this[CORDAPP_CONTRACT_NAME] != null) {
            Contract(shortName = this[CORDAPP_CONTRACT_NAME] ?: defaultName,
                    vendor = this[CORDAPP_CONTRACT_VENDOR] ?: UNKNOWN_VALUE,
                    version = this[CORDAPP_CONTRACT_VERSION] ?: DEFAULT_CORDAPP_VERSION.toString(),
                    licence = this[CORDAPP_CONTRACT_LICENCE] ?: UNKNOWN_VALUE,
                    minimumPlatformVersion = minPlatformVersion,
                    targetPlatformVersion = targetPlatformVersion
            )
        } else null

    // is it a Workflow (flows and services) Jar?
    val workflowInfo =
        if (this[CORDAPP_WORKFLOW_NAME] != null) {
            Workflow(shortName = this[CORDAPP_WORKFLOW_NAME] ?: defaultName,
                    vendor = this[CORDAPP_WORKFLOW_VENDOR] ?: UNKNOWN_VALUE,
                    version = this[CORDAPP_WORKFLOW_VERSION] ?: DEFAULT_CORDAPP_VERSION.toString(),
                    licence = this[CORDAPP_WORKFLOW_LICENCE] ?: UNKNOWN_VALUE,
                    minimumPlatformVersion = minPlatformVersion,
                    targetPlatformVersion = targetPlatformVersion
            )
        } else null

    // combined Contract and Workflow Jar ?
    if (contractInfo != null && workflowInfo != null) {
        return ContractAndWorkflow(contractInfo, workflowInfo, "", "", 0, "", minPlatformVersion, targetPlatformVersion)
    }
    else if (contractInfo != null) return contractInfo
    else if (workflowInfo != null) return workflowInfo

    /** need to maintain backwards compatibility so use old identifiers if existent */
    val shortName = this["Name"] ?: defaultName
    val vendor = this["Implementation-Vendor"] ?: UNKNOWN_VALUE
    val version = this["Implementation-Version"] ?: UNKNOWN_VALUE
    return Default(
            shortName = shortName,
            vendor = vendor,
            version = version,
            licence = UNKNOWN_VALUE,
            minimumPlatformVersion = minPlatformVersion,
            targetPlatformVersion = targetPlatformVersion
    )
}

