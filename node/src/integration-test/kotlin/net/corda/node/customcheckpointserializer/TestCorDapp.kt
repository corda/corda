package net.corda.node.customcheckpointserializer

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.serialization.CheckpointCustomSerializer
import net.corda.testing.node.internal.CustomCordapp
import net.corda.testing.node.internal.enclosedCordapp
import net.i2p.crypto.eddsa.EdDSAPublicKey
import org.assertj.core.api.Assertions
import java.security.PublicKey
import java.time.Duration

/**
 * Contains all the flows and custom serializers for testing custom checkpoint serializers
 */
class TestCorDapp {

    companion object {
        fun getCorDapp(): CustomCordapp = enclosedCordapp()
    }

    // Flows
    @StartableByRPC
    class TestFlowWithDifficultToSerializeLocalVariableAsAbstract(private val purchase: Int) : FlowLogic<Int>() {
        @Suspendable
        override fun call(): Int {

            // This object is difficult to serialize with Kryo
            val difficultToSerialize: DifficultToSerialize.BrokenMapAbstract<String, Int> = DifficultToSerialize.BrokenMapAbstractImpl()
            difficultToSerialize.putAll(mapOf("foo" to purchase))

            // Force a checkpoint
            sleep(Duration.ofSeconds(0))

            // Return value from deserialized object
            return difficultToSerialize["foo"] ?: 0
        }
    }

    @StartableByRPC
    class TestFlowWithDifficultToSerializeLocalVariableAsFinal(private val purchase: Int) : FlowLogic<Int>() {
        @Suspendable
        override fun call(): Int {

            // This object is difficult to serialize with Kryo
            val difficultToSerialize: DifficultToSerialize.BrokenMapFinal<String, Int> = DifficultToSerialize.BrokenMapFinal()
            difficultToSerialize.putAll(mapOf("foo" to purchase))

            // Force a checkpoint
            sleep(Duration.ofSeconds(0))

            // Return value from deserialized object
            return difficultToSerialize["foo"] ?: 0
        }
    }

    @StartableByRPC
    class TestFlowWithDifficultToSerializeLocalVariableAsInterface(private val purchase: Int) : FlowLogic<Int>() {
        @Suspendable
        override fun call(): Int {

            // This object is difficult to serialize with Kryo
            val difficultToSerialize: DifficultToSerialize.BrokenMapInterface<String, Int> = DifficultToSerialize.BrokenMapInterfaceImpl()
            difficultToSerialize.putAll(mapOf("foo" to purchase))

            // Force a checkpoint
            sleep(Duration.ofSeconds(0))

            // Return value from deserialized object
            return difficultToSerialize["foo"] ?: 0
        }
    }

    @StartableByRPC
    class TestFlowWithDifficultToSerializeLocalVariable(private val purchase: Int) : FlowLogic<Int>() {
        @Suspendable
        override fun call(): Int {

            // This object is difficult to serialize with Kryo
            val difficultToSerialize: DifficultToSerialize.BrokenMapClass<String, Int> = DifficultToSerialize.BrokenMapClass()
            difficultToSerialize.putAll(mapOf("foo" to purchase))

            // Force a checkpoint
            sleep(Duration.ofSeconds(0))

            // Return value from deserialized object
            return difficultToSerialize["foo"] ?: 0
        }
    }

    @StartableByRPC
    class TestFlowCheckingReferencesWork(private val reference: DifficultToSerialize.BrokenMapClass<String, Int>) :
            FlowLogic<DifficultToSerialize.BrokenMapClass<String, Int>>() {

        private val referenceField = reference
        @Suspendable
        override fun call(): DifficultToSerialize.BrokenMapClass<String, Int> {

            val ref = referenceField

            // Force a checkpoint
            sleep(Duration.ofSeconds(0))

            // Check all objects refer to same object
            Assertions.assertThat(reference).isSameAs(referenceField)
            Assertions.assertThat(referenceField).isSameAs(ref)

            // Return deserialized object
            return ref
        }
    }


