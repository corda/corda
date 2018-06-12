/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.serialization.internal

import net.corda.core.KeepForDJVM
import net.corda.core.serialization.SerializationEncoding
import net.corda.core.utilities.ByteSequence
import net.corda.core.utilities.OpaqueBytes
import net.corda.serialization.internal.OrdinalBits.OrdinalWriter
import org.iq80.snappy.SnappyFramedInputStream
import org.iq80.snappy.SnappyFramedOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream

@KeepForDJVM
class CordaSerializationMagic(bytes: ByteArray) : OpaqueBytes(bytes) {
    private val bufferView = slice()
    fun consume(data: ByteSequence): ByteBuffer? {
        return if (data.slice(end = size) == bufferView) data.slice(size) else null
    }
}

@KeepForDJVM
enum class SectionId : OrdinalWriter {
    /** Serialization data follows, and then discard the rest of the stream (if any) as legacy data may have trailing garbage. */
    DATA_AND_STOP,
    /** Identical behaviour to [DATA_AND_STOP], historically used for Kryo. Do not use in new code. */
    ALT_DATA_AND_STOP,
    /** The ordinal of a [CordaSerializationEncoding] follows, which should be used to decode the remainder of the stream. */
    ENCODING;

    companion object {
        val reader = OrdinalReader(values())
    }

    override val bits = OrdinalBits(ordinal)
}

@KeepForDJVM
enum class CordaSerializationEncoding : SerializationEncoding, OrdinalWriter {
    DEFLATE {
        override fun wrap(stream: OutputStream) = DeflaterOutputStream(stream)
        override fun wrap(stream: InputStream) = InflaterInputStream(stream)
    },
    SNAPPY {
        override fun wrap(stream: OutputStream) = SnappyFramedOutputStream(stream)
        override fun wrap(stream: InputStream) = SnappyFramedInputStream(stream, false)
    };

    companion object {
        val reader = OrdinalReader(values())
    }

    override val bits = OrdinalBits(ordinal)
    abstract fun wrap(stream: OutputStream): OutputStream
    abstract fun wrap(stream: InputStream): InputStream
}

const val encodingNotPermittedFormat = "Encoding not permitted: %s"
