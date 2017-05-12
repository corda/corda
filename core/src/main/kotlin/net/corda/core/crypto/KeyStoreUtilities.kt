package net.corda.core.crypto

import net.corda.core.exists
import net.corda.core.read
import net.corda.core.write
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Path
import java.security.*
import java.security.cert.Certificate
import java.security.cert.X509Certificate

object KeyStoreUtilities {
    val KEYSTORE_TYPE = "JKS"

    /**
     * Helper method to either open an existing keystore for modification, or create a new blank keystore.
     * @param keyStoreFilePath location of KeyStore file
     * @param storePassword password to open the store. This does not have to be the same password as any keys stored,
     * but for SSL purposes this is recommended.
     * @return returns the KeyStore opened/created
     */
    fun loadOrCreateKeyStore(keyStoreFilePath: Path, storePassword: String): KeyStore {
        val pass = storePassword.toCharArray()
        val keyStore = KeyStore.getInstance(KEYSTORE_TYPE)
        if (keyStoreFilePath.exists()) {
            keyStoreFilePath.read { keyStore.load(it, pass) }
        } else {
            keyStore.load(null, pass)
            keyStoreFilePath.write { keyStore.store(it, pass) }
        }
        return keyStore
    }

    /**
     * Helper method to open an existing keystore for modification/read
     * @param keyStoreFilePath location of KeyStore file which must exist, or this will throw FileNotFoundException
     * @param storePassword password to open the store. This does not have to be the same password as any keys stored,
     * but for SSL purposes this is recommended.
     * @return returns the KeyStore opened
     * @throws IOException if there was an error reading the key store from the file.
     * @throws KeyStoreException if the password is incorrect or the key store is damaged.
     */
    @Throws(KeyStoreException::class, IOException::class)
    fun loadKeyStore(keyStoreFilePath: Path, storePassword: String): KeyStore {
        return keyStoreFilePath.read { loadKeyStore(it, storePassword) }
    }

    /**
     * Helper method to open an existing keystore for modification/read
     * @param input stream containing a KeyStore e.g. loaded from a resource file
     * @param storePassword password to open the store. This does not have to be the same password as any keys stored,
     * but for SSL purposes this is recommended.
     * @return returns the KeyStore opened
     * @throws IOException if there was an error reading the key store from the stream.
     * @throws KeyStoreException if the password is incorrect or the key store is damaged.
     */
    @Throws(KeyStoreException::class, IOException::class)
    fun loadKeyStore(input: InputStream, storePassword: String): KeyStore {
        val pass = storePassword.toCharArray()
        val keyStore = KeyStore.getInstance(KEYSTORE_TYPE)
        input.use {
            keyStore.load(input, pass)
        }
        return keyStore
    }
}

/**
 * Helper extension method to add, or overwrite any key data in store
 * @param alias name to record the private key and certificate chain under
 * @param key cryptographic key to store
 * @param password password for unlocking the key entry in the future. This does not have to be the same password as any keys stored,
 * but for SSL purposes this is recommended.
 * @param chain the sequence of certificates starting with the public key certificate for this key and extending to the root CA cert
 */
fun KeyStore.addOrReplaceKey(alias: String, key: Key, password: CharArray, chain: Array<Certificate>) {
    if (containsAlias(alias)) {
        this.deleteEntry(alias)
    }
    this.setKeyEntry(alias, key, password, chain)
}

/**
 * Helper extension method to add, or overwrite any public certificate data in store
 * @param alias name to record the public certificate under
 * @param cert certificate to store
 */
fun KeyStore.addOrReplaceCertificate(alias: String, cert: Certificate) {
    if (containsAlias(alias)) {
        this.deleteEntry(alias)
    }
    this.setCertificateEntry(alias, cert)
}


/**
 * Helper method save KeyStore to storage
 * @param keyStoreFilePath the file location to save to
 * @param storePassword password to access the store in future. This does not have to be the same password as any keys stored,
 * but for SSL purposes this is recommended.
 */
fun KeyStore.save(keyStoreFilePath: Path, storePassword: String) = keyStoreFilePath.write { store(it, storePassword) }

fun KeyStore.store(out: OutputStream, password: String) = store(out, password.toCharArray())


/**
 * Extract public and private keys from a KeyStore file assuming storage alias is known.
 * @param keyPassword Password to unlock the private key entries
 * @param alias The name to lookup the Key and Certificate chain from
 * @return The KeyPair found in the KeyStore under the specified alias
 */
fun KeyStore.getKeyPair(alias: String, keyPassword: String): KeyPair = getCertificateAndKey(alias, keyPassword).keyPair

/**
 * Helper method to load a Certificate and KeyPair from their KeyStore.
 * The access details should match those of the createCAKeyStoreAndTrustStore call used to manufacture the keys.
 * @param keyPassword The password for the PrivateKey (not the store access password)
 * @param alias The name to search for the data. Typically if generated with the methods here this will be one of
 * CERT_PRIVATE_KEY_ALIAS, ROOT_CA_CERT_PRIVATE_KEY_ALIAS, INTERMEDIATE_CA_PRIVATE_KEY_ALIAS defined above
 */
fun KeyStore.getCertificateAndKey(alias: String, keyPassword: String): CertificateAndKey {
    val keyPass = keyPassword.toCharArray()
    val key = getKey(alias, keyPass) as PrivateKey
    val cert = getCertificate(alias) as X509Certificate
    // Using Crypto.decodePublicKey to convert X509Key to bouncy castle public key implementation.
    // Using Crypto.decodePrivateKey to convert sun provider key implementation to bouncy castle private key implementation.
    return CertificateAndKey(cert, KeyPair(Crypto.decodePublicKey(cert.publicKey.encoded), Crypto.decodePrivateKey(key.encoded)))
}

/**
 * Extract public X509 certificate from a KeyStore file assuming storage alias is know
 * @param alias The name to lookup the Key and Certificate chain from
 * @return The X509Certificate found in the KeyStore under the specified alias
 */
fun KeyStore.getX509Certificate(alias: String): X509Certificate = getCertificate(alias) as X509Certificate
