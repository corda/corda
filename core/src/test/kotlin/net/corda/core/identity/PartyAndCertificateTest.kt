/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.core.identity

import com.google.common.jimfs.Configuration.unix
import com.google.common.jimfs.Jimfs
import net.corda.core.crypto.entropyToKeyPair
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.nodeapi.internal.crypto.X509KeyStore
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.core.getTestPartyAndCertificate
import net.corda.testing.internal.DEV_ROOT_CA
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import java.math.BigInteger
import kotlin.test.assertFailsWith

class PartyAndCertificateTest {
    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    @Test
    fun `reject a path with no roles`() {
        val path = X509Utilities.buildCertPath(DEV_ROOT_CA.certificate)
        assertFailsWith<IllegalArgumentException> { PartyAndCertificate(path) }
    }

    @Test
    fun `kryo serialisation`() {
        val original = getTestPartyAndCertificate(Party(
                CordaX500Name(organisation = "Test Corp", locality = "Madrid", country = "ES"),
                entropyToKeyPair(BigInteger.valueOf(83)).public))
        val copy = original.serialize().deserialize()
        assertThat(copy).isEqualTo(original).isNotSameAs(original)
        assertThat(copy.certPath).isEqualTo(original.certPath)
        assertThat(copy.certificate).isEqualTo(original.certificate)
    }

    @Test
    fun `jdk serialization`() {
        val identity = getTestPartyAndCertificate(Party(
                CordaX500Name(organisation = "Test Corp", locality = "Madrid", country = "ES"),
                entropyToKeyPair(BigInteger.valueOf(83)).public))
        val original = identity.certificate
        val alias = identity.name.toString()
        val storePassword = "test"
        Jimfs.newFileSystem(unix()).use {
            val keyStoreFile = it.getPath("/serialization_test.jks")

            X509KeyStore.fromFile(keyStoreFile, storePassword, createNew = true).update {
                setCertificate(alias, original)
            }

            // Load the key store back in again
            val copy = X509KeyStore.fromFile(keyStoreFile, storePassword).getCertificate(alias)
            assertThat(copy).isEqualTo(original)
        }
    }
}
