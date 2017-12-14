@file:Suppress("UNUSED_PARAMETER")
@file:JvmName("CoreTestUtils")

package net.corda.testing

import net.corda.core.contracts.PartyAndReference
import net.corda.core.contracts.StateRef
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.entropyToKeyPair
import net.corda.core.crypto.generateKeyPair
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.internal.cert
import net.corda.core.internal.x500Name
import net.corda.core.node.NodeInfo
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.loggerFor
import net.corda.node.services.config.configureDevKeyAndTrustStores
import net.corda.nodeapi.internal.config.SSLConfiguration
import net.corda.nodeapi.internal.crypto.CertificateAndKeyPair
import net.corda.nodeapi.internal.crypto.CertificateType
import net.corda.nodeapi.internal.crypto.X509CertificateFactory
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.serialization.amqp.AMQP_ENABLED
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralSubtree
import org.bouncycastle.asn1.x509.NameConstraints
import org.bouncycastle.cert.X509CertificateHolder
import org.mockito.Mockito.mock
import org.mockito.internal.stubbing.answers.ThrowsException
import java.lang.reflect.Modifier
import java.math.BigInteger
import java.nio.file.Files
import java.security.KeyPair
import java.security.PublicKey
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

/**
 *  JAVA INTEROP
 *  ------------
 *
 *  Please keep the following points in mind when extending the Kotlin DSL:
 *
 *   - Annotate functions with Kotlin defaults with @JvmOverloads. This produces the relevant overloads for Java.
 *   - Void closures in arguments are inconvenient in Java, use overloading to define non-closure variants as well.
 *   - Top-level vals are trickier. *DO NOT USE @JvmField at the top level!* It's surprisingly easy to
 *     introduce a static init cycle because of the way Kotlin compiles top-level things, which can cause
 *     non-deterministic behaviour, including your field not being initialized at all! Instead opt for a proper Kotlin
 *     val either with a custom @JvmStatic get() or a lazy delegate if the initialiser has side-effects. See examples below.
 *   - Infix functions work as regular ones from Java, but symbols with spaces in them don't! Define a camelCase variant
 *     as well.
 *   - varargs are exposed as array types in Java. Define overloads for common cases.
 *   - The Int.DOLLARS syntax doesn't work from Java.  Use the DOLLARS(int) function instead.
 */

fun generateStateRef() = StateRef(SecureHash.randomSHA256(), 0)

private val freePortCounter = AtomicInteger(30000)
/**
 * Returns a localhost address with a free port.
 *
 * Unsafe for getting multiple ports!
 * Use [getFreeLocalPorts] for getting multiple ports.
 */
fun freeLocalHostAndPort() = NetworkHostAndPort("localhost", freePort())

/**
 * Returns a free port.
 *
 * Unsafe for getting multiple ports!
 * Use [getFreeLocalPorts] for getting multiple ports.
 */
fun freePort(): Int = freePortCounter.getAndAccumulate(0) { prev, _ -> 30000 + (prev - 30000 + 1) % 10000 }

/**
 * Creates a specified number of ports for use by the Node.
 *
 * Unlikely, but in the time between running this function and handing the ports
 * to the Node, some other process else could allocate the returned ports.
 */
fun getFreeLocalPorts(hostName: String, numberToAlloc: Int): List<NetworkHostAndPort> {
    val freePort = freePortCounter.getAndAccumulate(0) { prev, _ -> 30000 + (prev - 30000 + numberToAlloc) % 10000 }
    return (freePort until freePort + numberToAlloc).map { NetworkHostAndPort(hostName, it) }
}

