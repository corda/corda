/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.nodeapi.internal

import io.github.lukehutch.fastclasspathscanner.FastClasspathScanner
import net.corda.core.contracts.Contract
import net.corda.core.contracts.ContractClassName
import net.corda.core.internal.copyTo
import net.corda.core.internal.deleteIfExists
import net.corda.core.internal.read
import java.io.InputStream
import java.lang.reflect.Modifier
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

/**
 * Scans the jar for contracts.
 * @returns: found contract class names or null if none found
 */
fun scanJarForContracts(cordappJar: Path): List<ContractClassName> {
    val currentClassLoader = Contract::class.java.classLoader
    val scanResult = FastClasspathScanner()
            .addClassLoader(currentClassLoader)
            .overrideClasspath(cordappJar, Paths.get(Contract::class.java.protectionDomain.codeSource.location.toURI()))
            .scan()
    val contracts = (scanResult.getNamesOfClassesImplementing(Contract::class.qualifiedName) ).distinct()

    // Only keep instantiable contracts
    return URLClassLoader(arrayOf(cordappJar.toUri().toURL()), currentClassLoader).use {
        contracts.map(it::loadClass).filter { !it.isInterface && !Modifier.isAbstract(it.modifiers) }
    }.map { it.name }
}

fun <T> withContractsInJar(jarInputStream: InputStream, withContracts: (List<ContractClassName>, InputStream) -> T): T {
    val tempFile = Files.createTempFile("attachment", ".jar")
    try {
        jarInputStream.copyTo(tempFile, StandardCopyOption.REPLACE_EXISTING)
        val contracts = scanJarForContracts(tempFile.toAbsolutePath())
        return tempFile.read { withContracts(contracts, it) }
    } finally {
        tempFile.deleteIfExists()
    }
}