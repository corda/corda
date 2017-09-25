package net.corda.demobench.model

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ServiceControllerTest {

    @Test
    fun `test empty`() {
        val controller = ServiceController("/empty-services.conf")
        assertNotNull(controller.services)
        assertTrue(controller.services.isEmpty())

        assertNotNull(controller.notaries)
        assertTrue(controller.notaries.isEmpty())
    }

    @Test
    fun `test duplicates`() {
        val controller = ServiceController("/duplicate-services.conf")
        assertNotNull(controller.services)
        assertEquals(listOf("corda.example"), controller.services.map { it.value })
    }

    @Test
    fun `test notaries`() {
        val controller = ServiceController("/notary-services.conf")
        assertNotNull(controller.notaries)
        assertEquals(listOf("corda.notary.simple"), controller.notaries.map { it.value })
    }

    @Test
    fun `test services`() {
        val controller = ServiceController()
        assertNotNull(controller.services)
        assertTrue(controller.services.isNotEmpty())

        assertNotNull(controller.notaries)
        assertTrue(controller.notaries.isNotEmpty())
    }

}