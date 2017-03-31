package net.corda.core.serialization.amqp

import java.lang.UnsupportedOperationException
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type


class DeserializedParameterizedType(name: String) : ParameterizedType {
    val _typeName: String = name.toString()
    val _rawType = Class.forName(_typeName.substringBefore('<'))

    val _actualTypeParameters = {
        // TODO: this needs to parse properly
        // Parse name of form C<X,Y,Z...>
        val text = name.toString()
        val rawTypeName = text.substringBefore('<')
        val params = text.substringAfter('<').substringBeforeLast('>')
        val paramList = params.split(',')
        (paramList.map { Class.forName(it) }).toTypedArray()
    }()

    override fun getRawType(): Type = _rawType

    override fun getOwnerType(): Type {
        throw UnsupportedOperationException("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getActualTypeArguments(): Array<out Type> = _actualTypeParameters

    override fun getTypeName(): String {
        return _typeName
    }
}