package net.corda.core.node

import net.corda.core.identity.CordaX500Name
import java.nio.file.Path

interface LocalNodeConfiguration {

    val baseDirectory: Path
    val myLegalName: CordaX500Name
    val minimumPlatformVersion: Int
}