package net.corda.core.node

import net.corda.core.identity.CordaX500Name
import net.corda.core.node.services.ServiceInfo
import net.corda.core.node.services.ServiceType
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ServiceInfoTests {
    val serviceType = ServiceType.getServiceType("test", "service").getSubType("subservice")
    val name = CordaX500Name(organisation = "service.name", locality = "London", country = "GB")

    @Test
    fun `type and name encodes correctly`() {
        assertEquals(ServiceInfo(serviceType, name).toString(), "$serviceType|$name")
    }

    @Test
    fun `type and name parses correctly`() {
        assertEquals(ServiceInfo.parse("$serviceType|$name"), ServiceInfo(serviceType, name))
    }

    @Test
    fun `type only encodes correctly`() {
        assertEquals(ServiceInfo(serviceType).toString(), "$serviceType")
    }

    @Test
    fun `type only parses correctly`() {
        assertEquals(ServiceInfo.parse("$serviceType"), ServiceInfo(serviceType))
    }

    @Test
    fun `invalid encoding throws`() {
        assertFailsWith<IllegalArgumentException> { ServiceInfo.parse("$serviceType|$name|something") }
    }
}
