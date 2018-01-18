package com.r3.corda.networkmanage.hsm.signer

import com.r3.corda.networkmanage.common.signer.Signer
import com.r3.corda.networkmanage.hsm.authentication.Authenticator
import com.r3.corda.networkmanage.hsm.utils.X509Utilities
import com.r3.corda.networkmanage.hsm.utils.X509Utilities.getAndInitializeKeyStore
import com.r3.corda.networkmanage.hsm.utils.X509Utilities.verify
import net.corda.core.internal.DigitalSignatureWithCert
import net.corda.nodeapi.internal.crypto.getX509Certificate
import java.security.PrivateKey
import java.security.Signature

/**
 * Signer which connects to a HSM using the given [authenticator] to sign bytes.
 */
// TODO Rename this to HsmSigner
class HsmNetworkMapSigner(private val certificateKeyName: String,
                          private val privateKeyPassword: String,
                          private val authenticator: Authenticator) : Signer {
    /**
     * Signs given data using [CryptoServerJCE.CryptoServerProvider], which connects to the underlying HSM.
     */
    override fun signBytes(data: ByteArray): DigitalSignatureWithCert {
        return authenticator.connectAndAuthenticate { provider, _ ->
            val keyStore = getAndInitializeKeyStore(provider)
            val certificate = keyStore.getX509Certificate(certificateKeyName)
            // Don't worry this is not a real private key but a pointer to one that resides in the HSM. It only works
            // when used with the given provider.
            val key = keyStore.getKey(certificateKeyName, privateKeyPassword.toCharArray()) as PrivateKey
            val signature = Signature.getInstance(X509Utilities.SIGNATURE_ALGORITHM, provider).run {
                initSign(key)
                update(data)
                sign()
            }
            verify(data, signature, certificate.publicKey)
            DigitalSignatureWithCert(certificate, signature)
        }
    }
}
