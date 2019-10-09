package net.corda.node.services.statemachine

import com.google.common.primitives.Primitives
import net.corda.core.flows.*
import net.corda.core.internal.VisibleForTesting
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.debug
import net.corda.core.utilities.trace
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.TypeVariable
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
open class FlowLogicRefFactoryImpl(private val classloader: ClassLoader) : SingletonSerializeAsToken(), FlowLogicRefFactory {
    private val log: Logger = LoggerFactory.getLogger("net.corda.flow")

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
            findConstructor(flowClass, argTypes)
        } catch (e: IllegalArgumentException) {
            throw IllegalFlowLogicException(flowClass, "due to ambiguous match against the constructors: $argTypes")
        } catch (e: NoSuchElementException) {
            throw IllegalFlowLogicException(flowClass, "due to missing constructor for arguments: $argTypes")
        }

        // Build map of args from array
        val argsMap = args.zip(constructor.parameters).map { Pair(it.second.name!!, it.first) }.toMap()
        return createKotlin(flowClass, argsMap)
    }

    protected open fun findConstructorCheckDefaultParams(flowClass: Class<out FlowLogic<*>>, argTypes: List<Class<Any>?>):
            KFunction<FlowLogic<*>> {
        log.debug { "${javaClass.name}.findConstructorCheckDefaultParams() called for RPC parameter argument types:" }
        for (argType in argTypes) {
            log.debug { "  ARG-TYPE = '${argType}'" }
        }

        // There may be multiple matches. If there are, we will use the one with the least number of default parameter matches.
        log.trace { "Checking for matching constructors (${flowClass.kotlin.constructors.size} possible)..." }
        var conInd = -1
        var ctorMatch: KFunction<FlowLogic<*>>? = null
        var matchNumDefArgs = 0
        var matchCount = 0
        var matchIndex = 0
        for (ctor in flowClass.kotlin.constructors) {
            conInd++
            log.trace { "[${conInd}]: Checking constructor '${ctor}'" }
            val optional = ArrayList<Boolean>()
            for (param in ctor.parameters) {
                optional.add(param.isOptional)
                log.trace { "  Constructor-Param [${param.index}]: pname='${param.name}', ptype='${param.type}', opt=${param.isOptional}" }
            }

            // Get the types of the arguments, always boxed (as that's what we get in the invocation).
            val ctorTypes = ctor.javaConstructor!!.parameterTypes.map {
                if (it == null) { it } else { Primitives.wrap(it) }
            }

            // There must be at least as many constructor arguments as supplied arguments
            if (argTypes.size > ctorTypes.size) {
                log.trace { "  Failed to match constructor [${conInd}]: Not enough constructor arguments for supplied parameter list." }
                continue
            }

            // Check if all constructor arguments are assignable for all supplied arguments, then for remaining arguments in constructor
            // check that they are optional. If they are it's still a match.
            var matched = true
            var numDefaultsUsed = 0
            var index = 0
            for (conArg in ctorTypes) {
                if (index < argTypes.size) {
                    val argType = argTypes[index]
                    if (argType != null) {
                        log.trace { "  Comparing '$conArg' with '$argType': Assignable -> ${conArg.isAssignableFrom(argType)}" }
                        if (!conArg.isAssignableFrom(argType)) {
                            matched = false
                            log.trace { "  Failed to match constructor argument: Parameter '${conArg}' not assignment compatible." }
                            break // for conArg
                        }
                    }
                } else {
                    val opt = if (index < optional.size) { optional[index] } else { false }
                    log.trace { "  Checking if arg [${index}] '$conArg' is optional: -> ${opt}" }
                    if (!opt) {
                        matched = false
                        log.trace { "  Failed to match constructor argument: Non-optional parameter '${conArg}' is not supplied by client."}
                        break // for conArg
                    } else {
                        numDefaultsUsed++
                    }
                }
                index++
            }

            if (matched) {
                matchCount++
                if (ctorMatch != null) {
                    if (numDefaultsUsed < matchNumDefArgs) {
                        log.trace { ("Choosing better constructor [inx=${conInd}]: '${ctor}' (num_default_args_used=${numDefaultsUsed}) "
                                + "over [inx=${matchIndex}]: '${ctorMatch}' (num_default_args_used=${matchNumDefArgs})") }
                        ctorMatch = ctor // Replace matched constructor with this one (having less default arguments)
                        matchNumDefArgs = numDefaultsUsed
                        matchIndex = conInd
                    } else {
                        log.trace { ("Sticking with constructor [inx=${matchIndex}] '${ctorMatch}' (num_default_args_used=${matchNumDefArgs}) "
                                + "over [inx=${conInd}]: '${ctor}' (num_default_args_used=${numDefaultsUsed})") }
                    }
                } else {
                    log.trace { "Selecting first matching constructor [inx=${conInd}]: '${ctor}' (num_default_args_used=${numDefaultsUsed})"}
                    ctorMatch = ctor
                    matchNumDefArgs = numDefaultsUsed
                    matchIndex = conInd
                }
            }
        }

        if (ctorMatch == null) {
            var argSummary = "" // Create a meaningful error message for the client
            for (argt in argTypes) {
                argSummary += if (argSummary.length > 0) { ", " } else { "" }
                argSummary += argt.toString()
            }
            // Create message likely to be written to the log (info) with some details.
            log.error("Cannot find Constructor to match arguments: ${argSummary}")
            log.info("Candidate constructors are:")
            var candInd = 0
            for (ctor in flowClass.kotlin.constructors) {
                log.info("[${candInd}]: '${ctor}'")
                candInd++
            }
            throw Exception("No constructor found that matches arguments (${argSummary}), see log for more information.")
        }

        log.info("Matched constructor: ${ctorMatch} (num_default_args_used=$matchNumDefArgs, match_count=${matchCount})")
        return ctorMatch
    }

    protected open fun findConstructorDirectMatch(flowClass: Class<out FlowLogic<*>>, argTypes: List<Class<Any>?>): KFunction<FlowLogic<*>> {
        return flowClass.kotlin.constructors.single { ctor ->
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
    }

    protected open fun findConstructor(flowClass: Class<out FlowLogic<*>>, argTypes: List<Class<Any>?>): KFunction<FlowLogic<*>> {
        try {
            val ret = findConstructorDirectMatch(flowClass, argTypes)
            log.debug { "Found constructor by direct match: '${ret}'" }
            return ret
        } catch (e: Exception) {
            log.debug("Failed in findConstructorDirectMatch: ${e.message}")
        }

        return findConstructorCheckDefaultParams(flowClass, argTypes)
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
        } else if (type is TypeVariable<*>) {
            type.bounds.all { parameterAssignableFrom(it, value) }
        } else {
            false
        }
    }
}
