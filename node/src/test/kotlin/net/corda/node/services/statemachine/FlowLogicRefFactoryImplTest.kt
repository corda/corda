package net.corda.node.services.statemachine

import net.corda.core.flows.FlowLogic
import net.corda.core.flows.IllegalFlowLogicException
import net.corda.core.flows.SchedulableFlow
import org.junit.Test
import java.time.Duration
import kotlin.reflect.jvm.jvmName

class FlowLogicRefFactoryImplTest {

    data class ParamType1(val value: Int)
    data class ParamType2(val value: String)

    @Suppress("UNUSED_PARAMETER", "unused") // Things are used via reflection.
    @SchedulableFlow
    class KotlinFlowLogic(A: ParamType1, b: ParamType2) : FlowLogic<Unit>() {
        constructor() : this(ParamType1(1), ParamType2("2"))

        constructor(C: ParamType2) : this(ParamType1(1), C)

        constructor(illegal: Duration) : this(ParamType1(1), ParamType2(illegal.toString()))

        constructor(primitive: String) : this(ParamType1(1), ParamType2(primitive))

        constructor(kotlinType: Int) : this(ParamType1(kotlinType), ParamType2("b"))

        override fun call() = Unit
    }

    @SchedulableFlow
    class KotlinNoArgFlowLogic : FlowLogic<Unit>() {
        override fun call() = Unit
    }

    class NonSchedulableFlow : FlowLogic<Unit>() {
        override fun call() = Unit
    }

    private val flowLogicRefFactory = FlowLogicRefFactoryImpl(FlowLogicRefFactoryImpl::class.java.classLoader)
    @Test
    fun `create kotlin no arg`() {
        flowLogicRefFactory.create(KotlinNoArgFlowLogic::class.jvmName)
    }

    @Test
    fun `should create kotlin types`() {
        val args = mapOf(Pair("A", ParamType1(1)), Pair("b", ParamType2("Hello Jack")))
        flowLogicRefFactory.createKotlin(KotlinFlowLogic::class.java, args)
    }

    @Test
    fun `create primary`() {
        flowLogicRefFactory.create(KotlinFlowLogic::class.jvmName, ParamType1(1), ParamType2("Hello Jack"))
    }

    @Test
    fun `create kotlin void`() {
        flowLogicRefFactory.createKotlin(KotlinFlowLogic::class.java, emptyMap())
    }

    @Test
    fun `create kotlin non primary`() {
        val args = mapOf(Pair("C", ParamType2("Hello Jack")))
        flowLogicRefFactory.createKotlin(KotlinFlowLogic::class.java, args)
    }

    @Test
    fun `create java primitive no registration required`() {
        val args = mapOf(Pair("primitive", "A string"))
        flowLogicRefFactory.createKotlin(KotlinFlowLogic::class.java, args)
    }

    @Test
    fun `create kotlin primitive no registration required`() {
        val args = mapOf(Pair("kotlinType", 3))
        flowLogicRefFactory.createKotlin(KotlinFlowLogic::class.java, args)
    }

    @Test(expected = IllegalFlowLogicException::class)
    fun `create for non-schedulable flow logic`() {
        flowLogicRefFactory.create(NonSchedulableFlow::class.jvmName)
    }
}
