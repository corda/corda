package net.corda.nodeapi.internal.serialization.amqp

import java.io.NotSerializableException
import java.lang.reflect.Type

class SyntheticParameterException(val type: Type) : NotSerializableException("Type '${type.typeName} has synthetic "
        + "fields and is likely a nested inner class. This is not support by the Corda AMQP serialization framework")