package net.corda.node.services.keys

import net.corda.core.node.services.KeyManagementService
import java.security.KeyPair

interface KeyManagementServiceInternal : KeyManagementService {
    fun start(initialKeyPairs: Set<KeyPair>)
}
