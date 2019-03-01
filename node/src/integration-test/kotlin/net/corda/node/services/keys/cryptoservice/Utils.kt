package net.corda.node.services.keys.cryptoservice

import net.corda.core.crypto.internal.AliasPrivateKey
import net.corda.core.internal.declaredField
import net.corda.core.internal.inputStream
import net.corda.nodeapi.internal.DEV_CA_KEY_STORE_PASS
import net.corda.nodeapi.internal.crypto.KEYSTORE_TYPE
import org.assertj.core.api.Assertions
import sun.security.pkcs.PKCS8Key
import sun.security.util.DerValue
import java.nio.file.Path
import java.security.KeyStore
import kotlin.test.assertTrue

internal fun ensurePrivateKeyIsNotInKeyStoreFile(alias: String, nodeKeyStore: Path, keyStorePassword: String = DEV_CA_KEY_STORE_PASS) {
    val keyStore = KeyStore.getInstance(KEYSTORE_TYPE)
    keyStore.load(nodeKeyStore.inputStream(), keyStorePassword.toCharArray())
    val privateKey = keyStore.getKey(alias, keyStorePassword.toCharArray())
    Assertions.assertThat(privateKey is AliasPrivateKey)
    val aliasPrivateKey = PKCS8Key.parseKey(DerValue(AliasPrivateKey(alias).encoded))
    assertTrue(aliasPrivateKey.encoded.contentEquals(privateKey.encoded))
    assertTrue(privateKey.declaredField<ByteArray>("encodedKey").value.contentEquals(aliasPrivateKey.declaredField<ByteArray>("encodedKey").value))
    assertTrue(privateKey.declaredField<ByteArray>("key").value.contentEquals(aliasPrivateKey.declaredField<ByteArray>("key").value))
}