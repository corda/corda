package net.corda.nodeapi.internal

import net.corda.core.crypto.CompositeKey
import net.corda.core.crypto.Crypto
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.node.NodeInfo
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.coretesting.internal.TestNodeInfoBuilder
import net.corda.coretesting.internal.signWith
import net.corda.nodeapi.internal.crypto.CertificateType
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.SerializationEnvironmentRule
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Rule
import org.junit.Test
import java.security.KeyPair
import java.security.PublicKey
import java.security.SignatureException

class SignedNodeInfoTest {
    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    private val nodeInfoBuilder = TestNodeInfoBuilder()

    @Test(timeout=300_000)
	fun `verifying single identity`() {
        nodeInfoBuilder.addLegalIdentity(ALICE_NAME)
        val (nodeInfo, signedNodeInfo) = nodeInfoBuilder.buildWithSigned()
        assertThat(signedNodeInfo.verified()).isEqualTo(nodeInfo)
    }

    @Test(timeout=300_000)
	fun `verifying multiple identities`() {
        nodeInfoBuilder.addLegalIdentity(ALICE_NAME)
        nodeInfoBuilder.addLegalIdentity(BOB_NAME)
        val (nodeInfo, signedNodeInfo) = nodeInfoBuilder.buildWithSigned()
        assertThat(signedNodeInfo.verified()).isEqualTo(nodeInfo)
    }

    @Test(timeout=300_000)
	fun `verifying missing signature`() {
        val (_, aliceKey) = nodeInfoBuilder.addLegalIdentity(ALICE_NAME)
        nodeInfoBuilder.addLegalIdentity(BOB_NAME)
        val nodeInfo = nodeInfoBuilder.build()
        val signedNodeInfo = nodeInfo.signWith(listOf(aliceKey))
        assertThatThrownBy { signedNodeInfo.verified() }
                .isInstanceOf(SignatureException::class.java)
                .hasMessageContaining("Missing signatures")
    }

    @Test(timeout=300_000)
	fun `verifying composite keys only`() {
        val aliceKeyPair = generateKeyPair()
        val bobKeyPair = generateKeyPair()
        val identityKeyPair = generateKeyPair()
        val compositeKey = CompositeKey.Builder().addKeys(aliceKeyPair.public, bobKeyPair.public).build(threshold = 1)
        val nodeInfo = createNodeInfoWithSingleIdentity(ALICE_NAME, aliceKeyPair, compositeKey)
        val signedNodeInfo = nodeInfo.signWith(listOf(identityKeyPair.private))
        assertThatThrownBy { signedNodeInfo.verified() }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("At least one identity with a non-composite key needs to be specified.")
    }

    @Test(timeout=300_000)
	fun `verifying extra signature`() {
        val (_, aliceKey) = nodeInfoBuilder.addLegalIdentity(ALICE_NAME)
        val nodeInfo = nodeInfoBuilder.build()
        val signedNodeInfo = nodeInfo.signWith(listOf(aliceKey, generateKeyPair().private))
        assertThatThrownBy { signedNodeInfo.verified() }
                .isInstanceOf(SignatureException::class.java)
                .hasMessageContaining("Extra signatures")
    }

    @Test(timeout=300_000)
	fun `verifying incorrect signature`() {
        nodeInfoBuilder.addLegalIdentity(ALICE_NAME)
        val nodeInfo = nodeInfoBuilder.build()
        val signedNodeInfo = nodeInfo.signWith(listOf(generateKeyPair().private))
        assertThatThrownBy { signedNodeInfo.verified() }
                .isInstanceOf(SignatureException::class.java)
                .hasMessageContaining(ALICE_NAME.toString())
    }

    @Test(timeout=300_000)
	fun `verifying with signatures in wrong order`() {
        val (_, aliceKey) = nodeInfoBuilder.addLegalIdentity(ALICE_NAME)
        val (_, bobKey) = nodeInfoBuilder.addLegalIdentity(BOB_NAME)
        val nodeInfo = nodeInfoBuilder.build()
        val signedNodeInfo = nodeInfo.signWith(listOf(bobKey, aliceKey))
        assertThatThrownBy { signedNodeInfo.verified() }
                .isInstanceOf(SignatureException::class.java)
                .hasMessageContaining(ALICE_NAME.toString())
    }

    private fun generateKeyPair() = Crypto.generateKeyPair()

    private fun createNodeInfoWithSingleIdentity(name: CordaX500Name, nodeKeyPair: KeyPair, identityCertPublicKey: PublicKey): NodeInfo {
        val nodeCertificateAndKeyPair = createDevNodeCa(DEV_INTERMEDIATE_CA, name, nodeKeyPair)
        val identityCert = X509Utilities.createCertificate(
                CertificateType.LEGAL_IDENTITY,
                nodeCertificateAndKeyPair.certificate,
                nodeCertificateAndKeyPair.keyPair,
                nodeCertificateAndKeyPair.certificate.subjectX500Principal,
                identityCertPublicKey)
        val certPath = X509Utilities.buildCertPath(
                identityCert,
                nodeCertificateAndKeyPair.certificate,
                DEV_INTERMEDIATE_CA.certificate,
                DEV_ROOT_CA.certificate)
        val partyAndCertificate = PartyAndCertificate(certPath)
        return NodeInfo(
                listOf(NetworkHostAndPort("my.${partyAndCertificate.party.name.organisation}.com", 1234)),
                listOf(partyAndCertificate),
                1,
                1
        )
    }
}
