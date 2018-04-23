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

import org.apache.commons.io.FileUtils
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProcessUtilitiesTests {

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    companion object {

        private val tmpString = ProcessUtilitiesTests::class.java.name

        @JvmStatic
        fun main(args: Array<String>) {
            val fileNameToCreate = args[0]
            FileUtils.write(File(fileNameToCreate), tmpString)
        }
    }

    @Test
    fun `test dummy process can be started`() {
        val tmpFile = tempFolder.newFile("${ProcessUtilitiesTests::class.java.simpleName}.txt")
        val startedProcess = ProcessUtilities.startJavaProcess<ProcessUtilitiesTests>(listOf(tmpFile.absolutePath))
        assertTrue { startedProcess.waitFor(20, TimeUnit.SECONDS) }
        assertEquals(tmpString, FileUtils.readFileToString(tmpFile))
    }
}