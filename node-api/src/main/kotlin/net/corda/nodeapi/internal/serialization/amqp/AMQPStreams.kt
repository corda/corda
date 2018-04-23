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

import com.esotericsoftware.kryo.io.ByteBufferInputStream
import net.corda.nodeapi.internal.serialization.kryo.ByteBufferOutputStream
import net.corda.nodeapi.internal.serialization.kryo.serializeOutputStreamPool
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer

fun InputStream.asByteBuffer(): ByteBuffer {
    return if (this is ByteBufferInputStream) {
        byteBuffer // BBIS has no other state, so this is perfectly safe.
    } else {
        ByteBuffer.wrap(serializeOutputStreamPool.run {
            copyTo(it)
            it.toByteArray()
        })
    }
}

fun <T> OutputStream.alsoAsByteBuffer(remaining: Int, task: (ByteBuffer) -> T): T {
    return if (this is ByteBufferOutputStream) {
        alsoAsByteBuffer(remaining, task)
    } else {
        serializeOutputStreamPool.run {
            val result = it.alsoAsByteBuffer(remaining, task)
            it.copyTo(this)
            result
        }
    }
}
