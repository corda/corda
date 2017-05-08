package net.corda.node.services.statemachine

import com.google.common.primitives.Primitives
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.AppContext
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowLogicRef
import net.corda.core.flows.IllegalFlowLogicException
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.node.services.api.FlowLogicRefFactoryInternal
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
data class FlowLogicRefImpl internal constructor(val flowLogicClassName: String, val appContext: AppContext, val args: Map<String, Any?>) : FlowLogicRef

/**
 * A class for conversion to and from [FlowLogic] and [FlowLogicRef] instances.
 *
 * Validation of types is performed on the way in and way out in case this object is passed between JVMs which might have differing
 * whitelists.
 *
 * TODO: Ways to populate whitelist of "blessed" flows per node/party
 * TODO: Ways to populate argument types whitelist. Per node/party or global?
 * TODO: Align with API related logic for passing in FlowLogic references (FlowRef)
 * TODO: Actual support for AppContext / AttachmentsClassLoader
 */
class FlowLogicRefFactoryImpl(override val flowWhitelist: Map<String, Set<String>>) : SingletonSerializeAsToken(), FlowLogicRefFactoryInternal {
    constructor() : this(mapOf())

    // Pending real dependence on AppContext for class loading etc
    @Suppress("UNUSED_PARAMETER")
    private fun validateFlowClassName(className: String, appContext: AppContext) {
        // TODO: make this specific to the attachments in the [AppContext] by including [SecureHash] in whitelist check
        require(flowWhitelist.containsKey(className)) { "${FlowLogic::class.java.simpleName} of ${FlowLogicRef::class.java.simpleName} must have type on the whitelist: $className" }
    }

    // Pending real dependence on AppContext for class loading etc
    @Suppress("UNUSED_PARAMETER")
    private fun validateArgClassName(className: String, argClassName: String, appContext: AppContext) {
        // TODO: consider more carefully what to whitelist and how to secure flows
        // For now automatically accept standard java.lang.* and kotlin.* types.
        // All other types require manual specification at FlowLogicRefFactory construction time.
        if (argClassName.startsWith("java.lang.") || argClassName.startsWith("kotlin.")) {
            return
        }
        // TODO: make this specific to the attachments in the [AppContext] by including [SecureHash] in whitelist check
        require(flowWhitelist[className]!!.contains(argClassName)) { "Args to $className must have types on the args whitelist: $argClassName, but it has ${flowWhitelist[className]}" }
    }

    /**
     * Create a [FlowLogicRef] for the Kotlin primary constructor of a named [FlowLogic]
     */
    fun createKotlin(flowLogicClassName: String, args: Map<String, Any?>, attachments: List<SecureHash> = emptyList()): FlowLogicRef {
        val context = AppContext(attachments)
        validateFlowClassName(flowLogicClassName, context)
        for (arg in args.values.filterNotNull()) {
            validateArgClassName(flowLogicClassName, arg.javaClass.name, context)
        }
        val clazz = Class.forName(flowLogicClassName)
        require(FlowLogic::class.java.isAssignableFrom(clazz)) { "$flowLogicClassName is not a FlowLogic" }
        @Suppress("UNCHECKED_CAST")
        val logic = clazz as Class<FlowLogic<FlowLogic<*>>>
        return createKotlin(logic, args)
    }

    /**
     * Create a [FlowLogicRef] by assuming a single constructor and the given args.
     */
    override fun create(type: Class<out FlowLogic<*>>, vararg args: Any?): FlowLogicRef {
        // TODO: This is used via RPC but it's probably better if we pass in argument names and values explicitly
        // to avoid requiring only a single constructor.
        val argTypes = args.map { it?.javaClass }
        val constructor = try {
            type.kotlin.constructors.single { ctor ->
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
            throw IllegalFlowLogicException(type, "due to ambiguous match against the constructors: $argTypes")
        } catch (e: NoSuchElementException) {
            throw IllegalFlowLogicException(type, "due to missing constructor for arguments: $argTypes")
        }

        // Build map of args from array
        val argsMap = args.zip(constructor.parameters).map { Pair(it.second.name!!, it.first) }.toMap()
        return createKotlin(type, argsMap)
    }

    /**
     * Create a [FlowLogicRef] by trying to find a Kotlin constructor that matches the given args.
     *
     * TODO: Rethink language specific naming.
     */
    fun createKotlin(type: Class<out FlowLogic<*>>, args: Map<String, Any?>): FlowLogicRef {
        // TODO: we need to capture something about the class loader or "application context" into the ref,
        //       perhaps as some sort of ThreadLocal style object.  For now, just create an empty one.
        val appContext = AppContext(emptyList())
        validateFlowClassName(type.name, appContext)
        // Check we can find a constructor and populate the args to it, but don't call it
        createConstructor(appContext, type, args)
        return FlowLogicRefImpl(type.name, appContext, args)
    }

    /**
     * Create a [FlowLogicRef] by trying to find a Java constructor that matches the given args.
     */
    private fun createJava(type: Class<out FlowLogic<*>>, vararg args: Any?): FlowLogicRef {
        // Build map for each
        val argsMap = HashMap<String, Any?>(args.size)
        var index = 0
        args.forEach { argsMap["arg${index++}"] = it }
        return createKotlin(type, argsMap)
    }

    override fun toFlowLogic(ref: FlowLogicRef): FlowLogic<*> {
        if (ref !is FlowLogicRefImpl) throw IllegalFlowLogicException(ref.javaClass, "FlowLogicRef was not created via correct FlowLogicRefFactory interface")
        validateFlowClassName(ref.flowLogicClassName, ref.appContext)
        val klass = Class.forName(ref.flowLogicClassName, true, ref.appContext.classLoader).asSubclass(FlowLogic::class.java)
        return createConstructor(ref.appContext, klass, ref.args)()
    }

    private fun createConstructor(appContext: AppContext, clazz: Class<out FlowLogic<*>>, args: Map<String, Any?>): () -> FlowLogic<*> {
        for (constructor in clazz.kotlin.constructors) {
            val params = buildParams(appContext, clazz, constructor, args) ?: continue
            // If we get here then we matched every parameter
            return { constructor.callBy(params) }
        }
        throw IllegalFlowLogicException(clazz, "as could not find matching constructor for: $args")
    }

    private fun buildParams(appContext: AppContext, clazz: Class<out FlowLogic<*>>, constructor: KFunction<FlowLogic<*>>, args: Map<String, Any?>): HashMap<KParameter, Any?>? {
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
        params.values.forEach { if (it is Any) validateArgClassName(clazz.name, it.javaClass.name, appContext) }
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
        if (type is Class<*>) {
            if (type.isPrimitive) {
                return Primitives.unwrap(value.javaClass) == type
            } else {
                return type.isAssignableFrom(value.javaClass)
            }
        } else if (type is ParameterizedType) {
            return parameterAssignableFrom(type.rawType, value)
        } else {
            return false
        }
    }
}