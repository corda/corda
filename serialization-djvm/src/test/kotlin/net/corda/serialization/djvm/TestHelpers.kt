@file:JvmName("TestHelpers")
package net.corda.serialization.djvm

import net.corda.serialization.internal.SectionId
import net.corda.serialization.internal.amqp.Envelope
import net.corda.serialization.internal.amqp.alsoAsByteBuffer
import net.corda.serialization.internal.amqp.amqpMagic
import net.corda.serialization.internal.amqp.withDescribed
import net.corda.serialization.internal.amqp.withList
import org.apache.qpid.proton.codec.Data
import java.io.ByteArrayOutputStream

fun Envelope.write(): ByteArray {
    val data = Data.Factory.create()
    data.withDescribed(Envelope.DESCRIPTOR_OBJECT) {
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
