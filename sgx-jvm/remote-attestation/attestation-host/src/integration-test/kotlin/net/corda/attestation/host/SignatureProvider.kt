package net.corda.attestation.host

import net.corda.attestation.CryptoProvider
import net.corda.attestation.KEY_SIZE
import net.corda.attestation.toLittleEndian
import org.bouncycastle.asn1.ASN1InputStream
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.DLSequence
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.nio.ByteBuffer
import java.security.PrivateKey
import java.security.Signature
import java.security.interfaces.ECPublicKey

class SignatureProvider(private val cryptoProvider: CryptoProvider) : TestRule {
    override fun apply(stmt: Statement, description: Description): Statement {
        return stmt
    }

    fun signatureOf(signingKey: PrivateKey, localKey: ECPublicKey, remoteKey: ECPublicKey): ByteArray {
        val signature = Signature.getInstance("SHA256WithECDSA").let { signer ->
            signer.initSign(signingKey, cryptoProvider.crypto.random)
            signer.update(localKey.toLittleEndian())
            signer.update(remoteKey.toLittleEndian())
            signer.sign()
        }
        return ByteBuffer.allocate(KEY_SIZE).let { buf ->
            ASN1InputStream(signature).use { input ->
                for (number in input.readObject() as DLSequence) {
                    val pos = (number as ASN1Integer).positiveValue.toLittleEndian(KEY_SIZE / 2)
                    buf.put(pos)
                }
                buf.array()
            }
        }
    }
}