@file:JvmName("EnvelopeHelpers")
package net.corda.serialization.internal.amqp

import net.corda.serialization.internal.SectionId
import net.corda.serialization.internal.amqp.Envelope.Companion.DESCRIPTOR_OBJECT
import org.apache.qpid.proton.codec.Data
import java.io.ByteArrayOutputStream

fun Envelope.write(): ByteArray {
    val data = Data.Factory.create()
    data.withDescribed(DESCRIPTOR_OBJECT) {
        withList {
            putObject(obj)
            putObject(schema)
            putObject(transformsSchema)
        }
    }
    return ByteArrayOutputStream().use {
        amqpMagic.writeTo(it)
        SectionId.DATA_AND_STOP.writeTo(it)
        it.alsoAsByteBuffer(data.encodedSize().toInt(), data::encode)
        it.toByteArray()
    }
}