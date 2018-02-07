package net.corda.core.utilities

import net.corda.core.crypto.SecureHash

/**
 * This sequence can be used for test/demos
 * TODO - add warning on startup!
 */
val whiteListHashes = setOf(SecureHash.zeroHash, SecureHash.allOnesHash)
val whitelistAllContractsForTest = mapOf("*" to whiteListHashes.toList())