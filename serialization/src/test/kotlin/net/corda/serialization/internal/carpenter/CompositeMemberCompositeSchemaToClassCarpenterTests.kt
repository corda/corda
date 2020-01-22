package net.corda.serialization.internal.carpenter

import net.corda.core.serialization.CordaSerializable
import net.corda.serialization.internal.AllWhitelist
import net.corda.serialization.internal.amqp.testutils.testSerializationContext
import org.junit.Test
import java.io.NotSerializableException
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@CordaSerializable
interface I_ {
    val a: Int
}

class CompositeMembers : AmqpCarpenterBase(AllWhitelist) {

    @Test(timeout=300_000)
	fun parentIsUnknown() {
        @CordaSerializable
        data class A(val a: Int)

        @CordaSerializable
        data class B(val a: A, var b: Int)

        val (_, envelope) = B(A(10), 20).roundTrip()

        // We load an unknown class, B_mangled, which includes a reference to a known class, A.
        assertCanLoadAll(testSerializationContext, envelope.getMangled<B>())
    }

    @Test(timeout=300_000)
	fun bothAreUnknown() {
        @CordaSerializable
        data class A(override val a: Int) : I_

        @CordaSerializable
        data class B(val a: A, var b: Int)

        val (_, envelope) = B(A(10), 20).roundTrip()

        // We load an unknown class, B_mangled, which includes a reference to an unknown class, A_mangled.
        // For this to work, we must include A_mangled in our set of classes to load.
        assertCanLoadAll(testSerializationContext,
                envelope.getMangled<B>().mangle<A>(), envelope.getMangled<A>())
    }

    @Test(timeout=300_000)
	fun oneIsUnknown() {
        @CordaSerializable
        data class A(override val a: Int) : I_

        @CordaSerializable
        data class B(val a: A, var b: Int)

        val (_, envelope) = B(A(10), 20).roundTrip()

        // We load an unknown class, B_mangled, which includes a reference to an unknown class, A_mangled.
        // This will fail, because A_mangled is not included in our set of classes to load.
        assertFailsWith<NotSerializableException> { assertCanLoadAll(testSerializationContext,
                envelope.getMangled<B>().mangle<A>()) }
    }

    // See https://github.com/corda/corda/issues/4107
    @Test(timeout=300_000)
	fun withUUID() {
        @CordaSerializable
        data class IOUStateData(
                val value: Int,
                val ref: UUID,
                val newValue: String? = null
        )

        val uuid = UUID.randomUUID()
        val(_, envelope) = IOUStateData(10, uuid, "new value").roundTrip()
        val recarpented = envelope.getMangled<IOUStateData>().load(testSerializationContext)
        val instance = recarpented.new(null, uuid, 10)
        assertEquals(uuid, instance.get("ref"))
    }

    @Test(timeout=300_000)
	fun mapWithUnknown() {
        data class C(val a: Int)
        data class D(val m: Map<String, C>)
        val (_, envelope) = D(mapOf("c" to C(1))).roundTrip()

        val infoForD = envelope.typeInformationFor<D>().mangle<C>()
        val mangledMap = envelope.typeInformation.values.find { it.typeIdentifier.name == "java.util.Map" }!!.mangle<C>()
        val mangledC = envelope.getMangled<C>()

        assertEquals(
                "java.util.Map<java.lang.String, ${mangledC.typeIdentifier.prettyPrint(false)}>",
                mangledMap.prettyPrint(false))

        assertCanLoadAll(testSerializationContext, infoForD, mangledMap, mangledC)
    }

    @Test(timeout=300_000)
	fun parameterisedNonCollectionWithUnknown() {
        data class C(val a: Int)
        data class NotAMap<K, V>(val key: K, val value: V)
        data class D(val m: NotAMap<String, C>)
        val (_, envelope) = D(NotAMap("c" , C(1))).roundTrip()

        val infoForD = envelope.typeInformationFor<D>().mangle<C>()
        val mangledNotAMap = envelope.typeInformationFor<NotAMap<String, C>>().mangle<C>()
        val mangledC = envelope.getMangled<C>()

        assertCanLoadAll(testSerializationContext, infoForD, mangledNotAMap, mangledC)
    }
}
