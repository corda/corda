@file:JvmName("DJVM")
package sandbox.java.lang

fun Any.unsandbox(): Any {
    return when (this) {
        is Object -> fromDJVM()
        is ByteArray -> this
        is IntArray -> this
        is LongArray -> this
        is DoubleArray -> this
        is FloatArray -> this
        is ShortArray -> this
        is CharArray -> this
        is BooleanArray -> this
        is Array<*> -> fromDJVMArray()
        else -> this
    }
}

fun Any.sandbox(): Any {
    return when (this) {
        is kotlin.String -> String.toDJVM(this)
        is kotlin.Char -> Character.toDJVM(this)
        is kotlin.Long -> Long.toDJVM(this)
        is kotlin.Int -> Integer.toDJVM(this)
        is kotlin.Short -> Short.toDJVM(this)
        is kotlin.Byte -> Byte.toDJVM(this)
        is kotlin.Float -> Float.toDJVM(this)
        is kotlin.Double -> Double.toDJVM(this)
        is kotlin.Boolean -> Boolean.toDJVM(this)
        is ByteArray -> this
        is IntArray -> this
        is LongArray -> this
        is DoubleArray -> this
        is FloatArray -> this
        is ShortArray -> this
        is CharArray -> this
        is BooleanArray -> this
        is Array<*> -> toDJVMArray<Object>()
        else -> this
    }
}

private fun Array<*>.fromDJVMArray(): Array<*> {
    return Object.fromDJVM(this)
}

private fun Class<*>.toDJVMType(): Class<*> {
    return when (this) {
        kotlin.String::class.java -> String::class.java
        kotlin.Long::class.javaObjectType -> Long::class.java
        kotlin.Int::class.javaObjectType -> Integer::class.java
        kotlin.Short::class.javaObjectType -> Short::class.java
        kotlin.Byte::class.javaObjectType -> Byte::class.java
        kotlin.Double::class.javaObjectType -> Double::class.java
        kotlin.Float::class.javaObjectType -> Float::class.java
        kotlin.Boolean::class.javaObjectType -> Boolean::class.java
        kotlin.Char::class.javaObjectType -> Character::class.java
        kotlin.Any::class.java -> Object::class.java
        else -> throw IllegalArgumentException("Unknown type $name")
    }
}

private inline fun <reified T : Object> Array<*>.toDJVMArray(): Array<out T?> {
    @Suppress("unchecked_cast")
    return (java.lang.reflect.Array.newInstance(javaClass.componentType.toDJVMType(), size) as Array<T?>).also {
        for ((i, item) in withIndex()) {
            it[i] = item?.sandbox() as T
        }
    }
}
