package net.corda.node.services.keys

import net.corda.core.CordaOID
import net.corda.core.internal.CertRole
import net.corda.testing.MEGA_CORP
import net.corda.testing.MEGA_CORP_IDENTITY
import net.corda.testing.MEGA_CORP_KEY
import net.corda.testing.node.MockServices
import net.corda.testing.node.makeTestIdentityService
import net.corda.testing.singleIdentityAndCert
import org.bouncycastle.asn1.DEROctetString
import org.junit.Test
import kotlin.test.assertEquals

class KMSUtilsTests {
    @Test
    fun `should generate certificates with the correct role`() {
        val cordappPackages = emptyList<String>()
        val ledgerIdentityService = makeTestIdentityService(listOf(MEGA_CORP_IDENTITY))
        val mockServices = MockServices(cordappPackages, ledgerIdentityService, MEGA_CORP.name, MEGA_CORP_KEY)
        val wellKnownIdentity = mockServices.myInfo.singleIdentityAndCert()
        val confidentialIdentity = mockServices.keyManagementService.freshKeyAndCert(wellKnownIdentity, false)
        val cert = confidentialIdentity.certificate
        val extensionData = DEROctetString.getInstance(cert.getExtensionValue(CordaOID.X509_EXTENSION_CORDA_ROLE))
        val expected = CertRole.CONFIDENTIAL_IDENTITY
        val actual = CertRole.getInstance(extensionData.octets)!!
        assertEquals(expected, actual)
    }
}