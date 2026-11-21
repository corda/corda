package net.corda.coretests.internal

import net.corda.core.crypto.Crypto
import net.corda.core.internal.CertRole
import net.corda.nodeapi.internal.crypto.CertificateType
import net.corda.nodeapi.internal.crypto.X509Utilities
import org.bouncycastle.asn1.ASN1Integer
import org.junit.Test
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.security.auth.x500.X500Principal
import kotlin.test.*

class CertRoleTests {
    @Test(timeout=300_000)
	fun `should deserialize valid value`() {
        val expected = CertRole.DOORMAN_CA
        val actual = CertRole.getInstance(ASN1Integer(1L))
        assertEquals(expected, actual)
    }

    @Test(timeout=300_000)
	fun `should reject invalid values`() {
        // Below the lowest used value
        assertFailsWith<IllegalArgumentException> { CertRole.getInstance(ASN1Integer(0L)) }
        // Outside of the array size, but a valid integer
        assertFailsWith<IllegalArgumentException> { CertRole.getInstance(ASN1Integer(Integer.MAX_VALUE - 1L)) }
        // Outside of the range of integers
        assertFailsWith<IllegalArgumentException> { CertRole.getInstance(ASN1Integer(Integer.MAX_VALUE + 1L)) }
    }

    @Test(timeout=300_000)
	fun `check cert roles verify for various cert hierarchies`() {

        // Testing for various certificate hierarchies (with or without NodeCA).
        // ROOT -> Intermediate Root -> Doorman -> NodeCA -> Legal Identity cert -> Confidential key cert
        //                                      -> NodeCA -> TLS
        //                                      -> Legal Identity cert -> Confidential key cert
        //                                      -> TLS
        val rootSubject = X500Principal("CN=Root,O=R3 Ltd,L=London,C=GB")
        val intermediateRootSubject = X500Principal("CN=Intermediate Root,O=R3 Ltd,L=London,C=GB")
        val doormanSubject = X500Principal("CN=Doorman,O=R3 Ltd,L=London,C=GB")
        val nodeSubject = X500Principal("CN=Node,O=R3 Ltd,L=London,C=GB")

        val rootKeyPair = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
        val rootCert = X509Utilities.createSelfSignedCACertificate(rootSubject, rootKeyPair)
        val rootCertRole = CertRole.extract(rootCert)

        val intermediateRootKeyPair = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
        // Note that [CertificateType.ROOT_CA] is used for both root and intermediate root.
        val intermediateRootCert = X509Utilities.createCertificate(CertificateType.ROOT_CA, rootCert, rootKeyPair, intermediateRootSubject, intermediateRootKeyPair.public)
        val intermediateRootCertRole = CertRole.extract(intermediateRootCert)

        val doormanKeyPair = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
        // Note that [CertificateType.INTERMEDIATE_CA] has actually role = CertRole.DOORMAN_CA, see [CertificateType] in [X509Utilities].
        val doormanCert = X509Utilities.createCertificate(CertificateType.INTERMEDIATE_CA, intermediateRootCert, intermediateRootKeyPair, doormanSubject, doormanKeyPair.public)
        val doormanCertRole = CertRole.extract(doormanCert)!!

        val nodeCAKeyPair = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
        val nodeCACert = X509Utilities.createCertificate(CertificateType.NODE_CA, doormanCert, doormanKeyPair, nodeSubject, nodeCAKeyPair.public)
        val nodeCACertRole = CertRole.extract(nodeCACert)!!

        val tlsKeyPairFromNodeCA = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
        val tlsCertFromNodeCA = X509Utilities.createCertificate(CertificateType.TLS, nodeCACert, nodeCAKeyPair, nodeSubject, tlsKeyPairFromNodeCA.public)
        val tlsCertFromNodeCARole = CertRole.extract(tlsCertFromNodeCA)!!

        val tlsKeyPairFromDoorman = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
        val tlsCertFromDoorman = X509Utilities.createCertificate(CertificateType.TLS, doormanCert, doormanKeyPair, nodeSubject, tlsKeyPairFromDoorman.public)
        val tlsCertFromDoormanRole = CertRole.extract(tlsCertFromDoorman)!!

        val legalIDKeyPairFromNodeCA = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
        val legalIDCertFromNodeCA = X509Utilities.createCertificate(CertificateType.LEGAL_IDENTITY, nodeCACert, nodeCAKeyPair, nodeSubject, legalIDKeyPairFromNodeCA.public)
        val legalIDCertFromNodeCARole = CertRole.extract(legalIDCertFromNodeCA)!!

        val legalIDKeyPairFromDoorman = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
        val legalIDCertFromDoorman = X509Utilities.createCertificate(CertificateType.LEGAL_IDENTITY, doormanCert, doormanKeyPair, nodeSubject, legalIDKeyPairFromDoorman.public)
        val legalIDCertFromDoormanRole = CertRole.extract(legalIDCertFromDoorman)!!

        val confidentialKeyPair = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
        val confidentialCert = X509Utilities.createCertificate(CertificateType.CONFIDENTIAL_LEGAL_IDENTITY, legalIDCertFromNodeCA, legalIDKeyPairFromNodeCA, nodeSubject, confidentialKeyPair.public)
        val confidentialCertRole = CertRole.extract(confidentialCert)!!

        assertNull(rootCertRole)
        assertNull(intermediateRootCertRole)
        assertEquals(tlsCertFromNodeCARole, tlsCertFromDoormanRole)
        assertEquals(legalIDCertFromNodeCARole, legalIDCertFromDoormanRole)

        assertTrue { doormanCertRole.isValidParent(intermediateRootCertRole) } // Doorman is signed by Intermediate Root.
        assertTrue { nodeCACertRole.isValidParent(doormanCertRole) } // NodeCA is signed by Doorman.
        assertTrue { tlsCertFromNodeCARole.isValidParent(nodeCACertRole) } // TLS is signed by NodeCA.
        assertTrue { tlsCertFromDoormanRole.isValidParent(doormanCertRole) } // TLS can also be signed by Doorman.
        assertTrue { legalIDCertFromNodeCARole.isValidParent(nodeCACertRole) } // Legal Identity is signed by NodeCA.
        assertTrue { legalIDCertFromDoormanRole.isValidParent(doormanCertRole) } // Legal Identity can also be signed by Doorman.
        assertTrue { confidentialCertRole.isValidParent(legalIDCertFromNodeCARole) } // Confidential key cert is signed by Legal Identity.

        assertFalse { legalIDCertFromDoormanRole.isValidParent(tlsCertFromDoormanRole) } // Legal Identity cannot be signed by TLS.
        assertFalse { tlsCertFromNodeCARole.isValidParent(legalIDCertFromNodeCARole) } // TLS cannot be signed by Legal Identity.
        assertFalse { confidentialCertRole.isValidParent(nodeCACertRole) } // Confidential key cert cannot be signed by NodeCA.
        assertFalse { confidentialCertRole.isValidParent(doormanCertRole) } // Confidential key cert cannot be signed by Doorman.
    }

