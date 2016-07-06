package com.r3corda.core.protocols

import com.google.common.primitives.Primitives
import com.r3corda.core.contracts.StateRef
import com.r3corda.core.crypto.SecureHash
import com.r3corda.core.serialization.SingletonSerializeAsToken
import com.r3corda.protocols.TwoPartyDealProtocol
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.time.Duration
import java.util.*
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.jvm.javaType
import kotlin.reflect.primaryConstructor

/**
 * A class for conversion to and from [ProtocolLogic] and [ProtocolLogicRef] instances
 *
 * Validation of types is performed on the way in and way out in case this object is passed between JVMs which might have differing
 * whitelists.
 *
 * TODO: Ways to populate whitelist of "blessed" protocols per node/party
 * TODO: Ways to populate argument types whitelist. Per node/party or global?
 * TODO: Align with API related logic for passing in ProtocolLogic references (ProtocolRef)
 * TODO: Actual support for AppContext / AttachmentsClassLoader
 */
class ProtocolLogicRefFactory(private val protocolWhitelist: Map<String, Set<String>>) : SingletonSerializeAsToken() {

    constructor() : this(mapOf(Pair(TwoPartyDealProtocol.FixingRoleDecider::class.java.name, setOf(StateRef::class.java.name, Duration::class.java.name))))

    // Pending real dependence on AppContext for class loading etc
    @Suppress("UNUSED_PARAMETER")
    private fun validateProtocolClassName(className: String, appContext: AppContext) {
        // TODO: make this specific to the attachments in the [AppContext] by including [SecureHash] in whitelist check
        require(protocolWhitelist.containsKey(className)) { "${ProtocolLogic::class.java.simpleName} of ${ProtocolLogicRef::class.java.simpleName} must have type on the whitelist: $className" }
    }

    // Pending real dependence on AppContext for class loading etc
    @Suppress("UNUSED_PARAMETER")
    private fun validateArgClassName(className: String, argClassName: String, appContext: AppContext) {
        // Accept standard java.lang.* and kotlin.* types
        if (argClassName.startsWith("java.lang.") || argClassName.startsWith("kotlin.")) {
            return
        }
        // TODO: make this specific to the attachments in the [AppContext] by including [SecureHash] in whitelist check
        require(protocolWhitelist[className]!!.contains(argClassName)) { "Args to ${className} must have types on the args whitelist: $argClassName" }
    }

    /**
     * Create a [ProtocolLogicRef] for the Kotlin primary constructor or Java constructor and the given args.
     */
    fun create(type: Class<out ProtocolLogic<*>>, vararg args: Any?): ProtocolLogicRef {
        val constructor = type.kotlin.primaryConstructor ?: return createJava(type, *args)
        if (constructor.parameters.size < args.size) {
            throw IllegalProtocolLogicException(type, "due to too many arguments supplied to kotlin primary constructor")
        }
        // Build map of args from array
        val argsMap = args.zip(constructor.parameters).map { Pair(it.second.name!!, it.first) }.toMap()
        return createKotlin(type, argsMap)
    }

    /**
     * Create a [ProtocolLogicRef] by trying to find a Kotlin constructor that matches the given args.
     *
     * TODO: Rethink language specific naming.
     */
    fun createKotlin(type: Class<out ProtocolLogic<*>>, args: Map<String, Any?>): ProtocolLogicRef {
        // TODO: we need to capture something about the class loader or "application context" into the ref,
        //       perhaps as some sort of ThreadLocal style object.  For now, just create an empty one.
        val appContext = AppContext(emptyList())
        validateProtocolClassName(type.name, appContext)
        // Check we can find a constructor and populate the args to it, but don't call it
        createConstructor(appContext, type, args)
        return ProtocolLogicRef(type.name, appContext, args)
    }

    /**
     * Create a [ProtocolLogicRef] by trying to find a Java constructor that matches the given args.
     */
    private fun createJava(type: Class<out ProtocolLogic<*>>, vararg args: Any?): ProtocolLogicRef {
        // Build map for each
        val argsMap = HashMap<String, Any?>(args.size)
        var index = 0
        args.forEach { argsMap["arg${index++}"] = it }
        return createKotlin(type, argsMap)
    }

    fun toProtocolLogic(ref: ProtocolLogicRef): ProtocolLogic<*> {
        validateProtocolClassName(ref.protocolLogicClassName, ref.appContext)
        val klass = Class.forName(ref.protocolLogicClassName, true, ref.appContext.classLoader).asSubclass(ProtocolLogic::class.java)
        return createConstructor(ref.appContext, klass, ref.args)()
    }

    private fun createConstructor(appContext: AppContext, clazz: Class<out ProtocolLogic<*>>, args: Map<String, Any?>): () -> ProtocolLogic<*> {
        for (constructor in clazz.kotlin.constructors) {
            val params = buildParams(appContext, clazz, constructor, args) ?: continue
            // If we get here then we matched every parameter
            return { constructor.callBy(params) }
        }
        throw IllegalProtocolLogicException(clazz, "as could not find matching constructor for: $args")
    }

    private fun buildParams(appContext: AppContext, clazz: Class<out ProtocolLogic<*>>, constructor: KFunction<ProtocolLogic<*>>, args: Map<String, Any?>): HashMap<KParameter, Any?>? {
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

class IllegalProtocolLogicException(type: Class<*>, msg: String) : IllegalArgumentException("${ProtocolLogicRef::class.java.simpleName} cannot be constructed for ${ProtocolLogic::class.java.simpleName} of type ${type.name} $msg")

/**
 * A class representing a [ProtocolLogic] instance which would be possible to safely pass out of the contract sandbox
 *
 * Only allows a String reference to the ProtocolLogic class, and only allows restricted argument types as per [ProtocolLogicRefFactory]
 */
// TODO: align this with the existing [ProtocolRef] in the bank-side API (probably replace some of the API classes)
data class ProtocolLogicRef internal constructor(val protocolLogicClassName: String, val appContext: AppContext, val args: Map<String, Any?>)

/**
 * This is just some way to track what attachments need to be in the class loader, but may later include some app
 * properties loaded from the attachments.  And perhaps the authenticated user for an API call?
 */
data class AppContext(val attachments: List<SecureHash>) {
    // TODO: build a real [AttachmentsClassLoader] etc
    val classLoader: ClassLoader
        get() = this.javaClass.classLoader
}