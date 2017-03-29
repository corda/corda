package net.corda.node

import net.corda.core.flows.FlowLogic
import net.corda.core.getOrThrow
import net.corda.core.messaging.startFlow
import net.corda.core.node.CordaPluginRegistry
import net.corda.node.driver.driver
import net.corda.node.services.startFlowPermission
import net.corda.nodeapi.User
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import java.io.*

class BootTests {

    @Test
    fun `java deserialization is disabled`() {
        driver {
            val user = User("u", "p", setOf(startFlowPermission<ObjectInputStreamFlow>()))
            val future = startNode(rpcUsers = listOf(user)).getOrThrow().rpcClientToNode().
                start(user.username, user.password).proxy.startFlow(::ObjectInputStreamFlow).returnValue
            assertThatThrownBy { future.getOrThrow() }.isInstanceOf(InvalidClassException::class.java).hasMessage("filter status: REJECTED")
        }
    }

}

class ObjectInputStreamFlow : FlowLogic<Unit>() {

    override fun call() {
        System.clearProperty("jdk.serialFilter") // This checks that the node has already consumed the property.
        val data = ByteArrayOutputStream().apply { ObjectOutputStream(this).use { it.writeObject(object : Serializable {}) } }.toByteArray()
        ObjectInputStream(data.inputStream()).use { it.readObject() }
    }

}

class BootTestsPlugin : CordaPluginRegistry() {

    override val requiredFlows: Map<String, Set<String>> = mapOf(ObjectInputStreamFlow::class.java.name to setOf())

}
