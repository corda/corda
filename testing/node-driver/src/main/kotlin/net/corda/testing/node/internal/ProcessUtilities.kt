/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.testing.node.internal

import net.corda.core.internal.div
import net.corda.core.internal.exists
import java.io.File.pathSeparator
import java.nio.file.Path

object ProcessUtilities {
    inline fun <reified C : Any> startJavaProcess(
            arguments: List<String>,
            jdwpPort: Int? = null,
            extraJvmArguments: List<String> = emptyList()
    ): Process {
        return startJavaProcessImpl(C::class.java.name, arguments, defaultClassPath, jdwpPort, extraJvmArguments, null, null)
    }

    fun startCordaProcess(
            className: String,
            arguments: List<String>,
            jdwpPort: Int?,
            extraJvmArguments: List<String>,
            workingDirectory: Path?,
            maximumHeapSize: String
    ): Process {
        // FIXME: Instead of hacking our classpath, use the correct classpath for className.
        val classpath = defaultClassPath.split(pathSeparator).filter { !(it / "log4j2-test.xml").exists() }.joinToString(pathSeparator)
        return startJavaProcessImpl(className, arguments, classpath, jdwpPort, extraJvmArguments, workingDirectory, maximumHeapSize)
    }

    fun startJavaProcessImpl(
            className: String,
            arguments: List<String>,
            classpath: String,
            jdwpPort: Int?,
            extraJvmArguments: List<String>,
            workingDirectory: Path?,
            maximumHeapSize: String?
    ): Process {
        val command = mutableListOf<String>().apply {
            add((System.getProperty("java.home") / "bin" / "java").toString())
            (jdwpPort != null) && add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=$jdwpPort")
            if (maximumHeapSize != null) add("-Xmx$maximumHeapSize")
            add("-XX:+UseG1GC")
            addAll(extraJvmArguments)
            add(className)
            addAll(arguments)
        }
        return ProcessBuilder(command).apply {
            inheritIO()
            environment()["CLASSPATH"] = classpath
            if (workingDirectory != null) {
                redirectError((workingDirectory / "$className.stderr.log").toFile())
                redirectOutput((workingDirectory / "$className.stdout.log").toFile())
                directory(workingDirectory.toFile())
            }
        }.start()
    }

    val defaultClassPath: String get() = System.getProperty("java.class.path")
}