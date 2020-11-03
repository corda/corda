@file:Suppress("UNUSED_PARAMETER")
@file:JvmName("TestUtils")

package net.corda.testing.core

import net.corda.core.contracts.PartyAndReference
import net.corda.core.contracts.StateRef
import net.corda.core.crypto.*
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.internal.unspecifiedCountry
import net.corda.core.node.NodeInfo
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.millis
import net.corda.nodeapi.internal.createDevNodeCa
import net.corda.nodeapi.internal.crypto.CertificateAndKeyPair
import net.corda.nodeapi.internal.crypto.CertificateType
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.coretesting.internal.DEV_INTERMEDIATE_CA
import net.corda.coretesting.internal.DEV_ROOT_CA
import java.math.BigInteger
import java.security.KeyPair
import java.security.PublicKey
import java.security.cert.X509Certificate
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.fail

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

/** Returns a fake state reference for testing purposes. **/
fun generateStateRef(): StateRef = StateRef(SecureHash.randomSHA256(), 0)

private val freePortCounter = AtomicInteger(30000)

/**
 * Returns a localhost address with a free port.
 *
 * Unsafe for getting multiple ports!
 * Use [getFreeLocalPorts] for getting multiple ports.
 */
@Suppress("DEPRECATION")
@Deprecated("Returned port is not guaranteed to be free when used, which can result in flaky tests. Instead use a port " +
        "range that's unlikely to be used by the rest of the system, such as PortAllocation.Incremental(10000).")
fun freeLocalHostAndPort(): NetworkHostAndPort = NetworkHostAndPort("localhost", freePort())

/**
 * Returns a free port.
 *
 * Unsafe for getting multiple ports!
 * Use [getFreeLocalPorts] for getting multiple ports.
 */
@Deprecated("Returned port is not guaranteed to be free when used, which can result in flaky tests. Instead use a port " +
        "range that's unlikely to be used by the rest of the system, such as PortAllocation.Incremental(10000).")
fun freePort(): Int = freePortCounter.getAndAccumulate(0) { prev, _ -> 30000 + (prev - 30000 + 1) % 10000 }

/**
 * Creates a specified number of ports for use by the Node.
 *
 * Unlikely, but in the time between running this function and handing the ports
 * to the Node, some other process else could allocate the returned ports.
 */
@Deprecated("Returned port is not guaranteed to be free when used, which can result in flaky tests. Instead use a port " +
        "range that's unlikely to be used by the rest of the system, such as PortAllocation.Incremental(10000).")
fun getFreeLocalPorts(hostName: String, numberToAlloc: Int): List<NetworkHostAndPort> {
    val freePort = freePortCounter.getAndAccumulate(0) { prev, _ -> 30000 + (prev - 30000 + numberToAlloc) % 10000 }
    return (0 until numberToAlloc).map { NetworkHostAndPort(hostName, freePort + it) }
}

fun getTestPartyAndCertificate(party: Party): PartyAndCertificate {
    val trustRoot: X509Certificate = DEV_ROOT_CA.certificate
    val intermediate: CertificateAndKeyPair = DEV_INTERMEDIATE_CA

    val (nodeCaCert, nodeCaKeyPair) = createDevNodeCa(intermediate, party.name)

    val identityCert = X509Utilities.createCertificate(
            CertificateType.LEGAL_IDENTITY,
            nodeCaCert,
            nodeCaKeyPair,
            party.name.x500Principal,
            party.owningKey)

    val certPath = X509Utilities.buildCertPath(identityCert, nodeCaCert, intermediate.certificate, trustRoot)
    return PartyAndCertificate(certPath)
}

/**
 * Build a test party with a nonsense certificate authority for testing purposes.
 */
fun getTestPartyAndCertificate(name: CordaX500Name, publicKey: PublicKey): PartyAndCertificate {
    return getTestPartyAndCertificate(Party(name, publicKey))
}


private val count = AtomicInteger(0)
/**
 * Randomise a party name to avoid clashes with other tests.
 */
fun makeUnique(name: CordaX500Name) = name.copy(commonName =
    if (name.commonName == null) {
        count.incrementAndGet().toString()
    } else {
        "${ name.commonName }_${ count.incrementAndGet() }"
    })

