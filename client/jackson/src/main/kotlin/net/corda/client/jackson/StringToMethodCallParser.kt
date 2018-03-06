/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.client.jackson

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.google.common.collect.HashMultimap
import com.google.common.collect.Multimap
import net.corda.client.jackson.StringToMethodCallParser.ParsedMethodCall
import net.corda.core.CordaException
import net.corda.core.utilities.contextLogger
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.util.concurrent.Callable
import javax.annotation.concurrent.ThreadSafe
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.jvm.internal.KotlinReflectionInternalError
import kotlin.reflect.jvm.kotlinFunction

/**
 * This class parses strings in a format designed for human usability into [ParsedMethodCall] objects representing a
 * ready-to-invoke call on the given target object. The strings accepted by this class are a minor variant of
 * [Yaml](http://www.yaml.org/spec/1.2/spec.html) and can be easily typed at a command line. Intended use cases include
 * things like the Corda shell, text-based RPC dispatch, simple scripting and so on.
 *
 * # Syntax
 *
 * The format of the string is as follows. The first word is the name of the method and must always be present. The rest,
 * which is optional, is wrapped in curly braces and parsed as if it were a Yaml object. The keys of this object are then
 * mapped to the parameters of the method via the usual Jackson mechanisms. The standard [java.lang.Object] methods are
 * excluded.
 *
 * One convenient feature of Yaml is that barewords collapse into strings, thus you can write a call like the following:
 *
 *     fun someCall(note: String, option: Boolean)
 *
 *     someCall note: This is a really helpful feature, option: true
 *
 * ... and it will be parsed in the intuitive way. Quotes are only needed if you want to put a comma into the string.
 *
 * There is an [online Yaml parser](http://yaml-online-parser.appspot.com/) which can be used to explore
 * the allowed syntax.
 *
 * # Usage
 *
 * This class is thread safe. Multiple strings may be parsed in parallel, and the resulting [ParsedMethodCall]
 * objects may be reused multiple times and also invoked in parallel, as long as the underling target object is
 * thread safe itself.
 *
 * You may pass in an alternative [ObjectMapper] to control what types can be parsed, but it must be configured
 * with the [YAMLFactory] for the class to work.
 *
 * # Limitations
 *
 * - The target class must be either a Kotlin class, or a Java class compiled with the -parameters command line
 *   switch, as the class relies on knowing the names of parameters which isn't data provided by default by the
 *   Java compiler.
 * - Vararg methods are not supported, as the type information that'd be required is missing.
 * - Method overloads that have identical parameter names but different types can't be handled, because often
 *   a string could map to multiple types, so which one to use is ambiguous. If you want your interface to be
 *   usable with this utility make sure the parameter and method names don't rely on type overloading.
 *
 * # Examples
 *
 *     fun simple() = ...
 *     "simple"   -> runs the no-args function 'simple'
 *
 *     fun attachmentExists(id: SecureHash): Boolean
 *     "attachmentExists id: b6d7e826e87"  -> parses the given ID as a SecureHash
 *
 *     fun addNote(id: SecureHash, note: String)
 *     "addNote id: b6d7e826e8739ab2eb6e077fc4fba9b04fb880bb4cbd09bc618d30234a8827a4, note: Some note"
 */
