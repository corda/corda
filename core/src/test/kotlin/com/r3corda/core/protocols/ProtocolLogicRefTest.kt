package com.r3corda.core.protocols

import com.google.common.collect.Sets
import com.r3corda.core.days
import org.junit.Before
import org.junit.Test
import java.time.Duration

class ProtocolLogicRefTest {

    @Suppress("UNUSED_PARAMETER") // We will never use A or b
    class KotlinProtocolLogic(A: Int, b: String) : ProtocolLogic<Unit>() {
        constructor() : this(1, "2")

        constructor(C: String) : this(1, C)

        constructor(illegal: Duration) : this(1, illegal.toString())

        override fun call(): Unit {
        }
    }

    class KotlinNoArgProtocolLogic : ProtocolLogic<Unit>() {
        override fun call(): Unit {
        }
    }

    @Suppress("UNUSED_PARAMETER") // We will never use A or b
    class NotWhiteListedKotlinProtocolLogic(A: Int, b: String) : ProtocolLogic<Unit>() {
        override fun call(): Unit {
        }
    }

    lateinit var factory: ProtocolLogicRefFactory

    @Before
    fun setup() {
        // We have to allow Java boxed primitives but Kotlin warns we shouldn't be using them
        factory = ProtocolLogicRefFactory(Sets.newHashSet(KotlinProtocolLogic::class.java.name, KotlinNoArgProtocolLogic::class.java.name),
                Sets.newHashSet(@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN") Integer::class.java.name, String::class.java.name))
    }

    @Test
    fun testCreateKotlinNoArg() {
        factory.create(KotlinNoArgProtocolLogic::class.java)
    }

    @Test
    fun testCreateKotlin() {
        val args = mapOf(Pair("A", 1), Pair("b", "Hello Jack"))
        factory.createKotlin(KotlinProtocolLogic::class.java, args)
    }

    @Test
    fun testCreatePrimary() {
        factory.create(KotlinProtocolLogic::class.java, 1, "Hello Jack")
    }

    @Test(expected = IllegalArgumentException::class)
    fun testCreateNotWhiteListed() {
        factory.create(NotWhiteListedKotlinProtocolLogic::class.java, 1, "Hello Jack")
    }

    @Test
    fun testCreateKotlinVoid() {
        factory.createKotlin(KotlinProtocolLogic::class.java, emptyMap())
    }

    @Test
    fun testCreateKotlinNonPrimary() {
        val args = mapOf(Pair("C", "Hello Jack"))
        factory.createKotlin(KotlinProtocolLogic::class.java, args)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testCreateArgNotWhiteListed() {
        val args = mapOf(Pair("illegal", 1.days))
        factory.createKotlin(KotlinProtocolLogic::class.java, args)
    }
}
