/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node.services.keys

import net.corda.core.CordaOID
import net.corda.core.crypto.generateKeyPair
import net.corda.core.internal.CertRole
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.getTestPartyAndCertificate
import net.corda.testing.core.singleIdentityAndCert
import net.corda.testing.node.MockServices
import net.corda.testing.node.makeTestIdentityService
import org.bouncycastle.asn1.DEROctetString
import org.junit.Test
import kotlin.test.assertEquals

class KMSUtilsTests {
    @Test
    fun `should generate certificates with the correct role`() {
        val aliceKey = generateKeyPair()
        val alice = getTestPartyAndCertificate(ALICE_NAME, aliceKey.public)
        val cordappPackages = emptyList<String>()
        val ledgerIdentityService = makeTestIdentityService(alice)
        val mockServices = MockServices(cordappPackages, alice.name, ledgerIdentityService, aliceKey)
        val wellKnownIdentity = mockServices.myInfo.singleIdentityAndCert()
        val confidentialIdentity = mockServices.keyManagementService.freshKeyAndCert(wellKnownIdentity, false)
        val cert = confidentialIdentity.certificate
        val extensionData = DEROctetString.getInstance(cert.getExtensionValue(CordaOID.X509_EXTENSION_CORDA_ROLE))
        val expected = CertRole.CONFIDENTIAL_LEGAL_IDENTITY
        val actual = CertRole.getInstance(extensionData.octets)
        assertEquals(expected, actual)
    }
}