package net.corda.node.utilities.registration

import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.seconds
import net.corda.nodeapi.internal.crypto.CertificateAndKeyPair
import net.corda.testing.driver.PortAllocation
import net.corda.testing.internal.DEV_ROOT_CA
import net.corda.testing.node.internal.network.NetworkMapServer
import org.junit.rules.ExternalResource

class TestDoorman(private val portAllocation: PortAllocation, rootCertAndKeyPair: CertificateAndKeyPair = DEV_ROOT_CA) : ExternalResource() {

    private val registrationHandler = RegistrationHandler(rootCertAndKeyPair)
    internal lateinit var server: NetworkMapServer
    internal lateinit var serverHostAndPort: NetworkHostAndPort

    override fun before() {
        server = NetworkMapServer(
                pollInterval = 1.seconds,
                hostAndPort = portAllocation.nextHostAndPort(),
                myHostNameValue = "localhost",
                additionalServices = *arrayOf(registrationHandler))
        serverHostAndPort = server.start()
    }

    override fun after() {
        server.close()
    }
}
