package net.corda.coretests.node

import net.corda.core.node.NodeInfo
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.testing.core.TestIdentity
import net.corda.testing.core.getTestPartyAndCertificate
import org.junit.Before
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NodeInfoTests {

    private val party1 = TestIdentity.fresh("party1").party
    private val party2 = TestIdentity.fresh("party2").party

    private lateinit var testNode: NodeInfo

    @Before
    fun setup() {
        testNode = NodeInfo(
                addresses = listOf(
                        NetworkHostAndPort("127.0.0.1", 10000)
                ),
                legalIdentitiesAndCerts = listOf(
                        getTestPartyAndCertificate(party1),
                        getTestPartyAndCertificate(party2)
                ),
                platformVersion = 4,
                serial = 0
        )
    }

    @Test
    fun `should return true when the X500Name is present on the node`() {
        assertTrue(testNode.isLegalIdentity(party1.name), "Party 1 must exist on the node")
        assertTrue(testNode.isLegalIdentity(party2.name), "Party 2 must exist on the node")
    }

    @Test
    fun `should return false when the X500Name is not present on the node`() {
        assertFalse(testNode.isLegalIdentity(TestIdentity.fresh("party3").name),
                "Party 3 must not exist on the node")
    }
}