fun configureTestSSL(legalName: CordaX500Name): SSLConfiguration = object : SSLConfiguration {
    override val certificatesDirectory = Files.createTempDirectory("certs")
    override val keyStorePassword: String get() = "cordacadevpass"
    override val trustStorePassword: String get() = "trustpass"

    init {
        configureDevKeyAndTrustStores(legalName)
    }
}
fun getTestPartyAndCertificate(party: Party): PartyAndCertificate {
    val trustRoot: X509CertificateHolder = DEV_TRUST_ROOT
    val intermediate: CertificateAndKeyPair = DEV_CA

    val nodeCaName = party.name.copy(commonName = X509Utilities.CORDA_CLIENT_CA_CN)
    val nameConstraints = NameConstraints(arrayOf(GeneralSubtree(GeneralName(GeneralName.directoryName, party.name.x500Name))), arrayOf())
    val issuerKeyPair = Crypto.generateKeyPair(Crypto.ECDSA_SECP256K1_SHA256)
    val issuerCertificate = X509Utilities.createCertificate(CertificateType.NODE_CA, intermediate.certificate, intermediate.keyPair, nodeCaName, issuerKeyPair.public,
            nameConstraints = nameConstraints)

    val certHolder = X509Utilities.createCertificate(CertificateType.WELL_KNOWN_IDENTITY, issuerCertificate, issuerKeyPair, party.name, party.owningKey)
    val pathElements = listOf(certHolder, issuerCertificate, intermediate.certificate, trustRoot)
    val certPath = X509CertificateFactory().generateCertPath(pathElements.map(X509CertificateHolder::cert))
    return PartyAndCertificate(certPath)
}

/**
 * Build a test party with a nonsense certificate authority for testing purposes.
 */
fun getTestPartyAndCertificate(name: CordaX500Name, publicKey: PublicKey): PartyAndCertificate {
    return getTestPartyAndCertificate(Party(name, publicKey))
}

class TestIdentity @JvmOverloads constructor(val name: CordaX500Name, entropy: Long? = null) {
    val keyPair: KeyPair = if (entropy != null) entropyToKeyPair(BigInteger.valueOf(entropy)) else generateKeyPair()
    val publicKey: PublicKey get() = keyPair.public
    val party: Party = Party(name, publicKey)
    val identity: PartyAndCertificate by lazy { getTestPartyAndCertificate(party) } // Often not needed.
    fun ref(vararg bytes: Byte): PartyAndReference = party.ref(*bytes)
}

@Suppress("unused")
inline fun <reified T : Any> T.kryoSpecific(reason: String, function: () -> Unit) = if (!AMQP_ENABLED) {
    function()
} else {
    loggerFor<T>().info("Ignoring Kryo specific test, reason: $reason")
}

@Suppress("unused")
inline fun <reified T : Any> T.amqpSpecific(reason: String, function: () -> Unit) = if (AMQP_ENABLED) {
    function()
} else {
    loggerFor<T>().info("Ignoring AMQP specific test, reason: $reason")
}

/**
 * Until we have proper handling of multiple identities per node, for tests we use the first identity as special one.
 * TODO: Should be removed after multiple identities are introduced.
 */
fun NodeInfo.chooseIdentityAndCert(): PartyAndCertificate = legalIdentitiesAndCerts.first()

fun NodeInfo.chooseIdentity(): Party = chooseIdentityAndCert().party
/**
 * Extract a single identity from the node info. Throws an error if the node has multiple identities.
 */
fun NodeInfo.singleIdentityAndCert(): PartyAndCertificate = legalIdentitiesAndCerts.single()

/**
 * Extract a single identity from the node info. Throws an error if the node has multiple identities.
 */
fun NodeInfo.singleIdentity(): Party = singleIdentityAndCert().party

/**
 * A method on a mock was called, but no behaviour was previously specified for that method.
 * You can use [com.nhaarman.mockito_kotlin.doReturn] or similar to specify behaviour, see Mockito documentation for details.
 */
class UndefinedMockBehaviorException(message: String) : RuntimeException(message)

inline fun <reified T : Any> rigorousMock() = rigorousMock(T::class.java)
/**
 * Create a Mockito mock that has [UndefinedMockBehaviorException] as the default behaviour of all abstract methods,
 * and [org.mockito.invocation.InvocationOnMock.callRealMethod] as the default for all concrete methods.
 * @param T the type to mock. Note if you want concrete methods of a Kotlin interface to be invoked,
 * it won't work unless you mock a (trivial) abstract implementation of that interface instead.
 */
fun <T> rigorousMock(clazz: Class<T>): T = mock(clazz) {
    if (Modifier.isAbstract(it.method.modifiers)) {
        // Use ThrowsException to hack the stack trace, and lazily so we can customise the message:
        ThrowsException(UndefinedMockBehaviorException("Please specify what should happen when '${it.method}' is called, or don't call it. Args: ${Arrays.toString(it.arguments)}")).answer(it)
    } else {
        it.callRealMethod()
    }
}
