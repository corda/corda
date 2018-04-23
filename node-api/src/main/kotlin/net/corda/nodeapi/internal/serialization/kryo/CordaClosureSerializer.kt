/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.nodeapi.internal.serialization.kryo

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.Output
import com.esotericsoftware.kryo.serializers.ClosureSerializer
import java.io.Serializable

object CordaClosureSerializer : ClosureSerializer() {
    val ERROR_MESSAGE = "Unable to serialize Java Lambda expression, unless explicitly declared e.g., Runnable r = (Runnable & Serializable) () -> System.out.println(\"Hello world!\");"

    override fun write(kryo: Kryo, output: Output, target: Any) {
        if (!isSerializable(target)) {
            throw IllegalArgumentException(ERROR_MESSAGE)
        }
        super.write(kryo, output, target)
    }

    private fun isSerializable(target: Any): Boolean {
        return target is Serializable
    }
}

object CordaClosureBlacklistSerializer : ClosureSerializer() {
    val ERROR_MESSAGE = "Java 8 Lambda expressions are not supported for serialization."

    override fun write(kryo: Kryo, output: Output, target: Any) {
        throw IllegalArgumentException(ERROR_MESSAGE)
    }
}