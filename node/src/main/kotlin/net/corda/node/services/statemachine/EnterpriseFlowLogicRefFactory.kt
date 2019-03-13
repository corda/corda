package net.corda.node.services.statemachine

import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.core.flows.FlowLogic
import net.corda.core.internal.NamedCacheFactory
import java.util.concurrent.ConcurrentMap
import kotlin.reflect.KFunction

typealias FlowLogicConstructor = KFunction<FlowLogic<*>>

class EnterpriseFlowLogicRefFactoryImpl(classloader: ClassLoader, cacheFactory: NamedCacheFactory) : FlowLogicRefFactoryImpl(classloader) {

    private data class ConstructorSignature(val flowLogicClass: Class<out FlowLogic<*>>, val parameterTypes: List<Class<Any>?>)

    private val constructorCache: ConcurrentMap<ConstructorSignature, FlowLogicConstructor> = cacheFactory.buildNamed<ConstructorSignature, FlowLogicConstructor>(Caffeine.newBuilder(), "FlowLogicRefFactoryImpl_constructorCache")
            .asMap()

    override fun findConstructor(flowClass: Class<out FlowLogic<*>>, argTypes: List<Class<Any>?>): FlowLogicConstructor {
        val cacheKey = ConstructorSignature(flowClass, argTypes)
        return constructorCache.get(cacheKey) ?: super.findConstructor(flowClass, argTypes).apply { constructorCache.put(cacheKey, this) }
    }
}