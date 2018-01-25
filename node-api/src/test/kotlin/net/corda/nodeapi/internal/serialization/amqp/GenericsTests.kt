package net.corda.nodeapi.internal.serialization.amqp

import net.corda.core.contracts.*
import net.corda.core.serialization.SerializedBytes
import net.corda.nodeapi.internal.serialization.AllWhitelist
import net.corda.testing.common.internal.ProjectStructure.projectRootDir
import org.junit.Test
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.transactions.WireTransaction
import net.corda.testing.core.TestIdentity
import org.hibernate.Transaction
import java.io.File
import java.net.URI
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.assertEquals

data class TestContractState(
        override val participants: List<AbstractParty>
) : ContractState

class TestAttachmentConstraint : AttachmentConstraint {
    override fun isSatisfiedBy(attachment: Attachment) = true
}

class GenericsTests {
    companion object {
        val VERBOSE = false

        @Suppress("UNUSED")
        var localPath = projectRootDir.toUri().resolve(
                "node-api/src/test/resources/net/corda/nodeapi/internal/serialization/amqp")

        val miniCorp = TestIdentity(CordaX500Name("MiniCorp", "London", "GB"))
    }

    private fun printSeparator() = if (VERBOSE) println("\n\n-------------------------------------------\n\n") else Unit

    private fun <T : Any> BytesAndSchemas<T>.printSchema() = if (VERBOSE) println("${this.schema}\n") else Unit

    private fun ConcurrentHashMap<Any, AMQPSerializer<Any>>.printKeyToType() {
        if (!VERBOSE) return

        forEach {
            println("Key = ${it.key} - ${it.value.type.typeName}")
        }
        println()
    }

    @Test
    fun twoDifferentTypesSameParameterizedOuter() {
        data class G<A>(val a: A)

        val factory = testDefaultFactoryNoEvolution()

        val bytes1 = SerializationOutput(factory).serializeAndReturnSchema(G("hi")).apply { printSchema() }

        factory.getSerializersByDescriptor().printKeyToType()

        val bytes2 = SerializationOutput(factory).serializeAndReturnSchema(G(121)).apply { printSchema() }

        factory.getSerializersByDescriptor().printKeyToType()

        listOf(factory, testDefaultFactory()).forEach { f ->
            DeserializationInput(f).deserialize(bytes1.obj).apply { assertEquals("hi", this.a) }
            DeserializationInput(f).deserialize(bytes2.obj).apply { assertEquals(121, this.a) }
        }
    }

    @Test
    fun doWeIgnoreMultipleParams() {
        data class G1<out T>(val a: T)
        data class G2<out T>(val a: T)
        data class Wrapper<out T>(val a: Int, val b: G1<T>, val c: G2<T>)

        val factory = testDefaultFactoryNoEvolution()
        val factory2 = testDefaultFactoryNoEvolution()

        val bytes = SerializationOutput(factory).serializeAndReturnSchema(Wrapper(1, G1("hi"), G2("poop"))).apply { printSchema() }
        printSeparator()
        DeserializationInput(factory2).deserialize(bytes.obj)
    }

    @Test
    fun nestedSerializationOfGenerics() {
        data class G<out T>(val a: T)
        data class Wrapper<out T>(val a: Int, val b: G<T>)

        val factory = testDefaultFactoryNoEvolution()
        val altContextFactory = testDefaultFactoryNoEvolution()
        val ser = SerializationOutput(factory)

        val bytes = ser.serializeAndReturnSchema(G("hi")).apply { printSchema() }

        factory.getSerializersByDescriptor().printKeyToType()

        assertEquals("hi", DeserializationInput(factory).deserialize(bytes.obj).a)
        assertEquals("hi", DeserializationInput(altContextFactory).deserialize(bytes.obj).a)

        val bytes2 = ser.serializeAndReturnSchema(Wrapper(1, G("hi"))).apply { printSchema() }

        factory.getSerializersByDescriptor().printKeyToType()

        printSeparator()

        DeserializationInput(factory).deserialize(bytes2.obj).apply {
            assertEquals(1, a)
            assertEquals("hi", b.a)
        }

        DeserializationInput(altContextFactory).deserialize(bytes2.obj).apply {
            assertEquals(1, a)
            assertEquals("hi", b.a)
        }
    }

