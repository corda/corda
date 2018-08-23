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
     *
     * @param jar The open [JarInputStream] to collect signing parties from.
     * @throws InvalidJarSignersException If the signer sets for any two signable items are different from each other.
     */
    fun collectSigningParties(jar: JarInputStream): List<Party> {
        val signerSets = jar.fileSignerSets
        if (signerSets.isEmpty()) return emptyList()

        val (firstFile, firstSignerSet) = signerSets.first()
        for ((otherFile, otherSignerSet) in signerSets.subList(1, signerSets.size)) {
            if (otherSignerSet != firstSignerSet) throw InvalidJarSignersException(
                """
                Mismatch between signers ${firstSignerSet.toPartiesOrderedByName()} for file $firstFile
                and signers ${otherSignerSet.toPartiesOrderedByName()} for file $otherFile
                """.trimIndent().replace('\n', ' '))
        }

        return firstSignerSet.toPartiesOrderedByName()
    }

    private val JarInputStream.fileSignerSets: List<Pair<String, Set<CodeSigner>>> get() =
            entries.thatAreSignable.shreddedFrom(this).toFileSignerSet().toList()

    private val Sequence<JarEntry>.thatAreSignable: Sequence<JarEntry> get() =
            filterNot { entry -> entry.isDirectory || unsignableEntryName.matches(entry.name) }

    private fun Sequence<JarEntry>.shreddedFrom(jar: JarInputStream): Sequence<JarEntry> = map { entry ->
        entry.apply {
            while (jar.read(shredder) != -1) { // Must read entry fully for codeSigners to be valid.
                // Do nothing.
            }
        }
    }

    private fun Sequence<JarEntry>.toFileSignerSet(): Sequence<Pair<String, Set<CodeSigner>>> =
            map { entry -> entry.name to (entry.codeSigners?.toSet() ?: emptySet()) }

    private fun Set<CodeSigner>.toPartiesOrderedByName(): List<Party> = map {
        Party(it.signerCertPath.certificates[0] as X509Certificate)
    }.sortedBy { it.name.toString() } // Sorted for determinism.

    private val JarInputStream.entries get(): Sequence<JarEntry> = generateSequence(nextJarEntry) { nextJarEntry }
}

class InvalidJarSignersException(msg: String) : Exception(msg)