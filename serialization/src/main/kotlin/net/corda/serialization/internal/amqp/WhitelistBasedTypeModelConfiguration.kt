package net.corda.serialization.internal.amqp

import com.google.common.primitives.Primitives
import net.corda.core.serialization.ClassWhitelist
import net.corda.serialization.internal.model.LocalTypeModelConfiguration
import java.lang.reflect.Type

/**
 * [LocalTypeModelConfiguration] based on a [ClassWhitelist]
 */
class WhitelistBasedTypeModelConfiguration(
        private val whitelist: ClassWhitelist,
        private val customSerializerRegistry: CustomSerializerRegistry)
    : LocalTypeModelConfiguration {
    override fun isExcluded(type: Type): Boolean = whitelist.isNotWhitelisted(type.asClass())
    override fun isOpaque(type: Type): Boolean = Primitives.unwrap(type.asClass()) in opaqueTypes ||
            customSerializerRegistry.findCustomSerializer(type.asClass(), type) != null
}