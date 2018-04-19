package net.corda.nodeapi.internal

import io.github.lukehutch.fastclasspathscanner.FastClasspathScanner
import net.corda.core.contracts.Contract
import net.corda.core.contracts.ContractClassName
import net.corda.core.internal.copyTo
import net.corda.core.internal.deleteIfExists
import net.corda.core.internal.logElapsedTime
import net.corda.core.internal.read
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.lang.reflect.Modifier
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Scans the jar for contracts.
 * @returns: found contract class names or null if none found
 */
fun scanJarForContracts(cordappJar: Path): List<ContractClassName> {
    val scanResult = FastClasspathScanner()
            // A set of a single element may look odd, but if this is removed "Path" which itself is an `Iterable`
            // is getting broken into pieces to scan individually, which doesn't yield desired effect.
            .overrideClasspath(setOf(cordappJar))
            .scan()
    val contracts = (scanResult.getNamesOfClassesImplementing(Contract::class.qualifiedName) ).distinct()

    // Only keep instantiable contracts
    return URLClassLoader(arrayOf(cordappJar.toUri().toURL()), Contract::class.java.classLoader).use {
        contracts.map(it::loadClass).filter { !it.isInterface && !Modifier.isAbstract(it.modifiers) }
    }.map { it.name }
}

private val logger = LoggerFactory.getLogger("ClassloaderUtils")

fun <T> withContractsInJar(jarInputStream: InputStream, withContracts: (List<ContractClassName>, InputStream) -> T): T {
    val tempFile = Files.createTempFile("attachment", ".jar")
    try {
        jarInputStream.copyTo(tempFile, StandardCopyOption.REPLACE_EXISTING)
        val cordappJar = tempFile.toAbsolutePath()
        val contracts = logElapsedTime("Contracts loading for '$cordappJar'", logger) {
            scanJarForContracts(cordappJar)
        }
        return tempFile.read { withContracts(contracts, it) }
    } finally {
        tempFile.deleteIfExists()
    }
}