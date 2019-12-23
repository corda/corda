@file:JvmName("Serializers")
package net.corda.serialization.djvm.serializers

import net.corda.serialization.internal.model.TypeIdentifier
import java.lang.reflect.Type
import java.util.Collections.singleton

fun aliasFor(type: Type): Set<TypeIdentifier> = singleton(TypeIdentifier.forGenericType(type))
