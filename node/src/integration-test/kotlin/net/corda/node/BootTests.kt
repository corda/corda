package net.corda.node

import net.corda.core.flows.FlowLogic
import net.corda.core.getOrThrow
import net.corda.core.messaging.startFlow
import net.corda.core.node.CordaPluginRegistry
import net.corda.node.driver.driver
import net.corda.node.services.startFlowPermission
import net.corda.nodeapi.User
import org.junit.Test
import java.io.*
import kotlin.test.assertEquals
import kotlin.test.fail

class BootTests {

    @Test
    fun `java deserialization is disabled`() {
        driver {
            val user = User("u", "p", setOf(startFlowPermission<ObjectInputStreamFlow>()))
            val future = startNode(rpcUsers = listOf(user)).getOrThrow().rpcClientToNode().apply { start(user.username, user.password) }.proxy().startFlow(::ObjectInputStreamFlow).returnValue
            try {
                future.getOrThrow()
                fail("Expected invalid class.")
            } catch (e: InvalidClassException) {
                assertEquals("filter status: REJECTED", e.message)
            }
        }
    }

}

class ObjectInputStreamFlow : FlowLogic<Unit>() {

    override fun call() {
        val data = ByteArrayOutputStream().apply { ObjectOutputStream(this).use { it.writeObject(object : Serializable {}) } }.toByteArray()
        ObjectInputStream(data.inputStream()).use { it.readObject() }
    }

}

class BootTestsPlugin : CordaPluginRegistry() {

    override val requiredFlows: Map<String, Set<String>> = mapOf(ObjectInputStreamFlow::class.java.name to setOf())

}
