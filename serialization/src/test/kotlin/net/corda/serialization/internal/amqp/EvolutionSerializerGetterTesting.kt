/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.serialization.internal.amqp

import java.io.NotSerializableException

/**
 * An implementation of [EvolutionSerializerGetterBase] that disables all evolution within a
 * [SerializerFactory]. This is most useful in testing where it is known that evolution should not be
 * occurring and where bugs may be hidden by transparent invocation of an [EvolutionSerializer]. This
 * prevents that by simply throwing an exception whenever such a serializer is requested.
 */
class EvolutionSerializerGetterTesting : EvolutionSerializerGetterBase() {
    override fun getEvolutionSerializer(factory: SerializerFactory,
                                        typeNotation: TypeNotation,
                                        newSerializer: AMQPSerializer<Any>,
                                        schemas: SerializationSchemas): AMQPSerializer<Any> {
        throw NotSerializableException("No evolution should be occurring\n" +
                "    ${typeNotation.name}\n" +
                "        ${typeNotation.descriptor.name}\n" +
                "    ${newSerializer.type.typeName}\n" +
                "        ${newSerializer.typeDescriptor}\n\n${schemas.schema}")
    }
}
