package com.r3corda.core.node

import com.r3corda.core.node.services.ServiceInfo
import com.r3corda.core.node.services.ServiceType
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ServiceInfoTests {
    val serviceType = object : ServiceType("corda.service.subservice") {}
    val name = "service.name"

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