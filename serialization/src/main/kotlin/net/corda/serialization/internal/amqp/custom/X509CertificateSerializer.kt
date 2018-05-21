/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.serialization.internal.amqp.custom

import net.corda.core.serialization.SerializationContext
import net.corda.serialization.internal.amqp.*
import org.apache.qpid.proton.codec.Data
import java.lang.reflect.Type
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

object X509CertificateSerializer : CustomSerializer.Implements<X509Certificate>(X509Certificate::class.java) {
    override val schemaForDocumentation = Schema(listOf(RestrictedType(
            type.toString(),
            "",
            listOf(type.toString()),
            SerializerFactory.primitiveTypeName(ByteArray::class.java)!!,
            descriptor,
            emptyList()
    )))

    override fun writeDescribedObject(obj: X509Certificate, data: Data, type: Type, output: SerializationOutput,
                                      context: SerializationContext) {
        output.writeObject(obj.encoded, data, clazz, context)
    }

    override fun readObject(obj: Any, schemas: SerializationSchemas, input: DeserializationInput,
                            context: SerializationContext): X509Certificate {
        val bits = input.readObject(obj, schemas, ByteArray::class.java, context) as ByteArray
        return CertificateFactory.getInstance("X.509").generateCertificate(bits.inputStream()) as X509Certificate
    }
}
