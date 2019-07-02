package net.corda.core.node

import net.corda.core.cordapp.CordappDiagnosticInfo
import net.corda.core.serialization.CordaSerializable

@CordaSerializable
data class NodeDiagnosticInfo(val cordaVersionInfo: CordaVersionInfo,
                              val cordapps: List<CordappDiagnosticInfo>)