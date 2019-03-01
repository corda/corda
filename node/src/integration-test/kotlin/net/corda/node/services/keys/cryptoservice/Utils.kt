package net.corda.node.services.keys.cryptoservice

import net.corda.core.crypto.internal.AliasPrivateKey
import net.corda.core.internal.declaredField
import net.corda.core.internal.inputStream
import net.corda.nodeapi.internal.DEV_CA_KEY_STORE_PASS
import net.corda.nodeapi.internal.crypto.KEYSTORE_TYPE
import sun.security.pkcs.PKCS8Key
import sun.security.util.DerValue
import sun.security.x509.AlgorithmId
import java.nio.file.Path
import java.security.InvalidKeyException
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * When we generate and store a private key in the HSM, we still have a file-based node key store that stores the certificates.
 * We store the certificates along with an [AliasPrivateKey] as the corresponding private key entry. With this utility we demonstrate
 * that this private key entry does indeed not contain any actual private key material.
 */
internal fun ensurePrivateKeyIsNotInKeyStoreFile(alias: String, nodeKeyStore: Path, keyStorePassword: String = DEV_CA_KEY_STORE_PASS) {
    val keyStore = KeyStore.getInstance(KEYSTORE_TYPE)
    keyStore.load(nodeKeyStore.inputStream(), keyStorePassword.toCharArray())

    // The private key from the file.
    val privateKey = keyStore.getKey(alias, keyStorePassword.toCharArray())

    // a new private key that should be identical.
    val aliasPrivateKey = PKCS8Key.parseKey(DerValue(AliasPrivateKey(alias).encoded))

    assertTrue(aliasPrivateKey.encoded.contentEquals(privateKey.encoded))
    // comparing the output of getEncoded() is not sufficient, because it is not necessarily a simple getter. Therefore we access the
    // actual fields that contain relevant data and make sure they are identical.
    assertTrue(privateKey.declaredField<ByteArray>("encodedKey").value.contentEquals(aliasPrivateKey.declaredField<ByteArray>("encodedKey").value))
    assertTrue(privateKey.declaredField<ByteArray>("key").value.contentEquals(aliasPrivateKey.declaredField<ByteArray>("key").value))
    assertEquals(privateKey.declaredField<AlgorithmId>("algid").value, aliasPrivateKey.declaredField<AlgorithmId>("algid").value)

    // Demonstrate that signing is not possible
    val ecSignature = Signature.getInstance("SHA256withECDSA")
    assertFailsWith<InvalidKeyException> { ecSignature.initSign(privateKey as PrivateKey) }
    // We don't really know what type of key it is at this point
    val rsaSignature = Signature.getInstance("SHA256withRSA")
    assertFailsWith<InvalidKeyException> { rsaSignature.initSign(privateKey as PrivateKey) }
}