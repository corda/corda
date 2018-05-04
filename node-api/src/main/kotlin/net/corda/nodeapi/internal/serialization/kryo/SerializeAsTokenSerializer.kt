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
import com.esotericsoftware.kryo.KryoException
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import net.corda.core.internal.castIfPossible
import net.corda.core.serialization.SerializationToken
import net.corda.core.serialization.SerializeAsToken

/**
 * A Kryo serializer for [SerializeAsToken] implementations.
 */
class SerializeAsTokenSerializer<T : SerializeAsToken> : Serializer<T>() {
    override fun write(kryo: Kryo, output: Output, obj: T) {
        kryo.writeClassAndObject(output, obj.toToken(kryo.serializationContext() ?: throw KryoException("Attempt to write a ${SerializeAsToken::class.simpleName} instance of ${obj.javaClass.name} without initialising a context")))
    }

    override fun read(kryo: Kryo, input: Input, type: Class<T>): T {
        val token = (kryo.readClassAndObject(input) as? SerializationToken) ?: throw KryoException("Non-token read for tokenized type: ${type.name}")
        val fromToken = token.fromToken(kryo.serializationContext() ?: throw KryoException("Attempt to read a token for a ${SerializeAsToken::class.simpleName} instance of ${type.name} without initialising a context"))
        return type.castIfPossible(fromToken) ?: throw KryoException("Token read ($token) did not return expected tokenized type: ${type.name}")
    }
}