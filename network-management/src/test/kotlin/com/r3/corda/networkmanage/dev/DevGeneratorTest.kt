/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package com.r3.corda.networkmanage.dev

import net.corda.nodeapi.internal.crypto.X509KeyStore
import net.corda.nodeapi.internal.crypto.X509Utilities
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertTrue

class DevGeneratorTest {

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    @Test
    fun `key store and trust store are created and contain the certificates`() {
        // given
        val config = GeneratorConfiguration(directory = tempFolder.root.toPath())

        // when
        run(config)

        // then
        val keyStoreFile = File("${config.directory}/${config.keyStoreFileName}").toPath()
        val keyStore = X509KeyStore.fromFile(keyStoreFile, config.keyStorePass, createNew = true)

        assertTrue(X509Utilities.CORDA_INTERMEDIATE_CA in keyStore)
        assertTrue(X509Utilities.CORDA_ROOT_CA in keyStore)
    }
}