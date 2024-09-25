package net.corda.nodeapi.internal

import io.github.classgraph.ClassGraph
import net.corda.core.contracts.Contract
import net.corda.core.contracts.ContractClassName
import net.corda.core.contracts.UpgradedContract
import net.corda.core.contracts.UpgradedContractWithLegacyConstraint
import net.corda.core.crypto.SecureHash
import net.corda.core.internal.copyTo
import net.corda.core.internal.hash
import net.corda.core.internal.logElapsedTime
import net.corda.core.internal.pooledScan
import net.corda.core.internal.read
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Collections.singleton
import kotlin.io.path.deleteIfExists

// When scanning of the CorDapp Jar is performed without "corda-core.jar" being in the classpath, there is no way to appreciate
// relationships between those interfaces, therefore they have to be listed explicitly.
val coreContractClasses = setOf(Contract::class.java, UpgradedContractWithLegacyConstraint::class.java, UpgradedContract::class.java)

interface ContractsJar {
    val hash: SecureHash
    fun scan(): List<ContractClassName>
}

class ContractsJarFile(private val file: Path) : ContractsJar {
    override val hash: SecureHash by lazy(LazyThreadSafetyMode.NONE, file::hash)

    override fun scan(): List<ContractClassName> {
        val scanResult = ClassGraph().overrideClasspath(singleton(file)).enableClassInfo().pooledScan()

        return scanResult.use { result ->
            coreContractClasses
                    .asSequence()
                    .flatMap(result::getClassesImplementing)
                    .filterNot { it.isAbstract }
                    .filterNot { it.isInterface }
                    .map { it.name }
                    .toList()
        }
    }
}

private val logger = LoggerFactory.getLogger("ClassloaderUtils")

fun <T> withContractsInJar(jarInputStream: InputStream, withContracts: (List<ContractClassName>, InputStream) -> T): T {
    val tempFile = Files.createTempFile("attachment", ".jar")
    try {
        jarInputStream.use {
            it.copyTo(tempFile, StandardCopyOption.REPLACE_EXISTING)
        }
        val cordappJar = tempFile.toAbsolutePath()
        val contracts = logElapsedTime("Contracts loading for '$cordappJar'", logger) {
            ContractsJarFile(tempFile.toAbsolutePath()).scan()
        }
        return tempFile.read { withContracts(contracts, it) }
    } finally {
        tempFile.deleteIfExists()
    }
}
