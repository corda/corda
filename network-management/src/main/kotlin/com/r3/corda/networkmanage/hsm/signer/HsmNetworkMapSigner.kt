package com.r3.corda.networkmanage.hsm.signer

import com.r3.corda.networkmanage.common.signer.Signer
import com.r3.corda.networkmanage.common.utils.CORDA_NETWORK_MAP
import com.r3.corda.networkmanage.hsm.authentication.Authenticator
import com.r3.corda.networkmanage.hsm.utils.HsmX509Utilities
import com.r3.corda.networkmanage.hsm.utils.HsmX509Utilities.getAndInitializeKeyStore
import com.r3.corda.networkmanage.hsm.utils.HsmX509Utilities.verify
import net.corda.core.internal.DigitalSignatureWithCert
import net.corda.nodeapi.internal.crypto.getX509Certificate
import java.security.PrivateKey
import java.security.Signature

/**
 * Signer which connects to a HSM using the given [authenticator] to sign bytes.
 */
// TODO Rename this to HsmSigner
class HsmNetworkMapSigner(private val privateKeyPassword: String,
                          private val authenticator: Authenticator) : Signer {
    /**
     * Signs given data using [CryptoServerJCE.CryptoServerProvider], which connects to the underlying HSM.
     */
    override fun signBytes(data: ByteArray): DigitalSignatureWithCert {
        return authenticator.connectAndAuthenticate { provider, _ ->
            val keyStore = getAndInitializeKeyStore(provider)
            val certificate = keyStore.getX509Certificate(CORDA_NETWORK_MAP)
            // Don't worry this is not a real private key but a pointer to one that resides in the HSM. It only works
            // when used with the given provider.
            val key = keyStore.getKey(CORDA_NETWORK_MAP, privateKeyPassword.toCharArray()) as PrivateKey
            val signature = Signature.getInstance(HsmX509Utilities.SIGNATURE_ALGORITHM, provider).run {
                initSign(key)
                update(data)
                sign()
            }
            verify(data, signature, certificate.publicKey)
            DigitalSignatureWithCert(certificate, signature)
        }
    }
}
