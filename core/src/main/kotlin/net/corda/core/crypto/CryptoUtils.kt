package net.corda.core.crypto

import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey

/** Helper function for signing */
fun PrivateKey.sign(bytesToSign: ByteArray): ByteArray {
    return Crypto.doSign(this, bytesToSign)
}

/** Helper function to sign with a key pair */
fun KeyPair.sign(bytesToSign: ByteArray): ByteArray {
    return Crypto.doSign(this.private, bytesToSign)
}

/** Helper function to verify a signature */
fun PublicKey.verify(signatureData: ByteArray, clearData: ByteArray): Boolean {
    return Crypto.doVerify(this, signatureData, clearData)
}

/** Helper function for the signers to verify their own signatures */
fun KeyPair.verify(signatureData: ByteArray, clearData: ByteArray): Boolean {
    return Crypto.doVerify(this.public, signatureData, clearData)
}