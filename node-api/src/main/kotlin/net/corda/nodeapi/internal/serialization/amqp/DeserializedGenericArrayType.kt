/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.nodeapi.internal.serialization.amqp

import java.lang.reflect.GenericArrayType
import java.lang.reflect.Type
import java.util.*

/**
 * Implementation of [GenericArrayType] that we can actually construct.
 */
class DeserializedGenericArrayType(private val componentType: Type) : GenericArrayType {
    override fun getGenericComponentType(): Type = componentType
    override fun getTypeName(): String = "${componentType.typeName}[]"
    override fun toString(): String = typeName
    override fun hashCode(): Int = Objects.hashCode(componentType)
    override fun equals(other: Any?): Boolean {
        return other is GenericArrayType && (componentType == other.genericComponentType)
    }
}