    @Test
    fun nestedGenericsReferencesByteArrayViaSerializedBytes() {
        data class G(val a: Int)
        data class Wrapper<T : Any>(val a: Int, val b: SerializedBytes<T>)

        val factory = SerializerFactory(AllWhitelist, ClassLoader.getSystemClassLoader())
        val factory2 = SerializerFactory(AllWhitelist, ClassLoader.getSystemClassLoader())
        val ser = SerializationOutput(factory)

        val gBytes = ser.serialize(G(1))
        val bytes2 = ser.serializeAndReturnSchema(Wrapper<G>(1, gBytes))

        DeserializationInput(factory).deserialize(bytes2.obj).apply {
            assertEquals(1, a)
            assertEquals(1, DeserializationInput(factory).deserialize(b).a)
        }
        DeserializationInput(factory2).deserialize(bytes2.obj).apply {
            assertEquals(1, a)
            assertEquals(1, DeserializationInput(factory).deserialize(b).a)
        }
    }

    @Test
    fun nestedSerializationInMultipleContextsDoesntColideGenericTypes() {
        data class InnerA(val a_a: Int)
        data class InnerB(val a_b: Int)
        data class InnerC(val a_c: String)
        data class Container<T>(val b: T)
        data class Wrapper<T : Any>(val c: Container<T>)

        val factory = SerializerFactory(AllWhitelist, ClassLoader.getSystemClassLoader())
        val factories = listOf(factory, SerializerFactory(AllWhitelist, ClassLoader.getSystemClassLoader()))
        val ser = SerializationOutput(factory)

        ser.serialize(Wrapper(Container(InnerA(1)))).apply {
            factories.forEach {
                DeserializationInput(it).deserialize(this).apply { assertEquals(1, c.b.a_a) }
                it.getSerializersByDescriptor().printKeyToType(); printSeparator()
            }
        }

        ser.serialize(Wrapper(Container(InnerB(1)))).apply {
            factories.forEach {
                DeserializationInput(it).deserialize(this).apply { assertEquals(1, c.b.a_b) }
                it.getSerializersByDescriptor().printKeyToType(); printSeparator()
            }
        }

        ser.serialize(Wrapper(Container(InnerC("Ho ho ho")))).apply {
            factories.forEach {
                DeserializationInput(it).deserialize(this).apply { assertEquals("Ho ho ho", c.b.a_c) }
                it.getSerializersByDescriptor().printKeyToType(); printSeparator()
            }
        }
    }

    @Test
    fun nestedSerializationWhereGenericDoesntImpactFingerprint() {
        data class Inner(val a: Int)
        data class Container<T : Any>(val b: Inner)
        data class Wrapper<T : Any>(val c: Container<T>)

        val factorys = listOf(
                SerializerFactory(AllWhitelist, ClassLoader.getSystemClassLoader()),
                SerializerFactory(AllWhitelist, ClassLoader.getSystemClassLoader()))

        val ser = SerializationOutput(factorys[0])

        ser.serialize(Wrapper<Int>(Container(Inner(1)))).apply {
            factorys.forEach {
                assertEquals(1, DeserializationInput(it).deserialize(this).c.b.a)
            }
        }

        ser.serialize(Wrapper<String>(Container(Inner(1)))).apply {
            factorys.forEach {
                assertEquals(1, DeserializationInput(it).deserialize(this).c.b.a)
            }
        }
    }

    data class ForceWildcard<out T>(val t: T)

    private fun forceWildcardSerialize(
            a: ForceWildcard<*>,
            factory: SerializerFactory = SerializerFactory(AllWhitelist, ClassLoader.getSystemClassLoader())): SerializedBytes<*> {
        val bytes = SerializationOutput(factory).serializeAndReturnSchema(a)
        factory.getSerializersByDescriptor().printKeyToType()
        bytes.printSchema()
        return bytes.obj
    }

