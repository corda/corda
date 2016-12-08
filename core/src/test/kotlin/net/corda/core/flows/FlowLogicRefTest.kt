package net.corda.core.flows

import net.corda.core.days
import org.junit.Before
import org.junit.Test
import java.time.Duration

class FlowLogicRefTest {

    data class ParamType1(val value: Int)
    data class ParamType2(val value: String)

    @Suppress("UNUSED_PARAMETER", "unused") // Things are used via reflection.
    class KotlinFlowLogic(A: ParamType1, b: ParamType2) : FlowLogic<Unit>() {
        constructor() : this(ParamType1(1), ParamType2("2"))

        constructor(C: ParamType2) : this(ParamType1(1), C)

        constructor(illegal: Duration) : this(ParamType1(1), ParamType2(illegal.toString()))

        constructor(primitive: String) : this(ParamType1(1), ParamType2(primitive))

        constructor(kotlinType: Int) : this(ParamType1(kotlinType), ParamType2("b"))

        override fun call() = Unit
    }

    class KotlinNoArgFlowLogic : FlowLogic<Unit>() {
        override fun call() = Unit
    }

    @Suppress("UNUSED_PARAMETER") // We will never use A or b
    class NotWhiteListedKotlinFlowLogic(A: Int, b: String) : FlowLogic<Unit>() {
        override fun call() = Unit
    }

    lateinit var factory: FlowLogicRefFactory

    @Before
    fun setup() {
        // We have to allow Java boxed primitives but Kotlin warns we shouldn't be using them
        factory = FlowLogicRefFactory(mapOf(Pair(KotlinFlowLogic::class.java.name, setOf(ParamType1::class.java.name, ParamType2::class.java.name)),
                Pair(KotlinNoArgFlowLogic::class.java.name, setOf())))
    }

    @Test
    fun testCreateKotlinNoArg() {
        factory.create(KotlinNoArgFlowLogic::class.java)
    }

    @Test
    fun testCreateKotlin() {
        val args = mapOf(Pair("A", ParamType1(1)), Pair("b", ParamType2("Hello Jack")))
        factory.createKotlin(KotlinFlowLogic::class.java, args)
    }

    @Test
    fun testCreatePrimary() {
        factory.create(KotlinFlowLogic::class.java, ParamType1(1), ParamType2("Hello Jack"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun testCreateNotWhiteListed() {
        factory.create(NotWhiteListedKotlinFlowLogic::class.java, ParamType1(1), ParamType2("Hello Jack"))
    }

    @Test
    fun testCreateKotlinVoid() {
        factory.createKotlin(KotlinFlowLogic::class.java, emptyMap())
    }

    @Test
    fun testCreateKotlinNonPrimary() {
        val args = mapOf(Pair("C", ParamType2("Hello Jack")))
        factory.createKotlin(KotlinFlowLogic::class.java, args)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testCreateArgNotWhiteListed() {
        val args = mapOf(Pair("illegal", 1.days))
        factory.createKotlin(KotlinFlowLogic::class.java, args)
    }

    @Test
    fun testCreateJavaPrimitiveNoRegistrationRequired() {
        val args = mapOf(Pair("primitive", "A string"))
        factory.createKotlin(KotlinFlowLogic::class.java, args)
    }

    @Test
    fun testCreateKotlinPrimitiveNoRegistrationRequired() {
        val args = mapOf(Pair("kotlinType", 3))
        factory.createKotlin(KotlinFlowLogic::class.java, args)
    }
}
