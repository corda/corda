package net.corda.core.internal.reflection

import java.lang.UnsupportedOperationException
import java.lang.reflect.*
import java.util.*

internal class ArtificialParameterizedType(
        private val _rawType: Type,
        private val _ownerType: Type?,
        private val _actualTypeArguments: Array<Type>) : ParameterizedType {
    override fun getRawType(): Type = _rawType
    override fun getOwnerType(): Type? = _ownerType
    override fun getActualTypeArguments(): Array<Type> = _actualTypeArguments
    override fun toString(): String = TypeIdentifier.forGenericType(this).prettyPrint(false)
    override fun equals(other: Any?): Boolean =
            other is ParameterizedType &&
                    other.rawType == rawType &&
                    other.ownerType == ownerType &&
                    Arrays.equals(other.actualTypeArguments, actualTypeArguments)
    override fun hashCode(): Int =
            Arrays.hashCode(actualTypeArguments) xor Objects.hashCode(ownerType) xor Objects.hashCode(rawType)
}

internal class ArtificialGenericArrayType(private val componentType: Type) : GenericArrayType {
    override fun getGenericComponentType(): Type = componentType
    override fun toString() = "$componentType[]"
    override fun equals(other: Any?): Boolean =
            other is GenericArrayType && componentType == other.genericComponentType
    override fun hashCode(): Int = Objects.hashCode(componentType)
}

internal data class ArtificialTypeVariable<D : GenericDeclaration>(private val _genericDeclaration: D, private val _name: String, private val boundsList: List<Type>): TypeVariable<D> {
    override fun getName(): String = _name

    override fun getAnnotatedBounds(): Array<AnnotatedType> {
        throw UnsupportedOperationException("getAnnotatedBounds")
    }

    override fun getDeclaredAnnotations(): Array<Annotation> = emptyArray()
    override fun getGenericDeclaration(): D = _genericDeclaration
    override fun <T : Annotation?> getAnnotation(annotationClass: Class<T>?): T? = null

    override fun getAnnotations(): Array<Annotation> = emptyArray()
    override fun getBounds(): Array<Type> = boundsList.toTypedArray()
    override fun getTypeName(): String = _name
    override fun toString(): String = _name
}

internal class ArtificialWildcardType(val _lowerBounds: Array<Type>, val _upperBounds: Array<Type>) : WildcardType {

    override fun getLowerBounds(): Array<Type> = _lowerBounds
    override fun getUpperBounds(): Array<Type> = _upperBounds

    override fun equals(other: Any?): Boolean =
            other is WildcardType &&
                    Arrays.equals(_lowerBounds, other.lowerBounds) &&
                    Arrays.equals(_upperBounds, other.upperBounds)

    override fun hashCode(): Int {
        return lowerBounds.hashCode() xor upperBounds.hashCode()
    }

    override fun toString(): String {
        val supers = lowerBounds.joinToString { " super ${it.typeName}" }
        val extends = upperBounds.joinToString { " extends ${it.typeName}" }
        return "?$supers$extends"
    }
}
