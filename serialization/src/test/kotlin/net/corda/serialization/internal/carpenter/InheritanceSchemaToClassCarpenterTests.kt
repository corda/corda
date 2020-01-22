package net.corda.serialization.internal.carpenter

import net.corda.core.serialization.CordaSerializable
import net.corda.serialization.internal.AllWhitelist
import net.corda.serialization.internal.amqp.testutils.testSerializationContext
import org.junit.Test
import kotlin.test.*
import java.io.NotSerializableException

@CordaSerializable
interface J {
    val j: Int
}

@CordaSerializable
interface I {
    val i: Int
}

@CordaSerializable
interface II {
    val ii: Int
}

@CordaSerializable
interface III : I {
    val iii: Int
    override val i: Int
}

@CordaSerializable
interface IIII {
    val iiii: Int
    val i: I
}

class InheritanceSchemaToClassCarpenterTests : AmqpCarpenterBase(AllWhitelist) {
    @Test(timeout=300_000)
	fun interfaceParent1() {
        class A(override val j: Int) : J

        val (_, env) = A(20).roundTrip()
        val mangledA = env.getMangled<A>()

        val carpentedA = mangledA.load(testSerializationContext)
        val carpentedInstance = carpentedA.new(20)

        assertEquals(20, carpentedInstance.get("j"))

        val asJ = carpentedInstance as J
        assertEquals(20, asJ.j)
    }

    @Test(timeout=300_000)
	fun interfaceParent2() {
        @Suppress("UNUSED")
        class A(override val j: Int, val jj: Int) : J

        val (_, env) = A(23, 42).roundTrip()
        val carpentedA = env.getMangled<A>().load(testSerializationContext)
        val carpetedInstance = carpentedA.constructors[0].newInstance(23, 42)

        assertEquals(23, carpetedInstance.get("j"))
        assertEquals(42, carpetedInstance.get("jj"))

        val asJ = carpetedInstance as J
        assertEquals(23, asJ.j)
    }

    @Test(timeout=300_000)
	fun multipleInterfaces() {
        class A(override val i: Int, override val ii: Int) : I, II

        val (_, env) = A(23, 42).roundTrip()
        val carpentedA = env.getMangled<A>().load(testSerializationContext)
        val carpetedInstance = carpentedA.constructors[0].newInstance(23, 42)

        assertEquals(23, carpetedInstance.get("i"))
        assertEquals(42, carpetedInstance.get("ii"))

        val i = carpetedInstance as I
        val ii = carpetedInstance as II

        assertEquals(23, i.i)
        assertEquals(42, ii.ii)
    }

    @Test(timeout=300_000)
	fun nestedInterfaces() {
        class A(override val i: Int, override val iii: Int) : III

        val (_, env) = A(23, 42).roundTrip()
        val carpentedA = env.getMangled<A>().load(testSerializationContext)
        val carpetedInstance = carpentedA.constructors[0].newInstance(23, 42)

        assertEquals(23, carpetedInstance.get("i"))
        assertEquals(42, carpetedInstance.get("iii"))

        val i = carpetedInstance as I
        val iii = carpetedInstance as III

        assertEquals(23, i.i)
        assertEquals(23, iii.i)
        assertEquals(42, iii.iii)
    }

    @Test(timeout=300_000)
	fun memberInterface() {
        class A(override val i: Int) : I
        class B(override val i: I, override val iiii: Int) : IIII

        val (_, env) = B(A(23), 42).roundTrip()
        val carpentedA = env.getMangled<A>().load(testSerializationContext)
        val carpentedB = env.getMangled<B>().load(testSerializationContext)

        val carpentedAInstance = carpentedA.new(23)
        val carpentedBInstance = carpentedB.new(carpentedAInstance, 42)

        val iiii = carpentedBInstance as IIII
        assertEquals(23, iiii.i.i)
        assertEquals(42, iiii.iiii)
    }

    @Test(timeout=300_000)
	fun memberInterface2() {
        class A(override val i: Int) : I

        val (_, env) = A(23).roundTrip()

        // if we remove the nested interface we should get an error as it's impossible
        // to have a concrete class loaded without having access to all of it's elements
        assertFailsWith<NotSerializableException> { assertCanLoadAll(
                testSerializationContext,
                env.getMangled<A>().mangle<I>()) }
    }

    @Test(timeout=300_000)
	fun interfaceAndImplementation() {
        class A(override val i: Int) : I

        val (_, env) = A(23).roundTrip()

        // This time around we will succeed, because the mangled I is included in the type information to be loaded.
        assertCanLoadAll(testSerializationContext, env.getMangled<A>().mangle<I>(), env.getMangled<I>())
    }

    @Test(timeout=300_000)
	fun twoInterfacesAndImplementation() {
        class A(override val i: Int, override val ii: Int) : I, II

        val (_, env) = A(23, 42).roundTrip()
        assertCanLoadAll(
                testSerializationContext,
                env.getMangled<A>().mangle<I>().mangle<II>(),
                env.getMangled<I>(),
                env.getMangled<II>()
        )
    }

    @Test(timeout=300_000)
	fun nestedInterfacesAndImplementation() {
        class A(override val i: Int, override val iii: Int) : III

        val (_, env) = A(23, 42).roundTrip()
        assertCanLoadAll(
                testSerializationContext,
                env.getMangled<A>().mangle<I>().mangle<III>(),
                env.getMangled<I>(),
                env.getMangled<III>().mangle<I>()
        )
    }
}
