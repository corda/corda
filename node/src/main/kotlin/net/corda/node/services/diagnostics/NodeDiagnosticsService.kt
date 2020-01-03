package net.corda.node.services.diagnostics

import net.corda.common.logging.CordaVersion
import net.corda.core.cordapp.Cordapp
import net.corda.core.cordapp.CordappInfo
import net.corda.core.node.NodeDiagnosticInfo
import net.corda.core.node.services.DiagnosticsService
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.node.internal.cordapp.CordappProviderInternal

class NodeDiagnosticsService(private val cordappProvider: CordappProviderInternal) : DiagnosticsService, SingletonSerializeAsToken() {

    override fun nodeDiagnosticInfo(): NodeDiagnosticInfo {
        return NodeDiagnosticInfo(
                version = CordaVersion.releaseVersion,
                revision = CordaVersion.revision,
                platformVersion = CordaVersion.platformVersion,
                vendor = CordaVersion.vendor,
                cordapps = cordappProvider.cordapps
                        .filter { !it.jarPath.toString().endsWith("corda-core-${CordaVersion.releaseVersion}.jar") }
                        .map {
                            CordappInfo(
                                    type = when (it.info) {
                                        is Cordapp.Info.Contract -> "Contract CorDapp"
                                        is Cordapp.Info.Workflow -> "Workflow CorDapp"
                                        else -> "CorDapp"
                                    },
                                    name = it.name,
                                    shortName = it.info.shortName,
                                    minimumPlatformVersion = it.minimumPlatformVersion,
                                    targetPlatformVersion = it.targetPlatformVersion,
                                    version = it.info.version,
                                    vendor = it.info.vendor,
                                    licence = it.info.licence,
                                    jarHash = it.jarHash)
                        }
        )
    }
}