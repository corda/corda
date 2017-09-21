package net.corda.core.node

import net.corda.core.identity.CordaX500Name
import java.nio.file.Path

/**
 * Encapsulates information about local node configuration.
 */
interface LocalNodeConfiguration {
    /**
     * Base directory of the node.
     */
    val baseDirectory: Path
    /**
     * Node's legal name.
     */
    val myLegalName: CordaX500Name
    /**
     * Minimum platform version supported.
     */
    val minimumPlatformVersion: Int
}