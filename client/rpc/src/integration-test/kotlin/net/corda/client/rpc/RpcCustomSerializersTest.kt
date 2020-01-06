package net.corda.client.rpc

import net.corda.core.serialization.SerializationCustomSerializer
import net.corda.core.serialization.internal._driverSerializationEnv
import net.corda.core.serialization.internal._rpcClientSerializationEnv
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.internal.enclosedCordapp
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class RpcCustomSerializersTest {

    @Test
    fun `when custom serializers are not provided, the classpath is scanned to identify any existing ones`() {
        driver(DriverParameters(startNodesInProcess = true, cordappsForAllNodes = listOf(enclosedCordapp()))) {
            val server = startNode(providedName = ALICE_NAME).get()

            withSerializationEnvironmentsReset {
                val client = CordaRPCClient(hostAndPort = server.rpcAddress)

                val serializers = client.getRegisteredCustomSerializers()
                assertThat(serializers).hasSize(1)
                assertThat(serializers).hasOnlyElementsOfType(MySerializer::class.java)
            }
        }
    }

    @Test
    fun `when an empty set of custom serializers is provided, no scanning is performed and this empty set is used instead`() {
        driver(DriverParameters(startNodesInProcess = true, cordappsForAllNodes = listOf(enclosedCordapp()))) {
            val server = startNode(providedName = ALICE_NAME).get()

            withSerializationEnvironmentsReset {
                val client = CordaRPCClient(hostAndPort = server.rpcAddress, customSerializers = emptySet())

                val serializers = client.getRegisteredCustomSerializers()
                assertThat(serializers).isEmpty()
            }
        }
    }

    @Test
    fun `when a set of custom serializers is explicitly provided, these are used instead of scanning the classpath`() {
        driver(DriverParameters(startNodesInProcess = true, cordappsForAllNodes = emptyList())) {
            val server = startNode(providedName = ALICE_NAME).get()

            withSerializationEnvironmentsReset {
                val client = CordaRPCClient(hostAndPort = server.rpcAddress, customSerializers = setOf(MySerializer()))

                val serializers = client.getRegisteredCustomSerializers()
                assertThat(serializers).hasSize(1)
                assertThat(serializers).hasOnlyElementsOfType(MySerializer::class.java)
            }
        }
    }

    /**
     * This is done to avoid re-using the serialization environment setup by the driver the same way
     * it will happen when a client is initialised outside a node.
     */
    private fun withSerializationEnvironmentsReset(block: () -> Unit) {
        val driverSerializationEnv = _driverSerializationEnv.get()

        try {
            if (driverSerializationEnv != null) {
                _driverSerializationEnv.set(null)
            }

            block()
        } finally {
            if (driverSerializationEnv != null) {
                _driverSerializationEnv.set(driverSerializationEnv)
            }
            if (_rpcClientSerializationEnv.get() != null) {
                _rpcClientSerializationEnv.set(null)
            }
        }
    }


    class MySerializer: SerializationCustomSerializer<MyMagicClass, MySerializer.Proxy> {
        class Proxy(val magicObject: MyMagicClass)

        override fun fromProxy(proxy: Proxy): MyMagicClass {
            return MyMagicClass(proxy.magicObject.someField)
        }

        override fun toProxy(obj: MyMagicClass): Proxy {
            return Proxy(obj)
        }
    }

    class MyMagicClass(val someField: String)
}