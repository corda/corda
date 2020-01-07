package net.corda.node.services.diagnostics

import net.corda.common.logging.CordaVersion
import net.corda.core.cordapp.Cordapp
import net.corda.core.cordapp.CordappInfo
import net.corda.core.node.NodeDiagnosticInfo
import net.corda.core.node.services.diagnostics.DiagnosticsService
import net.corda.core.node.services.diagnostics.NodeVersionInfo
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.node.internal.cordapp.CordappProviderInternal

class NodeDiagnosticsService() : DiagnosticsService, SingletonSerializeAsToken() {

    override fun nodeVersionInfo(): NodeVersionInfo {
        return NodeVersionInfo(
                releaseVersion = CordaVersion.releaseVersion,
                revision = CordaVersion.revision,
                platformVersion = CordaVersion.platformVersion,
                vendor = CordaVersion.vendor
        )
    }
}