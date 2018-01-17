package net.corda.lazyhub

import net.corda.core.internal.toMap
import net.corda.core.internal.toTypedArray
import net.corda.core.internal.uncheckedCast
import java.lang.reflect.*
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.KVisibility
import kotlin.reflect.jvm.internal.ReflectProperties
import kotlin.reflect.jvm.isAccessible

private val javaTypeDelegateField = Class.forName("kotlin.reflect.jvm.internal.KTypeImpl").getDeclaredField("javaType\$delegate").apply { isAccessible = true }
internal fun kotlin.reflect.KType.toJavaType() = (javaTypeDelegateField.get(this) as ReflectProperties.Val<*>)()
internal interface Provider<T> {
    /** Most specific known type i.e. directly registered implementation class, or declared return type of factory method. */
    val type: Class<T>
    /** May be lazily computed. */
    val obj: T
}

/** Like [Provider] but capable of supplying null. */
internal class ArgSupplier(val provider: Provider<*>?) {
    companion object {
        val nullSupplier = ArgSupplier(null)
    }

    operator fun invoke() = provider?.obj
}

/** Common interface to Kotlin/Java params. */
internal interface Param {
    val type: Class<*>
    /** The supplier, or null to supply nothing so the Kotlin default is used. */
    fun supplierWhenUnsatisfiable(): ArgSupplier? = throw (if (type.isArray) ::UnsatisfiableArrayException else ::NoSuchProviderException)(toString())
}

internal class KParam(val kParam: KParameter) : Param {
    override val type = run {
        var jType = kParam.type.toJavaType()
        loop@ while (true) {
            jType = when (jType) {
                is ParameterizedType -> jType.rawType
                is TypeVariable<*> -> jType.bounds.first() // Potentially surprising but most consistent behaviour, see unit tests.
                else -> break@loop
            }
        }
        jType as Class<*>
    }

    override fun supplierWhenUnsatisfiable() = when {
        kParam.isOptional -> null // Use default value, even if param is also nullable.
        kParam.type.isMarkedNullable -> ArgSupplier.nullSupplier
        else -> super.supplierWhenUnsatisfiable()
    }

    override fun toString() = kParam.toString()
}

internal class JParam(private val param: Parameter, private val index: Int, override val type: Class<*>) : Param {
    override fun toString() = "parameter #$index ${param.name} of ${param.declaringExecutable}"
}

internal interface PublicConstructor<P, out T> {
    val params: List<P>
    operator fun invoke(argSuppliers: List<Pair<P, ArgSupplier>>): T
}

internal class KConstructor<out T>(val kFunction: KFunction<T>) : PublicConstructor<KParam, T> {
    companion object {
        fun <T> KFunction<T>.validate() = run {
            if (returnType.isMarkedNullable) throw NullableReturnTypeException(toString())
            isAccessible = true
            KConstructor(this)
        }
    }

    override val params = kFunction.parameters.map(::KParam)
    override fun invoke(argSuppliers: List<Pair<KParam, ArgSupplier>>): T {
        return kFunction.callBy(argSuppliers.stream().map { (param, supplier) -> param.kParam to supplier() }.toMap())
    }

    override fun toString() = kFunction.toString()
}

internal class JConstructor<out T>(private val constructor: Constructor<T>) : PublicConstructor<JParam, T> {
    // Much cheaper to get the types up-front than via the Parameter API:
    override val params = constructor.parameters.zip(constructor.parameterTypes).mapIndexed { i, (p, t) -> JParam(p, i, t) }

    override fun invoke(argSuppliers: List<Pair<JParam, ArgSupplier>>): T {
        return constructor.newInstance(*argSuppliers.stream().map { (_, supplier) -> supplier() }.toTypedArray())
    }

    override fun toString() = constructor.toString()
}

internal interface Concrete<T, out C : PublicConstructor<*, T>> {
    val clazz: Class<T>
    val publicConstructors: List<C>
}

internal class KConcrete<T : Any> private constructor(private val kClass: KClass<T>) : Concrete<T, KConstructor<T>> {
    companion object {
        fun <T : Any> KClass<T>.validate() = run {
            if (isAbstract) throw AbstractTypeException(toString())
            KConcrete(this).apply {
                if (publicConstructors.isEmpty()) throw NoPublicConstructorsException(toString())
            }
        }
    }

    override val clazz get() = kClass.java
    override val publicConstructors = kClass.constructors.filter { it.visibility == KVisibility.PUBLIC }.map(::KConstructor)
    override fun toString() = kClass.toString()
}

internal class JConcrete<T> private constructor(override val clazz: Class<T>) : Concrete<T, JConstructor<T>> {
    companion object {
        fun <T> Class<T>.validate() = run {
            if (Modifier.isAbstract(modifiers)) throw AbstractTypeException(toString())
            JConcrete(this).apply {
                if (publicConstructors.isEmpty()) throw NoPublicConstructorsException(toString())
            }
        }
    }

    override val publicConstructors = uncheckedCast<Array<out Constructor<*>>, Array<Constructor<T>>>(clazz.constructors).map(::JConstructor)
    override fun toString() = clazz.toString()
}
