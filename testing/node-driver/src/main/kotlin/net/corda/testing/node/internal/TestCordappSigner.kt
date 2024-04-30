package net.corda.testing.node.internal

import net.corda.core.internal.JarSignatureCollector
import net.corda.core.internal.deleteRecursively
import net.corda.testing.core.internal.JarSignatureTestUtils.containsKey
import net.corda.testing.core.internal.JarSignatureTestUtils.generateKey
import net.corda.testing.core.internal.JarSignatureTestUtils.signJar
import net.corda.testing.core.internal.JarSignatureTestUtils.unsignJar
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarInputStream
import kotlin.io.path.absolutePathString
import kotlin.io.path.copyTo
import kotlin.io.path.inputStream
import kotlin.io.path.name

object TestCordappSigner {
    private val defaultSignerDir = Files.createTempDirectory("testcordapp-signer")

    init {
        defaultSignerDir.generateKey(alias = "testcordapp")
        Runtime.getRuntime().addShutdownHook(Thread(defaultSignerDir::deleteRecursively))
    }

    fun signJarCopy(jar: Path, signerDir: Path? = null, signatureCount: Int = 1, algorithm: String = "RSA"): Path {
        val copy = Files.createTempFile(jar.name, ".jar")
        copy.toFile().deleteOnExit()
        jar.copyTo(copy, overwrite = true)
        signJar(copy, signerDir, signatureCount, algorithm)
        return copy
    }

    fun signJar(jar: Path, signerDir: Path? = null, signatureCount: Int = 1, algorithm: String = "RSA") {
        jar.unsignJar()
        val signerDirToUse = signerDir ?: defaultSignerDir
        for (i in 1 .. signatureCount) {
            // Note in the jarsigner tool if -sigfile is not specified then the first 8 chars of alias are used as the file
            // name for the .SF and .DSA files. (See jarsigner doc). So $i below needs to be at beginning so unique files are
            // created.
            val alias = "$i-testcordapp-$algorithm"
            val password = "secret!"
            if (!signerDirToUse.containsKey(alias, password)) {
                signerDirToUse.generateKey(alias, password, "O=Test Company Ltd $i,OU=Test,L=London,C=GB", algorithm)
            }
            signerDirToUse.signJar(jar.absolutePathString(), alias, password)
        }
    }
}
