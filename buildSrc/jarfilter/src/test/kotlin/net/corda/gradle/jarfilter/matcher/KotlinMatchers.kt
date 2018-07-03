@file:JvmName("KotlinMatchers")
package net.corda.gradle.jarfilter.matcher

import org.hamcrest.Description
import org.hamcrest.DiagnosingMatcher
import org.hamcrest.Matcher
import org.hamcrest.core.IsEqual.equalTo
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.KProperty
import kotlin.reflect.full.valueParameters
import kotlin.reflect.jvm.jvmName

fun isFunction(name: Matcher<in String>, returnType: Matcher<in String>, vararg parameters: Matcher<in KParameter>): Matcher<KFunction<*>> {
    return KFunctionMatcher(name, returnType, *parameters)
}

fun isFunction(name: String, returnType: KClass<*>, vararg parameters: KClass<*>): Matcher<KFunction<*>> {
    return isFunction(equalTo(name), matches(returnType), *parameters.toMatchers())
}

fun isConstructor(returnType: Matcher<in String>, vararg parameters: Matcher<in KParameter>): Matcher<KFunction<*>> {
    return KFunctionMatcher(equalTo("<init>"), returnType, *parameters)
}

fun isConstructor(returnType: KClass<*>, vararg parameters: KClass<*>): Matcher<KFunction<*>> {
    return isConstructor(matches(returnType), *parameters.toMatchers())
}

fun isConstructor(returnType: String, vararg parameters: KClass<*>): Matcher<KFunction<*>> {
    return isConstructor(equalTo(returnType), *parameters.toMatchers())
}

fun hasParam(type: Matcher<in String>): Matcher<KParameter> = KParameterMatcher(type)

fun hasParam(type: KClass<*>): Matcher<KParameter> = hasParam(matches(type))

fun isProperty(name: String, type: KClass<*>): Matcher<KProperty<*>> = isProperty(equalTo(name), matches(type))

fun isProperty(name: Matcher<in String>, type: Matcher<in String>): Matcher<KProperty<*>> = KPropertyMatcher(name, type)

fun isClass(name: String): Matcher<KClass<*>> = KClassMatcher(equalTo(name))

fun matches(type: KClass<*>): Matcher<in String> = equalTo(type.qualifiedName)

private fun Array<out KClass<*>>.toMatchers() = map(::hasParam).toTypedArray()

/**
 * Matcher logic for a Kotlin [KFunction] object. Also applicable to constructors.
 */
private class KFunctionMatcher(
    private val name: Matcher<in String>,
    private val returnType: Matcher<in String>,
    vararg parameters: Matcher<in KParameter>
) : DiagnosingMatcher<KFunction<*>>() {
    private val parameters = listOf(*parameters)

    override fun describeTo(description: Description) {
        description.appendText("KFunction[name as ").appendDescriptionOf(name)
            .appendText(", returnType as ").appendDescriptionOf(returnType)
            .appendText(", parameters as '")
        if (parameters.isNotEmpty()) {
            val param = parameters.iterator()
            description.appendValue(param.next())
            while (param.hasNext()) {
                description.appendText(",").appendValue(param.next())
            }
        }
        description.appendText("']")
    }

    override fun matches(obj: Any?, mismatch: Description): Boolean {
        if (obj == null) {
            mismatch.appendText("is null")
            return false
        }

        val function: KFunction<*> = obj as? KFunction<*> ?: return false
        if (!name.matches(function.name)) {
            mismatch.appendText("name is ").appendValue(function.name)
            return false
        }
        function.returnType.toString().apply {
            if (!returnType.matches(this)) {
                mismatch.appendText("returnType is ").appendValue(this)
                return false
            }
        }

        if (function.valueParameters.size != parameters.size) {
            mismatch.appendText("number of parameters is ").appendValue(function.valueParameters.size)
                .appendText(", parameters=").appendValueList("[", ",", "]", function.valueParameters)
            return false
        }

        var i = 0
        function.valueParameters.forEach { param ->
            if (!parameters[i].matches(param)) {
                mismatch.appendText("parameter[").appendValue(i).appendText("] is ").appendValue(param)
                return false
            }
            ++i
        }
        return true
    }
}

/**
 * Matcher logic for a Kotlin [KParameter] object.
 */
private class KParameterMatcher(
   private val type: Matcher<in String>
) : DiagnosingMatcher<KParameter>() {
    override fun describeTo(description: Description) {
        description.appendText("KParameter[type as ").appendDescriptionOf(type)
            .appendText("]")
    }

    override fun matches(obj: Any?, mismatch: Description): Boolean {
        if (obj == null) {
            mismatch.appendText("is null")
            return false
        }

        val parameter: KParameter = obj as? KParameter ?: return false
        parameter.type.toString().apply {
            if (!type.matches(this)) {
                mismatch.appendText("type is ").appendValue(this)
                return false
            }
        }
        return true
    }
}

/**
 * Matcher logic for a Kotlin [KProperty] object.
 */
private class KPropertyMatcher(
    private val name: Matcher<in String>,
    private val type: Matcher<in String>
) : DiagnosingMatcher<KProperty<*>>() {
    override fun describeTo(description: Description) {
        description.appendText("KProperty[name as ").appendDescriptionOf(name)
            .appendText(", type as ").appendDescriptionOf(type)
            .appendText("]")
    }

    override fun matches(obj: Any?, mismatch: Description): Boolean {
        if (obj == null) {
            mismatch.appendText("is null")
            return false
        }

        val property: KProperty<*> = obj as? KProperty<*> ?: return false
        if (!name.matches(property.name)) {
            mismatch.appendText("name is ").appendValue(property.name)
            return false
        }
        property.returnType.toString().apply {
            if (!type.matches(this)) {
                mismatch.appendText("type is ").appendValue(this)
                return false
            }
        }
        return true
    }
}

/**
 * Matcher logic for a Kotlin [KClass] object.
 */
private class KClassMatcher(private val className: Matcher<in String>) : DiagnosingMatcher<KClass<*>>() {
    override fun describeTo(description: Description) {
        description.appendText("KClass[name as ").appendDescriptionOf(className)
            .appendText("]")
    }

    override fun matches(obj: Any?, mismatch: Description): Boolean {
        if (obj == null) {
            mismatch.appendText("is null")
            return false
        }

        val type: KClass<*> = obj as? KClass<*> ?: return false
        type.jvmName.apply {
            if (!className.matches(this)) {
                mismatch.appendText("name is ").appendValue(this)
                return false
            }
        }
        return true
    }
}