    @Test(timeout=300_000)
	fun `first lookups of a role from many threads at once all succeed`() {
        // Nodes starting in parallel in one JVM, as the driver's in-process nodes do, can make the first CertRole lookup at the
        // same moment. With a lazy value that is not thread safe, one of them then fails with a NullPointerException. That first
        // lookup happens once per class loader, so each iteration loads CertRole afresh and starts several lookups together.
        val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 8)
        val legalIdentityId = CertRole.LEGAL_IDENTITY.toASN1Primitive() as ASN1Integer
        val pool = Executors.newFixedThreadPool(threads)
        try {
            repeat(FIRST_LOOKUP_ITERATIONS) { iteration ->
                val certRoleClass = Class.forName(CertRole::class.java.name, true, FreshCertRoleClassLoader())
                assertNotSame(CertRole::class.java, certRoleClass)
                val companion = certRoleClass.getField("Companion").get(null)
                val getInstance = companion.javaClass.getMethod("getInstance", ASN1Integer::class.java)
                val ready = AtomicInteger()
                val go = AtomicBoolean()
                val lookups = (1..threads).map {
                    pool.submit(Callable {
                        ready.incrementAndGet()
                        while (!go.get()) Thread.onSpinWait()
                        getInstance.invoke(companion, legalIdentityId)
                    })
                }
                // Spin rather than wait on a barrier, so that the threads start within nanoseconds of each other.
                while (ready.get() < threads) Thread.onSpinWait()
                go.set(true)
                lookups.forEach { lookup ->
                    val role = try {
                        lookup.get()
                    } catch (e: ExecutionException) {
                        val cause = (e.cause as? InvocationTargetException)?.targetException ?: e.cause
                        throw AssertionError("A concurrent first lookup failed in iteration $iteration", cause)
                    }
                    assertEquals(CertRole.LEGAL_IDENTITY.name, (role as Enum<*>).name)
                }
            }
        } finally {
            pool.shutdownNow()
        }
    }

    /**
     * Gives each instance its own, uninitialised copy of [CertRole]. Other classes come from the test's class loader.
     * core-tests/build.gradle excludes this class loader from Quasar instrumentation by name, so keep the two in step.
     */
    private class FreshCertRoleClassLoader : ClassLoader(CertRole::class.java.classLoader) {
        override fun loadClass(name: String, resolve: Boolean): Class<*> {
            if (freshClasses.none { name == it || name.startsWith("$it$") }) return super.loadClass(name, resolve)
            return synchronized(getClassLoadingLock(name)) {
                findLoadedClass(name) ?: classBytes.getOrPut(name) {
                    val resource = name.replace('.', '/') + ".class"
                    requireNotNull(parent.getResourceAsStream(resource)) { "$resource not found" }.use { it.readBytes() }
                }.let { defineClass(name, it, 0, it.size) }
            }
        }
    }

    private companion object {
        const val FIRST_LOOKUP_ITERATIONS = 2000

        /**
         * The classes [FreshCertRoleClassLoader] defines itself: [CertRole], and Kotlin's lazy implementations so that they
         * run interpreted, as they do the first time a node starts. Once the JIT has compiled them, the race window is much
         * smaller and the test would rarely catch a lazy value that is not thread safe.
         */
        val freshClasses = listOf(
                CertRole::class.java.name,
                "kotlin.LazyKt",
                "kotlin.LazyKt__LazyJVMKt",
                "kotlin.LazyKt__LazyKt",
                "kotlin.UnsafeLazyImpl",
                "kotlin.SafePublicationLazyImpl",
                "kotlin.SynchronizedLazyImpl",
                "kotlin.InitializedLazyImpl",
                "kotlin.UNINITIALIZED_VALUE"
        )
        val classBytes = ConcurrentHashMap<String, ByteArray>()
    }
}