/**
 * A class that encapsulates a test identity containing a [CordaX500Name] and a [KeyPair], alongside a range
 * of utility methods for use during testing.
 */
class TestIdentity(val name: CordaX500Name, val keyPair: KeyPair) {
    companion object {
        /**
         * Creates an identity that won't equal any other. This is mostly useful as a throwaway for test helpers.
         * @param organisation the organisation part of the new identity's name.
         */
        @JvmStatic
        @JvmOverloads
        fun fresh(organisation: String, signatureScheme: SignatureScheme = Crypto.DEFAULT_SIGNATURE_SCHEME): TestIdentity {
            val keyPair = Crypto.generateKeyPair(signatureScheme)
            val name = CordaX500Name(organisation, keyPair.public.toStringShort(), CordaX500Name.unspecifiedCountry)
            return TestIdentity(name, keyPair)
        }
    }

    /** Creates an identity with a deterministic [keyPair] i.e. same [entropy] same keyPair. */
    @JvmOverloads constructor(name: CordaX500Name, entropy: Long, signatureScheme: SignatureScheme = Crypto.DEFAULT_SIGNATURE_SCHEME)
            : this(name, Crypto.deriveKeyPairFromEntropy(signatureScheme, BigInteger.valueOf(entropy)))

    /** Creates an identity with the given name and a fresh keyPair. */
    @JvmOverloads constructor(name: CordaX500Name, signatureScheme: SignatureScheme = Crypto.DEFAULT_SIGNATURE_SCHEME)
            : this(name, Crypto.generateKeyPair(signatureScheme))

    val publicKey: PublicKey get() = keyPair.public
    val party: Party = Party(name, publicKey)
    val identity: PartyAndCertificate by lazy { getTestPartyAndCertificate(party) } // Often not needed.

    /** Returns a [PartyAndReference] for this identity and the given reference. */
    fun ref(vararg bytes: Byte): PartyAndReference = party.ref(*bytes)
}

/**
 * Extract a single identity from the node info. Throws an error if the node has multiple identities.
 */
fun NodeInfo.singleIdentityAndCert(): PartyAndCertificate = legalIdentitiesAndCerts.single()

/**
 * Extract a single identity from the node info. Throws an error if the node has multiple identities.
 */
fun NodeInfo.singleIdentity(): Party = singleIdentityAndCert().party

/**
 * Executes a test action, if test fails then it retries with a small delay until test succeeds or the timeout expires.
 * Useful in cases when a the action side effect is not immediately observable and may take a ONLY few seconds.
 * Which will allow the make the tests more deterministic instead of relaying on thread sleeping before asserting the side effects.
 *
 * Don't use with the large timeouts.
 *
 * Example usage:
 *
 * executeTest(5.seconds) {
 *      val result = cut.getWaitingFlows(WaitingFlowQuery(counterParties = listOf(bobParty, daveParty)))
 *      assertEquals(1, result.size)
 *      assertEquals(daveStart.id, result.first().id)
 *      assertNull(result.first().externalOperationImplName)
 *      assertEquals(WaitingSource.RECEIVE, result.first().source)
 *      assertEquals(1, result.first().waitingForParties.size)
 *      assertEquals(DAVE_NAME, result.first().waitingForParties.first().party.name)
 * }
 *
 * The above will test our expectation that the getWaitingFlows action was executed successfully considering
 * that it may take a few hundreds of milliseconds for the flow state machine states to settle.
 */
@Suppress("TooGenericExceptionCaught", "MagicNumber", "ComplexMethod")
fun <T> executeTest(
        timeout: Duration,
        cleanup: (() -> Unit)? = null,
        retryDelay: Duration = 50.millis,
        block: () -> T
): T {
    val end = Instant.now().plus(timeout)
    var lastException: Throwable?
    do {
        try {
            val result = block()
            try {
                cleanup?.invoke()
            } catch (e: Throwable) {
                // Intentional
            }
            return result
        } catch (e: Throwable) {
            lastException = e
        }
        Thread.sleep(retryDelay.toMillis())
        val now = Instant.now()
    } while (now < end)
    try {
        cleanup?.invoke()
    } catch (e: Throwable) {
        // Intentional
    }
    if(lastException == null) {
        fail("Failed to execute the operation n time")
    } else {
        throw lastException
    }
}