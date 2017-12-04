package net.corda.node.services.keys

import net.corda.core.CordaOID
import net.corda.core.crypto.IdentityRoleExtension
import net.corda.core.identity.Role
import net.corda.testing.node.MockServices
import net.corda.testing.singleIdentityAndCert
import org.bouncycastle.asn1.DEROctetString
import org.junit.Test
import kotlin.test.assertEquals

class KMSUtilsTests {
    @Test
    fun `should generate certificates with the correct role`() {
        val mockServices = MockServices()
        val wellKnownIdentity = mockServices.myInfo.singleIdentityAndCert()
        val confidentialIdentity = mockServices.keyManagementService.freshKeyAndCert(wellKnownIdentity, false)
        val cert = confidentialIdentity.certificate
        val extensionData = DEROctetString.getInstance(cert.getExtensionValue(CordaOID.X509_EXTENSION_CORDA_ROLE))
        val expected = Role.CONFIDENTIAL_IDENTITY
        val actual = IdentityRoleExtension.getInstance(extensionData.octets)!!.role
        assertEquals(expected, actual)
    }
}