package net.corda.nodeapi.internal

import io.github.lukehutch.fastclasspathscanner.FastClasspathScanner
import net.corda.core.contracts.Contract
import net.corda.core.contracts.ContractClassName
import net.corda.core.contracts.UpgradedContract
import net.corda.core.internal.copyTo
import net.corda.core.internal.deleteIfExists
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Scans the jar for contracts.
 * @returns: found contract class names or null if none found
 */
fun scanJarForContracts(cordappJarPath: String): List<ContractClassName>? {
    val scanResult = FastClasspathScanner().addClassLoader(Thread.currentThread().contextClassLoader).overrideClasspath(cordappJarPath).scan()
    val contracts = (scanResult.getNamesOfClassesImplementing(Contract::class.qualifiedName) + scanResult.getNamesOfClassesImplementing(UpgradedContract::class.qualifiedName)).distinct()
    return if (contracts.isEmpty()) null else contracts
}

fun <T> withContractsInJar(jarInputStream: InputStream, withContracts: (List<ContractClassName>?, InputStream) -> T): T {
    val tempFile = Files.createTempFile("attachment", ".jar")
    try {
        jarInputStream.copyTo(tempFile, StandardCopyOption.REPLACE_EXISTING)
        val contracts = scanJarForContracts(tempFile.toAbsolutePath().toString())
        return tempFile.toFile().inputStream().use { withContracts(contracts, it) }
    } finally {
        tempFile.deleteIfExists()
    }
}