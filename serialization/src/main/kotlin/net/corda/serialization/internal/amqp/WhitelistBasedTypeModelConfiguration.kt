package net.corda.serialization.internal.amqp

import com.google.common.primitives.Primitives
import net.corda.core.serialization.ClassWhitelist
import net.corda.serialization.internal.model.BaseLocalTypes
import net.corda.serialization.internal.model.LocalTypeModelConfiguration
import org.apache.qpid.proton.amqp.*
import java.lang.reflect.Type
import java.util.Date
import java.util.EnumSet
import java.util.UUID
import java.util.function.Function
import java.util.function.Predicate

/**
 * [LocalTypeModelConfiguration] based on a [ClassWhitelist]
 */
class WhitelistBasedTypeModelConfiguration(
        private val whitelist: ClassWhitelist,
        private val customSerializerRegistry: CustomSerializerRegistry,
        override val baseTypes: BaseLocalTypes
) : LocalTypeModelConfiguration {
    constructor(whitelist: ClassWhitelist, customSerializerRegistry: CustomSerializerRegistry)
        : this(whitelist, customSerializerRegistry, DEFAULT_BASE_TYPES)

    override fun isExcluded(type: Type): Boolean = whitelist.isNotWhitelisted(type.asClass())
    override fun isOpaque(type: Type): Boolean = Primitives.unwrap(type.asClass()) in opaqueTypes ||
            customSerializerRegistry.findCustomSerializer(type.asClass(), type) != null
}

// Copied from SerializerFactory so that we can have equivalent behaviour, for now.
private val opaqueTypes = setOf(
        Character::class.java,
        Char::class.java,
        Boolean::class.java,
        Byte::class.java,
        UnsignedByte::class.java,
        Short::class.java,
        UnsignedShort::class.java,
        Int::class.java,
        UnsignedInteger::class.java,
        Long::class.java,
        UnsignedLong::class.java,
        Float::class.java,
        Double::class.java,
        Decimal32::class.java,
        Decimal64::class.java,
        Decimal128::class.java,
        Date::class.java,
        UUID::class.java,
        ByteArray::class.java,
        String::class.java,
        Symbol::class.java
)

@Suppress("unchecked_cast")
private val DEFAULT_BASE_TYPES = BaseLocalTypes(
    collectionClass = Collection::class.java,
    enumSetClass = EnumSet::class.java,
    exceptionClass = Exception::class.java,
    mapClass = Map::class.java,
    stringClass = String::class.java,
    isEnum = Predicate { clazz -> clazz.isEnum },
    enumConstants = Function { clazz -> clazz.enumConstants },
    enumConstantNames = Function { clazz ->
        (clazz as Class<out Enum<*>>).enumConstants.map(Enum<*>::name)
    }
)