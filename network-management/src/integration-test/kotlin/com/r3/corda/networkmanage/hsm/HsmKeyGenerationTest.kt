/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package com.r3.corda.networkmanage.hsm

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import com.r3.corda.networkmanage.common.HsmBaseTest
import com.r3.corda.networkmanage.common.utils.CORDA_NETWORK_MAP
import com.r3.corda.networkmanage.hsm.authentication.CryptoServerProviderConfig
import com.r3.corda.networkmanage.hsm.authentication.InputReader
import com.r3.corda.networkmanage.hsm.generator.AutoAuthenticator
import com.r3.corda.networkmanage.hsm.generator.run
import com.r3.corda.networkmanage.hsm.utils.HsmX509Utilities
import net.corda.core.identity.CordaX500Name
import net.corda.nodeapi.internal.crypto.CertificateType
import net.corda.nodeapi.internal.crypto.X509Utilities.CORDA_INTERMEDIATE_CA
import net.corda.nodeapi.internal.crypto.X509Utilities.CORDA_ROOT_CA
import org.junit.Before
import org.junit.Test
import java.security.cert.X509Certificate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class HsmKeyGenerationTest : HsmBaseTest() {

    private lateinit var inputReader: InputReader

    @Before
    override fun setUp() {
        super.setUp()
        inputReader = mock()
        whenever(inputReader.readLine()).thenReturn(hsmSimulator.cryptoUserCredentials().username)
        whenever(inputReader.readPassword(any())).thenReturn(hsmSimulator.cryptoUserCredentials().password)
    }

    @Test
    fun `Root and network map certificates have different namespace`() {
        // when root cert is created
        run(createGeneratorParameters(
                keyGroup = ROOT_CERT_KEY_GROUP,
                rootKeyGroup = null,
                certificateType = CertificateType.ROOT_CA,
                subject = ROOT_CERT_SUBJECT
        ))
        // when network map cert is created
        run(createGeneratorParameters(
                keyGroup = NETWORK_MAP_CERT_KEY_GROUP,
                rootKeyGroup = ROOT_CERT_KEY_GROUP,
                certificateType = CertificateType.NETWORK_MAP,
                subject = NETWORK_MAP_CERT_SUBJECT
        ))
        // when doorman cert is created
        run(createGeneratorParameters(
                keyGroup = DOORMAN_CERT_KEY_GROUP,
                rootKeyGroup = ROOT_CERT_KEY_GROUP,
                certificateType = CertificateType.INTERMEDIATE_CA,
                subject = DOORMAN_CERT_SUBJECT
        ))

        // then root cert is persisted in the HSM
        AutoAuthenticator(createProviderConfig(ROOT_CERT_KEY_GROUP), HSM_USER_CONFIGS).connectAndAuthenticate { provider ->
            val keyStore = HsmX509Utilities.getAndInitializeKeyStore(provider)
            val rootCert = keyStore.getCertificate(CORDA_ROOT_CA) as X509Certificate
            assertEquals(rootCert.issuerX500Principal, rootCert.subjectX500Principal)
        }

        // then network map cert is persisted in the HSM

        AutoAuthenticator(createProviderConfig(NETWORK_MAP_CERT_KEY_GROUP), HSM_USER_CONFIGS)
                .connectAndAuthenticate { provider ->
                    val keyStore = HsmX509Utilities.getAndInitializeKeyStore(provider)
                    val networkMapCert = keyStore.getCertificate(CORDA_NETWORK_MAP) as X509Certificate
                    assertNotNull(networkMapCert)
                    assertEquals(CordaX500Name.parse(ROOT_CERT_SUBJECT).x500Principal, networkMapCert.issuerX500Principal)
                }

        // then doorman cert is persisted in the HSM

        AutoAuthenticator(createProviderConfig(DOORMAN_CERT_KEY_GROUP), HSM_USER_CONFIGS)
                .connectAndAuthenticate { provider ->
                    val keyStore = HsmX509Utilities.getAndInitializeKeyStore(provider)
                    val networkMapCert = keyStore.getCertificate(CORDA_INTERMEDIATE_CA) as X509Certificate
                    assertNotNull(networkMapCert)
                    assertEquals(CordaX500Name.parse(ROOT_CERT_SUBJECT).x500Principal, networkMapCert.issuerX500Principal)
                }
    }

    private fun createProviderConfig(keyGroup: String): CryptoServerProviderConfig {
        return CryptoServerProviderConfig(
                Device = "${hsmSimulator.port}@${hsmSimulator.host}",
                KeySpecifier = 1,
                KeyGroup = keyGroup,
                StoreKeysExternal = false)
    }
}