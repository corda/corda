package net.corda.core.node

import net.corda.core.cordapp.CordappInfo
import net.corda.core.serialization.CordaSerializable

@CordaSerializable
data class NodeDiagnosticInfo(val version: String,
                              val revision: String,
                              val platformVersion: Int,
                              val vendor: String,
                              val cordapps: List<CordappInfo>)