@file:JvmName("JavaMatchers")
package net.corda.gradle.jarfilter.matcher

import org.hamcrest.Description
import org.hamcrest.DiagnosingMatcher
import org.hamcrest.Matcher
import org.hamcrest.core.IsEqual.*
import java.lang.reflect.Method
import kotlin.reflect.KClass

fun isMethod(name: Matcher<in String>, returnType: Matcher<in Class<*>>, vararg parameters: Matcher<in Class<*>>): Matcher<Method> {
    return MethodMatcher(name, returnType, *parameters)
}

fun isMethod(name: String, returnType: Class<*>, vararg parameters: Class<*>): Matcher<Method> {
    return isMethod(equalTo(name), equalTo(returnType), *parameters.toMatchers())
}

private fun Array<out Class<*>>.toMatchers() = map(::equalTo).toTypedArray()

val KClass<*>.javaDeclaredMethods: List<Method> get() = java.declaredMethods.toList()

/**
 * Matcher logic for a Java [Method] object. Also applicable to constructors.
 */
private class MethodMatcher(
    private val name: Matcher<in String>,
    private val returnType: Matcher<in Class<*>>,
    vararg parameters: Matcher<in Class<*>>
) : DiagnosingMatcher<Method>() {
    private val parameters = listOf(*parameters)

    override fun describeTo(description: Description) {
        description.appendText("Method[name as ").appendDescriptionOf(name)
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

        val method: Method = obj as? Method ?: return false
        if (!name.matches(method.name)) {
            mismatch.appendText("name is ").appendValue(method.name)
            return false
        }
        method.returnType.apply {
            if (!returnType.matches(this)) {
                mismatch.appendText("returnType is ").appendValue(this.name)
                return false
            }
        }

        if (method.parameterTypes.size != parameters.size) {
            mismatch.appendText("number of parameters is ").appendValue(method.parameterTypes.size)
                    .appendText(", parameters=").appendValueList("[", ",", "]", method.parameterTypes)
            return false
        }

        var i = 0
        method.parameterTypes.forEach { param ->
            if (!parameters[i].matches(param)) {
                mismatch.appendText("parameter[").appendValue(i).appendText("] is ").appendValue(param)
                return false
            }
            ++i
        }
        return true
    }
}
