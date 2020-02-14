package net.corda.client.rpc

import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.ContractState
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.messaging.startFlow
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.serialization.SerializationCustomSerializer
import net.corda.core.serialization.internal._driverSerializationEnv
import net.corda.core.serialization.internal._rpcClientSerializationEnv
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.getOrThrow
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.internal.enclosedCordapp
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.Test
import java.io.NotSerializableException

class RpcCustomSerializersTest {

    @Test(timeout=300_000)
	fun `when custom serializers are not provided, the classpath is scanned to identify any existing ones`() {
        driver(DriverParameters(startNodesInProcess = true, cordappsForAllNodes = listOf(enclosedCordapp()))) {
            val server = startNode(providedName = ALICE_NAME).get()

            withSerializationEnvironmentsReset {
                val client = CordaRPCClient(hostAndPort = server.rpcAddress)

                val serializers = client.getRegisteredCustomSerializers()
                assertThat(serializers).hasSize(2)
                assertThat(serializers).hasOnlyElementsOfTypes(MySerializer::class.java, TestStateSerializer::class.java)
            }
        }
    }

    @Test(timeout=300_000)
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

    @Test(timeout=300_000)
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

    @Test(timeout=300_000)
    fun `when a custom serializer is missing from the rpc client the resulting exception progagtes and client does not reconnect`() {
        driver(DriverParameters(startNodesInProcess = false, cordappsForAllNodes = listOf(enclosedCordapp()))) {
            val server = startNode(providedName = ALICE_NAME).get()

            var numReconnects = 0
            val gracefulReconnect = GracefulReconnect(onReconnect = {++numReconnects})

            withSerializationEnvironmentsReset {
                val client = CordaRPCClient(hostAndPort = server.rpcAddress, customSerializers = emptySet())
                (client.start(server.rpcUsers.first().username, server.rpcUsers.first().password, gracefulReconnect)).use {
                    val rpcOps = it.proxy

                    rpcOps.startFlow(::InsertTestStateFlow, defaultNotaryIdentity).returnValue.getOrThrow()


                    assertThatExceptionOfType(RPCException::class.java).isThrownBy {
                        rpcOps.vaultQueryBy<TestState>().states
                    }.withCauseInstanceOf(NotSerializableException::class.java)
                }
                assertThat(numReconnects).isEqualTo(0)
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

    class TestContract: Contract {
        override fun verify(tx: LedgerTransaction) { }
        class Insert : CommandData
    }

    @BelongsToContract(TestContract::class)
    class TestState(parties: List<AbstractParty>) : ContractState {
        override val participants = parties
    }

    @StartableByRPC
    class InsertTestStateFlow(private val notary: Party): FlowLogic<Unit>() {
        override fun call() {
            val myKey = serviceHub.myInfo.legalIdentities.first().owningKey
            val tx = TransactionBuilder(notary)
                    .addCommand(TestContract.Insert(), myKey)
                    .addOutputState(TestState(serviceHub.myInfo.legalIdentities))
            val stx = serviceHub.signInitialTransaction(tx, myKey)
            subFlow(FinalityFlow(stx, emptyList()))
        }
    }

    @Suppress("UNUSED")
    class TestStateSerializer: SerializationCustomSerializer<TestState, TestStateSerializer.Proxy> {
        data class Proxy(val participants: List<AbstractParty>, val b: Int)
        override fun toProxy(obj: TestState): Proxy = Proxy(obj.participants, 1)
        override fun fromProxy(proxy: Proxy): TestState = TestState(proxy.participants)
    }

}