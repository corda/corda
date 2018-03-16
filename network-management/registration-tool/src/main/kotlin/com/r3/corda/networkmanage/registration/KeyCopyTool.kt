package com.r3.corda.networkmanage.registration

import com.r3.corda.networkmanage.registration.ToolOption.KeyCopierOption
import net.corda.nodeapi.internal.crypto.X509KeyStore

/**
 * This utility will copy private key with [KeyCopierOption.srcAlias] from provided source keystore and copy it to target
 * keystore with the same alias, or [KeyCopierOption.destAlias] if provided.
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
    val srcKeystore = X509KeyStore.fromFile(srcPath, srcPass)
    val srcPrivateKey = srcKeystore.getPrivateKey(srcAlias)
    val srcCertChain = srcKeystore.getCertificateChain(srcAlias)

    X509KeyStore.fromFile(destPath, destPass).update {
        val keyAlias = destAlias ?: srcAlias
        setPrivateKey(keyAlias, srcPrivateKey, srcCertChain)
        println("Added '$keyAlias' to keystore : $destPath, the tool will now terminate.")
    }
}
