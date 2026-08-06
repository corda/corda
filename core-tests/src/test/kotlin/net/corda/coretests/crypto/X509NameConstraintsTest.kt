package net.corda.coretests.crypto

import net.corda.core.crypto.Crypto
import net.corda.core.identity.CordaX500Name
import net.corda.nodeapi.internal.crypto.CertificateType
import net.corda.nodeapi.internal.crypto.ContentSignerBuilder
import net.corda.nodeapi.internal.crypto.X509KeyStore
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.crypto.toJca
import net.corda.testing.internal.createDevIntermediateCaCertPath
import org.bouncycastle.asn1.ASN1EncodableVector
import org.bouncycastle.asn1.ASN1OctetString
import org.bouncycastle.asn1.DERSequence
import org.bouncycastle.asn1.DERTaggedObject
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralSubtree
import org.bouncycastle.asn1.x509.NameConstraints
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.Test
import java.security.UnrecoverableKeyException
import java.security.cert.CertPathValidator
import java.security.cert.CertPathValidatorException
import java.security.cert.PKIXParameters
import java.security.cert.X509Certificate
import javax.security.auth.x500.X500Principal
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class X509NameConstraintsTest {

    companion object {
        private const val storePassword = "storePassword"
        private const val keyPassword = "entryPassword"
    }

    private fun makeKeyStores(subjectName: X500Name, nameConstraints: NameConstraints): Pair<X509KeyStore, X509KeyStore> {
        val (rootCa, intermediateCa) = createDevIntermediateCaCertPath()

        val trustStore = X509KeyStore(storePassword).apply {
            setCertificate(X509Utilities.CORDA_ROOT_CA, rootCa.certificate)
        }

        val keyStore = X509KeyStore(storePassword).apply {
            val nodeCaKeyPair = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
            val nodeCaCert = X509Utilities.createCertificate(
                    CertificateType.NODE_CA,
                    intermediateCa.certificate,
                    intermediateCa.keyPair,
                    CordaX500Name("Corda Client CA", "R3 Ltd", "London", "GB").x500Principal,
                    nodeCaKeyPair.public,
                    nameConstraints = nameConstraints)
            val tlsKeyPair = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
            val tlsCert = X509Utilities.createCertificate(
                    CertificateType.TLS,
                    nodeCaCert,
                    nodeCaKeyPair,
                    X500Principal(subjectName.encoded),
                    tlsKeyPair.public)
            setPrivateKey(X509Utilities.CORDA_CLIENT_TLS, tlsKeyPair.private, listOf(tlsCert, nodeCaCert, intermediateCa.certificate, rootCa.certificate), keyPassword)
        }

        return Pair(keyStore, trustStore)
    }

    @Test(timeout=300_000)
	fun `illegal common name`() {
        val acceptableNames = listOf("CN=Bank A TLS, O=Bank A", "CN=Bank A")
                .map { GeneralSubtree(GeneralName(X500Name(it))) }.toTypedArray()

        val nameConstraints = NameConstraints(acceptableNames, null)
        val pathValidator = CertPathValidator.getInstance("PKIX")

        assertFailsWith(CertPathValidatorException::class) {
            val (keystore, trustStore) = makeKeyStores(X500Name("CN=Bank B"), nameConstraints)
            val params = PKIXParameters(trustStore.internal)
            params.isRevocationEnabled = false
            val certPath = X509Utilities.buildCertPath(keystore.getCertificateChain(X509Utilities.CORDA_CLIENT_TLS))
            pathValidator.validate(certPath, params)
        }

        assertTrue {
            val (keystore, trustStore) = makeKeyStores(X500Name("CN=Bank A TLS, O=Bank A"), nameConstraints)
            val params = PKIXParameters(trustStore.internal)
            params.isRevocationEnabled = false
            val certPath = X509Utilities.buildCertPath(keystore.getCertificateChain(X509Utilities.CORDA_CLIENT_TLS))
            pathValidator.validate(certPath, params)
            true
        }

        assertTrue {
            val (keystore, trustStore) = makeKeyStores(X500Name("CN=Bank A"), nameConstraints)
            val params = PKIXParameters(trustStore.internal)
            params.isRevocationEnabled = false
            val certPath = X509Utilities.buildCertPath(keystore.getCertificateChain(X509Utilities.CORDA_CLIENT_TLS))
            pathValidator.validate(certPath, params)
            true
        }
    }

    @Test(timeout=300_000)
    fun `x500 name with correct cn and extra attribute`() {
        // Do not use Security.addProvider(BouncyCastleProvider()) to avoid EdDSA signature disruption in other tests.
        Crypto.findProvider(BouncyCastleProvider.PROVIDER_NAME)
        val acceptableNames = listOf("CN=Bank A TLS, UID=", "O=Bank A")
                .map { GeneralSubtree(GeneralName(X500Name(it))) }.toTypedArray()

        val nameConstraints = NameConstraints(acceptableNames, null)
        val pathValidator = CertPathValidator.getInstance("PKIX", BouncyCastleProvider.PROVIDER_NAME)

        assertFailsWith(CertPathValidatorException::class) {
            val (keystore, trustStore) = makeKeyStores(X500Name("CN=Bank A"), nameConstraints)
            val params = PKIXParameters(trustStore.internal)
            params.isRevocationEnabled = false
            val certPath = X509Utilities.buildCertPath(keystore.getCertificateChain(X509Utilities.CORDA_CLIENT_TLS))
            pathValidator.validate(certPath, params)
        }

        assertFailsWith(CertPathValidatorException::class) {
            val (keystore, trustStore) = makeKeyStores(X500Name("CN=Bank A, UID=12345"), nameConstraints)
            val params = PKIXParameters(trustStore.internal)
            params.isRevocationEnabled = false
            val certPath = X509Utilities.buildCertPath(keystore.getCertificateChain(X509Utilities.CORDA_CLIENT_TLS))
            pathValidator.validate(certPath, params)
        }

        assertTrue {
            val (keystore, trustStore) = makeKeyStores(X500Name("CN=Bank A TLS, UID=, E=me@email.com, C=GB"), nameConstraints)
            val params = PKIXParameters(trustStore.internal)
            params.isRevocationEnabled = false
            val certPath = X509Utilities.buildCertPath(keystore.getCertificateChain(X509Utilities.CORDA_CLIENT_TLS))
            pathValidator.validate(certPath, params)
            true
        }

        assertTrue {
            val (keystore, trustStore) = makeKeyStores(X500Name("O=Bank A, UID=, E=me@email.com, C=GB"), nameConstraints)
            val params = PKIXParameters(trustStore.internal)
            params.isRevocationEnabled = false
            val certPath = X509Utilities.buildCertPath(keystore.getCertificateChain(X509Utilities.CORDA_CLIENT_TLS))
            pathValidator.validate(certPath, params)
            true
        }
    }

    @Test(timeout=300_000)
	fun `test private key retrieval`() {
        val acceptableNames = listOf("CN=Bank A TLS, UID=", "O=Bank A")
                .map { GeneralSubtree(GeneralName(X500Name(it))) }.toTypedArray()

        val nameConstraints = NameConstraints(acceptableNames, null)
        val (keystore, _) = makeKeyStores(X500Name("CN=Bank A"), nameConstraints)

        val privateKey = keystore.getPrivateKey(X509Utilities.CORDA_CLIENT_TLS, keyPassword)
        assertEquals(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME.algorithmName, privateKey.algorithm)

        assertFailsWith(UnrecoverableKeyException::class) {
            keystore.getPrivateKey(X509Utilities.CORDA_CLIENT_TLS, "gibberish")
        }
    }

    /**
     * Build the RAW DER of a Name Constraints extension value with:
     *   permittedSubtrees [0] = { directoryName: O=Bank A }   (one real permitted subtree)
     *   excludedSubtrees  [1] = {} (EMPTY)                     (the malformation: A1 00)
     *
     * Built by hand so the empty [1] is guaranteed present regardless of how BC's
     * NameConstraints object encodes an empty array.
     */
    private fun malformedNameConstraintsDer(): ByteArray {
        // permitted: one GeneralSubtree wrapping a directoryName GeneralName for O=Bank A
        val permittedSubtree = GeneralSubtree(GeneralName(X500Name("O=Bank A")))
        val permittedTagged = DERTaggedObject(false, 0, DERSequence(permittedSubtree))
        // excluded: explicit EMPTY sequence under context tag [1]  -> encodes as A1 00
        val emptyExcludedTagged = DERTaggedObject(false, 1, DERSequence())
        val nc = DERSequence(ASN1EncodableVector().apply {
            add(permittedTagged)
            add(emptyExcludedTagged)
        })
        return nc.getEncoded("DER")
    }

    /**
     * Mint a NODE_CA certificate carrying a GUARANTEED-malformed Name Constraints (empty [1]).
     *
     * Uses Corda's own helpers as far as possible:
     *   - X509Utilities.createPartialCertificate builds the NODE_CA builder (BasicConstraints,
     *     KeyUsage, EKU, SKI/AKI, Corda role, validity) exactly as production does, and returns
     *     the builder BEFORE signing.
     *   - We then inject the malformed Name Constraints via the byte[] addExtension overload,
     *     which stores the raw DER verbatim (no ASN.1 re-decode), so the empty [1] survives on
     *     every BC version and regardless of the Corda fix (which only affects the NameConstraints
     *     object path, not raw bytes).
     *   - ContentSignerBuilder signs, matching Corda's own createCertificate internals.
     *
     * This deliberately does NOT pass a NameConstraints object through createCertificate, because
     * the fix prevents an empty excludedSubtrees from ever being emitted that way.
     */
    private fun makeKeyStoresWithRawNameConstraints(subjectName: X500Name,
                                                    rawNameConstraintsValue: ByteArray): Pair<X509KeyStore, X509KeyStore> {
        val (rootCa, intermediateCa) = createDevIntermediateCaCertPath()

        val trustStore = X509KeyStore(storePassword).apply {
            setCertificate(X509Utilities.CORDA_ROOT_CA, rootCa.certificate)
        }

        val keyStore = X509KeyStore(storePassword).apply {
            val nodeCaKeyPair = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)

            // ---- NODE_CA: build via Corda's partial-cert helper, then inject the raw malformed NC.
            val nodeCaWindow = X509Utilities.getCertificateValidityWindow(
                    X509Utilities.DEFAULT_VALIDITY_WINDOW.first,
                    X509Utilities.DEFAULT_VALIDITY_WINDOW.second,
                    intermediateCa.certificate)
            val nodeCaBuilder = X509Utilities.createPartialCertificate(
                    CertificateType.NODE_CA,
                    intermediateCa.certificate.subjectX500Principal,
                    intermediateCa.certificate.publicKey,
                    CordaX500Name("Corda Client CA", "R3 Ltd", "London", "GB").x500Principal,
                    nodeCaKeyPair.public,
                    nodeCaWindow)
            // Raw bytes -> stored verbatim, empty [1] preserved regardless of BC version / Corda fix.
            nodeCaBuilder.addExtension(Extension.nameConstraints, true, rawNameConstraintsValue)

            // Sign with the intermediate CA key using Corda's own signer builder.
            val nodeCaScheme = Crypto.findSignatureScheme(intermediateCa.keyPair.private)
            val nodeCaSigner = ContentSignerBuilder.build(
                    nodeCaScheme, intermediateCa.keyPair.private, Crypto.findProvider(nodeCaScheme.providerName))
            val nodeCaCert = nodeCaBuilder.build(nodeCaSigner).toJca()

            // ---- TLS leaf under the malformed NODE_CA. This is a normal, well-formed leaf and can
            // use Corda's createCertificate directly (no malformation on the leaf).
            val tlsKeyPair = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
            val tlsCert = X509Utilities.createCertificate(
                    CertificateType.TLS,
                    nodeCaCert,
                    nodeCaKeyPair,
                    X500Principal(subjectName.encoded),
                    tlsKeyPair.public)

            setPrivateKey(X509Utilities.CORDA_CLIENT_TLS, tlsKeyPair.private,
                    listOf(tlsCert, nodeCaCert, intermediateCa.certificate, rootCa.certificate), keyPassword)
        }

        return Pair(keyStore, trustStore)
    }

    /** Extract the raw DER of the Name Constraints extension value (unwraps the OCTET STRING). */
    private fun nameConstraintsDer(cert: X509Certificate): ByteArray {
        val extVal = cert.getExtensionValue(Extension.nameConstraints.id)
                ?: error("certificate has no Name Constraints extension")
        return ASN1OctetString.getInstance(extVal).octets
    }

    /** The NODE_CA cert is the one carrying the Name Constraints extension. */
    private fun nodeCaCertOf(keystore: X509KeyStore): X509Certificate =
            keystore.getCertificateChain(X509Utilities.CORDA_CLIENT_TLS)
                    .single { it.getExtensionValue(Extension.nameConstraints.id) != null }

    /** True if the extension DER ends in an empty [1] sequence (A1 00). */
    private fun hasEmptyExcludedTail(der: ByteArray): Boolean =
            der.size >= 2 && der[der.size - 2] == 0xA1.toByte() && der[der.size - 1] == 0x00.toByte()


    // -------------------------------------------------------------------------------------
    // (CANARY): a GUARANTEED-malformed empty-excluded cert must still validate under the
    // default provider. Records which provider that is. If a future JDK hardens its Name
    // Constraints parser, this test fails and warns us that field certs will break on-platform.
    // -------------------------------------------------------------------------------------
    @Test(timeout = 300_000)
    fun `canary - malformed empty excluded name constraints is tolerated by the default provider`() {
        val (keystore, trustStore) = makeKeyStoresWithRawNameConstraints(
                X500Name("O=Bank A, C=GB"), malformedNameConstraintsDer())

        // Sanity: the NODE_CA cert genuinely carries the malformed empty [1] (DER tail A1 00).
        val der = nameConstraintsDer(nodeCaCertOf(keystore))
        assertTrue(hasEmptyExcludedTail(der),
                "sample cert is not malformed as intended; DER tail was " +
                        der.takeLast(4).joinToString(" ") { "%02X".format(it) })

        val pathValidator = CertPathValidator.getInstance("PKIX")
        val defaultProviderName = pathValidator.provider.name
        println("Default PKIX CertPathValidator provider = '$defaultProviderName' " +
                "(class ${pathValidator.provider.javaClass.name})")

        // Current expected state: default PKIX provider is SUN. If this flips, investigate.
        assertEquals("SUN", defaultProviderName,
                "Default PKIX provider is no longer 'SUN' - the tolerance guarantee this canary " +
                        "relies on may not hold; re-evaluate field-cert acceptance.")

        val params = PKIXParameters(trustStore.internal)
        params.isRevocationEnabled = false
        val certPath = X509Utilities.buildCertPath(keystore.getCertificateChain(X509Utilities.CORDA_CLIENT_TLS))

        try {
            pathValidator.validate(certPath, params)
        } catch (e: CertPathValidatorException) {
            throw AssertionError(
                    "CANARY TRIPPED: the default JDK provider ('$defaultProviderName') now REJECTS a " +
                            "certificate with an empty excludedSubtrees. The JDK has hardened its Name " +
                            "Constraints parser (like Bouncy Castle >= 1.85 did). Node CA certs issued before " +
                            "the Corda fix that still carry an empty excludedSubtrees will now fail PKIX path " +
                            "validation on the platform default provider too - i.e. at TLS handshake and node " +
                            "startup. Those certs must be reissued. Original failure: ${e.message}", e)
        }
    }


    // -------------------------------------------------------------------------------------
    // (FIX PROOF): a cert built by the Corda createCertificate path with omitted excluded
    // (the fix) validates cleanly under the BOUNCY CASTLE provider.
    // -------------------------------------------------------------------------------------
    @Test(timeout = 300_000)
    fun `fixed - corda-issued cert with omitted excluded validates under bouncy castle`() {
        Crypto.findProvider(BouncyCastleProvider.PROVIDER_NAME)

        val permitted = listOf("CN=Bank A TLS, UID=", "O=Bank A")
                .map { GeneralSubtree(GeneralName(X500Name(it))) }.toTypedArray()
        val wellFormed = NameConstraints(permitted, null)   // the fix: omit excluded

        val (keystore, trustStore) = makeKeyStores(X500Name("O=Bank A, C=GB"), wellFormed)

        // The emitted extension must be well-formed (no empty [1] tail).
        val der = nameConstraintsDer(nodeCaCertOf(keystore))
        assertNull(NameConstraints.getInstance(der).excludedSubtrees,
                "fixed cert unexpectedly still has an excludedSubtrees element")

        val pathValidator = CertPathValidator.getInstance("PKIX", BouncyCastleProvider.PROVIDER_NAME)
        val params = PKIXParameters(trustStore.internal)
        params.isRevocationEnabled = false
        val certPath = X509Utilities.buildCertPath(keystore.getCertificateChain(X509Utilities.CORDA_CLIENT_TLS))

        pathValidator.validate(certPath, params)   // must not throw
    }

    // -------------------------------------------------------------------------------------
    // BC rejects the GUARANTEED-malformed cert (locks in the regression trigger and
    // makes TEST 2's clean pass meaningful).
    // -------------------------------------------------------------------------------------
    @Test(timeout = 300_000)
    fun `malformed empty excluded cert is rejected by bouncy castle`() {
        Crypto.findProvider(BouncyCastleProvider.PROVIDER_NAME)

        val (keystore, trustStore) = makeKeyStoresWithRawNameConstraints(
                X500Name("O=Bank A, C=GB"), malformedNameConstraintsDer())

        // Confirm we really built a malformed cert.
        assertTrue(hasEmptyExcludedTail(nameConstraintsDer(nodeCaCertOf(keystore))),
                "sample cert is not malformed as intended")

        val pathValidator = CertPathValidator.getInstance("PKIX", BouncyCastleProvider.PROVIDER_NAME)
        val params = PKIXParameters(trustStore.internal)
        params.isRevocationEnabled = false
        val certPath = X509Utilities.buildCertPath(keystore.getCertificateChain(X509Utilities.CORDA_CLIENT_TLS))

        val ex = assertFailsWith(CertPathValidatorException::class) {
            pathValidator.validate(certPath, params)
        }
        val messages = generateSequence<Throwable>(ex) { it.cause }.mapNotNull { it.message }.joinToString(" | ")
        assertTrue(messages.contains("sequence may not be empty", ignoreCase = true) ||
                messages.contains("Name constraints extension could not be decoded", ignoreCase = true),
                "expected a malformed-NameConstraints failure but got: $messages")
    }

    // -------------------------------------------------------------------------------------
    // (ENCODING SHAPE, fix contract): NameConstraints(permitted, null) as passed through
    // the Corda createCertificate path must emit an extension with NO excludedSubtrees element.
    // (Pure structural check, no validator involved.)
    // -------------------------------------------------------------------------------------
    @Test(timeout = 300_000)
    fun `corda createCertificate omits excluded subtrees when null`() {
        val permitted = arrayOf(GeneralSubtree(GeneralName(X500Name("O=Bank A"))))
        val (ks, _) = makeKeyStores(X500Name("O=Bank A, C=GB"), NameConstraints(permitted, null))
        val parsed = NameConstraints.getInstance(nameConstraintsDer(nodeCaCertOf(ks)))
        assertNull(parsed.excludedSubtrees, "excludedSubtrees should be omitted when null is passed")
        assertNotNull(parsed.permittedSubtrees, "permittedSubtrees should be present")
    }


    // -------------------------------------------------------------------------------------
    // (CRITICALITY): the Name Constraints extension Corda emits is marked critical.
    // -------------------------------------------------------------------------------------
    @Test(timeout = 300_000)
    fun `name constraints extension is critical`() {
        val permitted = arrayOf(GeneralSubtree(GeneralName(X500Name("O=Bank A"))))
        val (ks, _) = makeKeyStores(X500Name("O=Bank A, C=GB"), NameConstraints(permitted, null))
        val criticalOids = nodeCaCertOf(ks).criticalExtensionOIDs
        assertNotNull(criticalOids, "certificate reports no critical extensions")
        assertTrue(criticalOids.contains(Extension.nameConstraints.id),
                "Name Constraints must be a critical extension; critical OIDs were: $criticalOids")
    }
}
