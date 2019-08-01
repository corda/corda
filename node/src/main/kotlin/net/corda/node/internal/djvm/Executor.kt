package net.corda.node.internal.djvm

import java.lang.reflect.Constructor
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

class Executor(classLoader: ClassLoader) {
    private val constructor: Constructor<out Any>
    private val executeMethod: Method

    init {
        val taskClass = classLoader.loadClass("sandbox.RawTask")
        constructor = taskClass.getDeclaredConstructor(classLoader.loadClass("sandbox.java.util.function.Function"))
        executeMethod = taskClass.getMethod("apply", Any::class.java)
    }

    fun execute(task: Any, input: Any?): Any? {
        return try {
            executeMethod.invoke(constructor.newInstance(task), input)
        } catch (ex: InvocationTargetException) {
            throw ex.targetException
        }
    }
}
