package net.corda.node.services.keys

import net.corda.core.CordaOID
import net.corda.core.crypto.IdentityRoleExtension
import net.corda.core.identity.CertRole
import net.corda.testing.*
import net.corda.testing.node.MockServices
import net.corda.testing.node.makeTestIdentityService
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
        val actual = IdentityRoleExtension.getInstance(extensionData.octets)!!.role
        assertEquals(expected, actual)
    }
}