    @Suppress("UNCHECKED_CAST")
    private fun forceWildcardDeserializeString(
            bytes: SerializedBytes<*>,
            factory: SerializerFactory = SerializerFactory(AllWhitelist, ClassLoader.getSystemClassLoader())) {
        DeserializationInput(factory).deserialize(bytes as SerializedBytes<ForceWildcard<String>>)
    }

    @Suppress("UNCHECKED_CAST")
    private fun forceWildcardDeserializeDouble(
            bytes: SerializedBytes<*>,
            factory: SerializerFactory = SerializerFactory(AllWhitelist, ClassLoader.getSystemClassLoader())) {
        DeserializationInput(factory).deserialize(bytes as SerializedBytes<ForceWildcard<Double>>)
    }

    @Suppress("UNCHECKED_CAST")
    private fun forceWildcardDeserialize(
            bytes: SerializedBytes<*>,
            factory: SerializerFactory = SerializerFactory(AllWhitelist, ClassLoader.getSystemClassLoader())) {
        DeserializationInput(factory).deserialize(bytes as SerializedBytes<ForceWildcard<*>>)
    }

    @Test
    fun forceWildcard() {
        forceWildcardDeserializeString(forceWildcardSerialize(ForceWildcard("hello")))
        forceWildcardDeserializeDouble(forceWildcardSerialize(ForceWildcard(3.0)))
    }

    @Test
    fun forceWildcardSharedFactory() {
        val f = SerializerFactory(AllWhitelist, ClassLoader.getSystemClassLoader())
        forceWildcardDeserializeString(forceWildcardSerialize(ForceWildcard("hello"), f), f)
        forceWildcardDeserializeDouble(forceWildcardSerialize(ForceWildcard(3.0), f), f)
    }

    @Test
    fun forceWildcardDeserialize() {
        forceWildcardDeserialize(forceWildcardSerialize(ForceWildcard("hello")))
        forceWildcardDeserialize(forceWildcardSerialize(ForceWildcard(10)))
        forceWildcardDeserialize(forceWildcardSerialize(ForceWildcard(20.0)))
    }

    @Test
    fun forceWildcardDeserializeSharedFactory() {
        val f = SerializerFactory(AllWhitelist, ClassLoader.getSystemClassLoader())
        forceWildcardDeserialize(forceWildcardSerialize(ForceWildcard("hello"), f), f)
        forceWildcardDeserialize(forceWildcardSerialize(ForceWildcard(10), f), f)
        forceWildcardDeserialize(forceWildcardSerialize(ForceWildcard(20.0), f), f)
    }

    @Test
    fun loadGenericFromFile() {
        val resource = "${javaClass.simpleName}.${testName()}"
        val sf = testDefaultFactory()

        // Uncomment to re-generate test files, needs to be done in three stages
        // File(URI("$localPath/$resource")).writeBytes(forceWildcardSerialize(ForceWildcard("wibble")).bytes)

        assertEquals("wibble",
                DeserializationInput(sf).deserialize(SerializedBytes<ForceWildcard<*>>(
                        File(GenericsTests::class.java.getResource(resource).toURI()).readBytes())).t)
    }

    data class StateAndString(val state: TransactionState<*>, val ref: String)
    data class GenericStateAndString<out T: ContractState>(val state: TransactionState<T>, val ref: String)

