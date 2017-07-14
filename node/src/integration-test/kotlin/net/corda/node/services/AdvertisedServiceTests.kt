package net.corda.node.services

import com.google.common.util.concurrent.Futures
import net.corda.core.getOrThrow
import net.corda.core.node.services.ServiceInfo
import net.corda.core.node.services.ServiceType
import net.corda.testing.DUMMY_BANK_A
import net.corda.testing.DUMMY_BANK_B
import net.corda.testing.node.NodeBasedTest
import org.bouncycastle.asn1.x500.X500Name
import org.junit.Test
import kotlin.test.assertEquals

class AdvertisedServiceTests : NodeBasedTest() {
    private val serviceName = X500Name("CN=Custom Service,O=R3,OU=corda,L=London,C=GB")
    private val serviceType = ServiceType.corda.getSubType("custom")

    @Test
    fun `service is accessible through getServiceOf`() {
        val (bankA) = Futures.allAsList(
                startNode(DUMMY_BANK_A.name),
                startNode(DUMMY_BANK_B.name, advertisedServices = setOf(ServiceInfo(serviceType, serviceName)))
        ).getOrThrow()
        val serviceParty = bankA.services.networkMapCache.getServiceOf(serviceType)
        assertEquals(serviceName, serviceParty?.name)
    }
}