@ThreadSafe
open class StringToMethodCallParser<in T : Any> @JvmOverloads constructor(
        targetType: Class<out T>,
        private val om: ObjectMapper = JacksonSupport.createNonRpcMapper(YAMLFactory())) {
    /** Same as the regular constructor but takes a Kotlin reflection [KClass] instead of a Java [Class]. */
    constructor(targetType: KClass<out T>) : this(targetType.java)

    companion object {
        @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
        private val ignoredNames = Object::class.java.methods.map { it.name }

        private fun methodsFromType(clazz: Class<*>): Multimap<String, Method> {
            val result = HashMultimap.create<String, Method>()
            for ((key, value) in clazz.methods.filterNot { it.isSynthetic && it.name !in ignoredNames }.map { it.name to it }) {
                result.put(key, value)
            }
            return result
        }

        private val log = contextLogger()
    }

    /** The methods that can be invoked via this parser. */
    protected val methodMap: Multimap<String, Method> = methodsFromType(targetType)

    /** A map of method name to parameter names for the target type. */
    val methodParamNames: Map<String, List<String>> = targetType.declaredMethods.mapNotNull {
        try {
            it.name to paramNamesFromMethod(it)
        } catch (e: KotlinReflectionInternalError) {
            // Kotlin reflection doesn't support every method that can exist on an object (in particular, reified
            // inline methods) so we just ignore those here.
            null
        }
    }.toMap()

    inner class ParsedMethodCall(private val target: T?, val method: Method, val args: Array<Any?>) : Callable<Any?> {
        operator fun invoke(): Any? = call()
        override fun call(): Any? {
            if (target == null)
                throw IllegalStateException("No target object was specified")
            if (log.isDebugEnabled)
                log.debug("Invoking call ${method.name}($args)")
            return method.invoke(target, *args)
        }
    }

    /**
     * Uses either Kotlin or Java 8 reflection to learn the names of the parameters to a method.
     */
    open fun paramNamesFromMethod(method: Method): List<String> {
        val kf: KFunction<*>? = method.kotlinFunction
        return method.parameters.mapIndexed { index, param ->
            when {
                param.isNamePresent -> param.name
            // index + 1 because the first Kotlin reflection param is 'this', but that doesn't match Java reflection.
                kf != null -> kf.parameters[index + 1].name ?: throw UnparseableCallException.ReflectionDataMissing(method.name, index)
                else -> throw UnparseableCallException.ReflectionDataMissing(method.name, index)
            }
        }
    }

    /**
     * Uses either Kotlin or Java 8 reflection to learn the names of the parameters to a constructor.
     */
    open fun paramNamesFromConstructor(ctor: Constructor<*>): List<String> {
        val kf: KFunction<*>? = ctor.kotlinFunction
        return ctor.parameters.mapIndexed { index, param ->
            when {
                param.isNamePresent -> param.name
                kf != null -> kf.parameters[index].name ?: throw UnparseableCallException.ReflectionDataMissing("<init>", index)
                else -> throw UnparseableCallException.ReflectionDataMissing("<init>", index)
            }
        }
    }

    open class UnparseableCallException(command: String, cause: Throwable? = null) : CordaException("Could not parse as a command: $command", cause) {
        class UnknownMethod(val methodName: String) : UnparseableCallException("Unknown command name: $methodName")
        class MissingParameter(methodName: String, val paramName: String, command: String) : UnparseableCallException("Parameter $paramName missing from attempt to invoke $methodName in command: $command")
        class TooManyParameters(methodName: String, command: String) : UnparseableCallException("Too many parameters provided for $methodName: $command")
        class ReflectionDataMissing(methodName: String, argIndex: Int) : UnparseableCallException("Method $methodName missing parameter name at index $argIndex")
        class FailedParse(e: Exception) : UnparseableCallException(e.message ?: e.toString(), e)
    }

    /**
     * Parses the given command as a call on the target type. The target should be specified, if it's null then
     * the resulting [ParsedMethodCall] can't be invoked, just inspected.
     */
    @Throws(UnparseableCallException::class)
    fun parse(target: T?, command: String): ParsedMethodCall {
        log.debug("Parsing call command from string: {}", command)
        val spaceIndex = command.indexOf(' ')
        val name = if (spaceIndex != -1) command.substring(0, spaceIndex) else command
        val argStr = if (spaceIndex != -1) command.substring(spaceIndex) else ""
        val methods: Collection<Method> = methodMap[name]
        if (methods.isEmpty())
            throw UnparseableCallException.UnknownMethod(name)
        log.debug("Parsing call for method {}", name)
        // Attempt to parse for each method in turn, allowing the exception to leak if we're on the last one
        // and fail for that too.
        for ((index, method) in methods.withIndex()) {
            try {
                val args = parseArguments(name, paramNamesFromMethod(method).zip(method.parameterTypes), argStr)
                return ParsedMethodCall(target, method, args)
            } catch (e: UnparseableCallException) {
                if (index == methods.size - 1)
                    throw e
            }
        }
        throw UnparseableCallException("No overloads of the method matched")  // Should be unreachable!
    }

    /**
     * Parses only the arguments string given the info about parameter names and types.
     *
     * @param methodNameHint A name that will be used in exceptions if thrown; not used for any other purpose.
     */
    @Throws(UnparseableCallException::class)
    fun parseArguments(methodNameHint: String, parameters: List<Pair<String, Class<*>>>, args: String): Array<Any?> {
        // If we have parameters, wrap them in {} to allow the Yaml parser to eat them on a single line.
        val parameterString = "{ $args }"
        val tree: JsonNode = om.readTree(parameterString) ?: throw UnparseableCallException(args)
        if (tree.size() > parameters.size) throw UnparseableCallException.TooManyParameters(methodNameHint, args)
        val inOrderParams: List<Any?> = parameters.mapIndexed { _, (argName, argType) ->
            val entry = tree[argName] ?: throw UnparseableCallException.MissingParameter(methodNameHint, argName, args)
            try {
                om.readValue(entry.traverse(om), argType)
            } catch (e: Exception) {
                throw UnparseableCallException.FailedParse(e)
            }
        }
        if (log.isDebugEnabled) {
            inOrderParams.forEachIndexed { i, param ->
                val tmp = if (param != null) "${param.javaClass.name} -> $param" else "(null)"
                log.debug("Parameter $i. $tmp")
            }
        }
        return inOrderParams.toTypedArray()
    }

    /** Returns a string-to-string map of commands to a string describing available parameter types. */
    val availableCommands: Map<String, String>
        get() {
            return methodMap.entries().map { entry ->
                val (name, args) = entry   // TODO: Kotlin 1.1
                val argStr = if (args.parameterCount == 0) "" else {
                    val paramNames = methodParamNames[name]!!
                    val typeNames = args.parameters.map { it.type.simpleName }
                    val paramTypes = paramNames.zip(typeNames)
                    paramTypes.map { "${it.first}: ${it.second}" }.joinToString(", ")
                }
                Pair(name, argStr)
            }.toMap()
        }
}
