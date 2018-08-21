package net.corda.core.internal

import net.corda.core.identity.Party
import java.security.CodeSigner
import java.security.cert.X509Certificate
import java.util.jar.JarEntry
import java.util.jar.JarInputStream

/**
 * Utility class which provides the ability to extract a list of signing parties from a [JarInputStream].
 */
object JarSignatureCollector {

    /** @see <https://docs.oracle.com/javase/8/docs/technotes/guides/jar/jar.html#Signed_JAR_File> */
    private val unsignableEntryName = "META-INF/(?:.*[.](?:SF|DSA|RSA)|SIG-.*)".toRegex()

    /**
     * An array which essentially serves as /dev/null for reading through a jar and obtaining its code signers.
     */
    private val shredder = ByteArray(1024)

    /**
     * Returns an ordered list of every [Party] which has signed every signable item in the given [JarInputStream].
     * Any party which has signed some but not all signable items is eliminated.
     *
     * @param jar The open [JarInputStream] to collect signing parties from.
     */
    fun collectSigningParties(jar: JarInputStream): List<Party> {
        // Can't start with empty set if we're doing intersections. Logically the null means "all possible signers":
        var attachmentSigners: MutableSet<CodeSigner>? = null

        for (signers in jar.getSignerSets()) {
            attachmentSigners = attachmentSigners?.intersect(signers) ?: signers.toMutableSet()

            if (attachmentSigners.isEmpty()) break // Performance short-circuit.
        }

        return attachmentSigners?.toPartiesOrderedByName() ?: emptyList()
    }

    private fun <T> MutableSet<T>.intersect(others: Iterable<T>) = apply { retainAll(others) }

    private fun JarInputStream.getSignerSets() =
            entries().thatAreSignable().shreddedFrom(this).toSignerSets()

    private fun Sequence<JarEntry>.thatAreSignable() =
            filterNot { entry -> entry.isDirectory || unsignableEntryName.matches(entry.name) }

    private fun Sequence<JarEntry>.shreddedFrom(jar: JarInputStream) = map { entry ->
        entry.apply {
            while (jar.read(shredder) != -1) { // Must read entry fully for codeSigners to be valid.
                // Do nothing.
            }
        }
    }

    private fun Sequence<JarEntry>.toSignerSets() = map { entry -> entry.codeSigners?.toList() ?: emptyList() }

    private fun Set<CodeSigner>.toPartiesOrderedByName() = map {
        Party(it.signerCertPath.certificates[0] as X509Certificate)
    }.sortedBy { it.name.toString() } // Sorted for determinism.

    private fun JarInputStream.entries() = generateSequence(nextJarEntry) { nextJarEntry }
}