    //
    // If this doesn't blow up all is fine
    private fun fingerprintingDiffersStrip(state: Any) {
        class cl : ClassLoader()

        val m = ClassLoader::class.java.getDeclaredMethod("findLoadedClass", *arrayOf<Class<*>>(String::class.java))
        m.isAccessible = true

        val factory1 = testDefaultFactory()
        factory1.register(net.corda.nodeapi.internal.serialization.amqp.custom.PublicKeySerializer)
        val ser1 = TestSerializationOutput(VERBOSE, factory1).serializeAndReturnSchema(state)

        // attempt at having a class loader without some of the derived non core types loaded and thus
        // possibly altering how we serialise things
        val altClassLoader = cl()

        val factory2 = SerializerFactory(AllWhitelist, altClassLoader)
        factory2.register(net.corda.nodeapi.internal.serialization.amqp.custom.PublicKeySerializer)
        val ser2 = TestSerializationOutput(VERBOSE, factory2).serializeAndReturnSchema(state)

        //  now deserialise those objects
        val factory3 = testDefaultFactory()
        factory3.register(net.corda.nodeapi.internal.serialization.amqp.custom.PublicKeySerializer)
        val des1 = DeserializationInput(factory3).deserializeAndReturnEnvelope(ser1.obj)

        val factory4 = SerializerFactory(AllWhitelist, cl())
        factory4.register(net.corda.nodeapi.internal.serialization.amqp.custom.PublicKeySerializer)
        val des2 = DeserializationInput(factory4).deserializeAndReturnEnvelope(ser2.obj)

    }

    @Test
    fun fingerprintingDiffers() {
        val state = TransactionState<TestContractState> (
                TestContractState(listOf(miniCorp.party)),
                "wibble", miniCorp.party,
                encumbrance = null,
                constraint = TestAttachmentConstraint())

        val sas = StateAndString(state, "wibble")

        fingerprintingDiffersStrip(sas)
    }

    @Test
    fun fingerprintingDiffersList() {
        val state = TransactionState<TestContractState> (
                TestContractState(listOf(miniCorp.party)),
                "wibble", miniCorp.party,
                encumbrance = null,
                constraint = TestAttachmentConstraint())

        val sas = StateAndString(state, "wibble")

        fingerprintingDiffersStrip(Collections.singletonList(sas))
    }


    //
    // Force object to be serialised as Example<T> and deserialized as Example<?>
    //
    @Test
    fun fingerprintingDiffersListLoaded() {
        //
        // using this wrapper class we force the object to be serialised as
        //      net.corda.core.contracts.TransactionState<T>
        //
        data class TransactionStateWrapper<out T : ContractState> (val o: List<GenericStateAndString<T>>)

        val state = TransactionState<TestContractState> (
                TestContractState(listOf(miniCorp.party)),
                "wibble", miniCorp.party,
                encumbrance = null,
                constraint = TestAttachmentConstraint())

        val sas = GenericStateAndString(state, "wibble")

        val factory1 = testDefaultFactoryNoEvolution()
        val factory2 = testDefaultFactory()

        factory1.register(net.corda.nodeapi.internal.serialization.amqp.custom.PublicKeySerializer)
        factory2.register(net.corda.nodeapi.internal.serialization.amqp.custom.PublicKeySerializer)

        val ser1 = TestSerializationOutput(VERBOSE, factory1).serializeAndReturnSchema(
                TransactionStateWrapper(Collections.singletonList(sas)))

        val des1 = DeserializationInput(factory2).deserializeAndReturnEnvelope(ser1.obj)

        assertEquals(sas.ref, des1.obj.o.firstOrNull()?.ref ?: "WILL NOT MATCH")
    }

    @Test
    fun anotherTry() {
        open class BaseState(val a : Int)
        class DState(a: Int) : BaseState(a)
        data class LTransactionState<out T : BaseState> constructor(val data: T)
        data class StateWrapper<out T : BaseState>(val state: LTransactionState<T>)

        val factory1 = testDefaultFactoryNoEvolution()

        val state = LTransactionState(DState(1020304))
        val stateAndString = StateWrapper(state)

        val ser1 = TestSerializationOutput(VERBOSE, factory1).serializeAndReturnSchema(stateAndString)

        //val factory2 = testDefaultFactoryNoEvolution()
        val factory2 = testDefaultFactory()
        val des1 = DeserializationInput(factory2).deserializeAndReturnEnvelope(ser1.obj)

        assertEquals(state.data.a, des1.obj.state.data.a)
    }

}
