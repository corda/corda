package net.corda.nodeapi.internal

import io.github.lukehutch.fastclasspathscanner.FastClasspathScanner
import net.corda.core.contracts.Contract
import net.corda.core.contracts.ContractClassName
import net.corda.core.contracts.UpgradedContract
import net.corda.core.contracts.UpgradedContractWithLegacyConstraint
import net.corda.core.crypto.SecureHash
import net.corda.core.internal.*
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Collections.singleton

// When scanning of the CorDapp Jar is performed without "corda-core.jar" being the in the classpath, there is no way to appreciate
// relationships between those interfaces, therefore they have to be listed explicitly.
val coreContractClasses = setOf(Contract::class, UpgradedContractWithLegacyConstraint::class, UpgradedContract::class)

interface ContractsJar {
    val hash: SecureHash
    fun scan(): List<ContractClassName>
}

class ContractsJarFile(private val file: Path) : ContractsJar {
    override val hash: SecureHash by lazy(LazyThreadSafetyMode.NONE, file::hash)

    override fun scan(): List<ContractClassName> {
        val scanResult = FastClasspathScanner()
                // A set of a single element may look odd, but if this is removed "Path" which itself is an `Iterable`
                // is getting broken into pieces to scan individually, which doesn't yield desired effect.
                .overrideClasspath(singleton(file))
                .scan()

        val contractClassNames = coreContractClasses
                .flatMap { scanResult.getNamesOfClassesImplementing(it.qualifiedName) }
                .toSet()

        return URLClassLoader(arrayOf(file.toUri().toURL()), Contract::class.java.classLoader).use { cl ->
            contractClassNames.mapNotNull {
                val contractClass = cl.loadClass(it)
                // Only keep instantiable contracts
                if (contractClass.isConcreteClass) contractClass.name else null
            }
        }
    }
}

private val logger = LoggerFactory.getLogger("ClassloaderUtils")

fun <T> withContractsInJar(jarInputStream: InputStream, withContracts: (List<ContractClassName>, InputStream) -> T): T {
    val tempFile = Files.createTempFile("attachment", ".jar")
    try {
        jarInputStream.copyTo(tempFile, StandardCopyOption.REPLACE_EXISTING)
        val cordappJar = tempFile.toAbsolutePath()
        val contracts = logElapsedTime("Contracts loading for '$cordappJar'", logger) {
            ContractsJarFile(tempFile.toAbsolutePath()).scan()
        }
        return tempFile.read { withContracts(contracts, it) }
    } finally {
        tempFile.deleteIfExists()
    }
}
