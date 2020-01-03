package net.corda.core.node.services

import net.corda.core.node.NodeDiagnosticInfo

/**
 * A [DiagnosticsService] provides APIs that allow CorDapps to query information about the node that CorDapp is currently running on.
 */
interface DiagnosticsService {

    /**
     * Retrieve information about the current node version and the current available CorDapps.
     *
     * @return [NodeDiagnosticInfo] A structure holding information about the current node version.
     */
    fun nodeDiagnosticInfo() : NodeDiagnosticInfo
}