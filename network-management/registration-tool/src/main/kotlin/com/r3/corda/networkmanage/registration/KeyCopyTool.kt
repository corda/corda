package com.r3.corda.networkmanage.registration

import com.r3.corda.networkmanage.registration.ToolOption.KeyCopierOption
import net.corda.nodeapi.internal.crypto.X509KeyStore

/**
 * This utility will copy private key with [KeyCopierOption.sourceAlias] from provided source keystore and copy it to target
 * keystore with the same alias, or [KeyCopierOption.destinationAlias] if provided.
 *
 * This tool uses Corda's security provider which support EdDSA keys.
 */
fun KeyCopierOption.copyKeystore() {
    println("**************************************")
    println("*                                    *")
    println("*           Key copy tool            *")
    println("*                                    *")
    println("**************************************")
    println()
    println("This utility will copy private key with [srcalias] from provided source keystore and copy it to target keystore with the same alias, or [destalias] if provided.")
    println()

    // Read private key and certificates from notary identity keystore.
    val srcKeystore = X509KeyStore.fromFile(sourceFile, sourcePassword ?: readPassword("Source key store password:"))
    val srcPrivateKey = srcKeystore.getPrivateKey(sourceAlias)
    val srcCertChain = srcKeystore.getCertificateChain(sourceAlias)

    X509KeyStore.fromFile(destinationFile, destinationPassword ?: readPassword("Destination key store password:")).update {
        val keyAlias = destinationAlias ?: sourceAlias
        setPrivateKey(keyAlias, srcPrivateKey, srcCertChain)
        println("Added '$keyAlias' to keystore : $destinationFile")
    }
}