    @StartableByRPC
    class TestFlowCheckingPublicKeySerializer :
            FlowLogic<PublicKey>() {

        @Suspendable
        override fun call(): PublicKey {
            val ref = ourIdentity.owningKey

            // Force a checkpoint
            sleep(Duration.ofSeconds(0))

            // Return deserialized object
            return ref
        }
    }

    // Custom serializers

    @Suppress("unused")
    class TestInterfaceSerializer :
            CheckpointCustomSerializer<DifficultToSerialize.BrokenMapInterface<Any, Any>, HashMap<Any, Any>> {

        override fun toProxy(obj: DifficultToSerialize.BrokenMapInterface<Any, Any>): HashMap<Any, Any> {
            val proxy = HashMap<Any, Any>()
            return obj.toMap(proxy)
        }
        override fun fromProxy(proxy: HashMap<Any, Any>): DifficultToSerialize.BrokenMapInterface<Any, Any> {
            return DifficultToSerialize.BrokenMapInterfaceImpl<Any, Any>()
                    .also { it.putAll(proxy) }
        }
    }

    @Suppress("unused")
    class TestClassSerializer :
            CheckpointCustomSerializer<DifficultToSerialize.BrokenMapClass<Any, Any>, HashMap<Any, Any>> {

        override fun toProxy(obj: DifficultToSerialize.BrokenMapClass<Any, Any>): HashMap<Any, Any> {
            val proxy = HashMap<Any, Any>()
            return obj.toMap(proxy)
        }
        override fun fromProxy(proxy: HashMap<Any, Any>): DifficultToSerialize.BrokenMapClass<Any, Any> {
            return DifficultToSerialize.BrokenMapClass<Any, Any>()
                    .also { it.putAll(proxy) }
        }
    }

    @Suppress("unused")
    class TestAbstractClassSerializer :
            CheckpointCustomSerializer<DifficultToSerialize.BrokenMapAbstract<Any, Any>, HashMap<Any, Any>> {

        override fun toProxy(obj: DifficultToSerialize.BrokenMapAbstract<Any, Any>): HashMap<Any, Any> {
            val proxy = HashMap<Any, Any>()
            return obj.toMap(proxy)
        }
        override fun fromProxy(proxy: HashMap<Any, Any>): DifficultToSerialize.BrokenMapAbstract<Any, Any> {
            return DifficultToSerialize.BrokenMapAbstractImpl<Any, Any>()
                    .also { it.putAll(proxy) }
        }
    }

    @Suppress("unused")
    class TestFinalClassSerializer :
            CheckpointCustomSerializer<DifficultToSerialize.BrokenMapFinal<Any, Any>, HashMap<Any, Any>> {

        override fun toProxy(obj: DifficultToSerialize.BrokenMapFinal<Any, Any>): HashMap<Any, Any> {
            val proxy = HashMap<Any, Any>()
            return obj.toMap(proxy)
        }
        override fun fromProxy(proxy: HashMap<Any, Any>): DifficultToSerialize.BrokenMapFinal<Any, Any> {
            return DifficultToSerialize.BrokenMapFinal<Any, Any>()
                    .also { it.putAll(proxy) }
        }
    }

    @Suppress("unused")
    class BrokenPublicKeySerializer :
            CheckpointCustomSerializer<PublicKey, String> {
        override fun toProxy(obj: PublicKey): String {
            throw FlowException("Broken on purpose")
        }

        override fun fromProxy(proxy: String): PublicKey {
            throw FlowException("Broken on purpose")
        }
    }

    @Suppress("unused")
    class BrokenEdDSAPublicKeySerializer :
            CheckpointCustomSerializer<EdDSAPublicKey, String> {
        override fun toProxy(obj: EdDSAPublicKey): String {
            throw FlowException("Broken on purpose")
        }

        override fun fromProxy(proxy: String): EdDSAPublicKey {
            throw FlowException("Broken on purpose")
        }
    }

}
