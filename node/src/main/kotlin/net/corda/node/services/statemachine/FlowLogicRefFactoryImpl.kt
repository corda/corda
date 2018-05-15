/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node.services.statemachine

import net.corda.core.internal.VisibleForTesting
import com.google.common.primitives.Primitives
import net.corda.core.flows.*
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SingletonSerializeAsToken
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util.*
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.jvm.javaConstructor
import kotlin.reflect.jvm.javaType

/**
 * The internal concrete implementation of the FlowLogicRef marker interface.
 */
@CordaSerializable
data class FlowLogicRefImpl internal constructor(val flowLogicClassName: String, val args: Map<String, Any?>) : FlowLogicRef

/**
 * A class for conversion to and from [FlowLogic] and [FlowLogicRef] instances.
 *
 * Validation of types is performed on the way in and way out in case this object is passed between JVMs which might have differing
 * whitelists.
 *
 * TODO: Align with API related logic for passing in FlowLogic references (FlowRef)
 * TODO: Actual support for AppContext / AttachmentsClassLoader
 * TODO: at some point check whether there is permission, beyond the annotations, to start flows. For example, as a security
 * measure we might want the ability for the node admin to blacklist a flow such that it moves immediately to the "Flow Hospital"
 * in response to a potential malicious use or buggy update to an app etc.
 */
// TODO: Replace with a per app classloader/cordapp provider/cordapp loader - this will do for now
class FlowLogicRefFactoryImpl(private val classloader: ClassLoader) : SingletonSerializeAsToken(), FlowLogicRefFactory {
    override fun create(flowClass: Class<out FlowLogic<*>>, vararg args: Any?): FlowLogicRef {
        if (!flowClass.isAnnotationPresent(SchedulableFlow::class.java)) {
            throw IllegalFlowLogicException(flowClass, "because it's not a schedulable flow")
        }
        return createForRPC(flowClass, *args)
    }

    override fun create(flowClassName: String, vararg args: Any?): FlowLogicRef {
        val flowClass = validatedFlowClassFromName(flowClassName)
        if (!flowClass.isAnnotationPresent(SchedulableFlow::class.java)) {
            throw IllegalFlowLogicException(flowClass, "because it's not a schedulable flow")
        }
        return createForRPC(flowClass, *args)
    }

    private fun validatedFlowClassFromName(flowClassName: String): Class<out FlowLogic<*>> {
        val forName = try {
            Class.forName(flowClassName, true, classloader)
        } catch (e: ClassNotFoundException) {
            throw IllegalFlowLogicException(flowClassName, "Flow not found: $flowClassName")
        }
        return forName.asSubclass(FlowLogic::class.java) ?:
            throw IllegalFlowLogicException(flowClassName, "The class $flowClassName is not a subclass of FlowLogic.")
    }

    override fun createForRPC(flowClass: Class<out FlowLogic<*>>, vararg args: Any?): FlowLogicRef {
        // TODO: This is used via RPC but it's probably better if we pass in argument names and values explicitly
        // to avoid requiring only a single constructor.
        val argTypes = args.map { it?.javaClass }
        val constructor = try {
            flowClass.kotlin.constructors.single { ctor ->
                // Get the types of the arguments, always boxed (as that's what we get in the invocation).
                val ctorTypes = ctor.javaConstructor!!.parameterTypes.map { Primitives.wrap(it) }
                if (argTypes.size != ctorTypes.size)
                    return@single false
                for ((argType, ctorType) in argTypes.zip(ctorTypes)) {
                    if (argType == null) continue   // Try and find a match based on the other arguments.
                    if (!ctorType.isAssignableFrom(argType)) return@single false
                }
                true
            }
        } catch (e: IllegalArgumentException) {
            throw IllegalFlowLogicException(flowClass, "due to ambiguous match against the constructors: $argTypes")
        } catch (e: NoSuchElementException) {
            throw IllegalFlowLogicException(flowClass, "due to missing constructor for arguments: $argTypes")
        }

        // Build map of args from array
        val argsMap = args.zip(constructor.parameters).map { Pair(it.second.name!!, it.first) }.toMap()
        return createKotlin(flowClass, argsMap)
    }

    /**
     * Create a [FlowLogicRef] by trying to find a Kotlin constructor that matches the given args.
     *
     * TODO: Rethink language specific naming.
     */
    @VisibleForTesting
    internal fun createKotlin(type: Class<out FlowLogic<*>>, args: Map<String, Any?>): FlowLogicRef {
        // Check we can find a constructor and populate the args to it, but don't call it
        createConstructor(type, args)
        return FlowLogicRefImpl(type.name, args)
    }

    override fun toFlowLogic(ref: FlowLogicRef): FlowLogic<*> {
        if (ref !is FlowLogicRefImpl) throw IllegalFlowLogicException(ref.javaClass, "FlowLogicRef was not created via correct FlowLogicRefFactory interface")
        // We re-validate here because a FlowLogicRefImpl could have arrived via deserialization and therefore the
        // class name could point to anything at all.
        val klass = validatedFlowClassFromName(ref.flowLogicClassName)
        return createConstructor(klass, ref.args)()
    }

    private fun createConstructor(clazz: Class<out FlowLogic<*>>, args: Map<String, Any?>): () -> FlowLogic<*> {
        for (constructor in clazz.kotlin.constructors) {
            val params = buildParams(constructor, args) ?: continue
            // If we get here then we matched every parameter
            return { constructor.callBy(params) }
        }
        throw IllegalFlowLogicException(clazz, "as could not find matching constructor for: $args")
    }

    private fun buildParams(constructor: KFunction<FlowLogic<*>>, args: Map<String, Any?>): HashMap<KParameter, Any?>? {
        val params = hashMapOf<KParameter, Any?>()
        val usedKeys = hashSetOf<String>()
        for (parameter in constructor.parameters) {
            if (!tryBuildParam(args, parameter, params)) {
                return null
            } else {
                usedKeys += parameter.name!!
            }
        }
        if ((args.keys - usedKeys).isNotEmpty()) {
            // Not all args were used
            return null
        }
        return params
    }

    private fun tryBuildParam(args: Map<String, Any?>, parameter: KParameter, params: HashMap<KParameter, Any?>): Boolean {
        val containsKey = parameter.name in args
        // OK to be missing if optional
        return (parameter.isOptional && !containsKey) || (containsKey && paramCanBeBuilt(args, parameter, params))
    }

    private fun paramCanBeBuilt(args: Map<String, Any?>, parameter: KParameter, params: HashMap<KParameter, Any?>): Boolean {
        val value = args[parameter.name]
        params[parameter] = value
        return (value is Any && parameterAssignableFrom(parameter.type.javaType, value)) || parameter.type.isMarkedNullable
    }

    private fun parameterAssignableFrom(type: Type, value: Any): Boolean {
        return if (type is Class<*>) {
            if (type.isPrimitive) {
                Primitives.unwrap(value.javaClass) == type
            } else {
                type.isAssignableFrom(value.javaClass)
            }
        } else if (type is ParameterizedType) {
            parameterAssignableFrom(type.rawType, value)
        } else {
            false
        }
    }
}
