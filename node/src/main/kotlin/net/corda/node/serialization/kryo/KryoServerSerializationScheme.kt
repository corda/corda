/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node.serialization.kryo

import com.esotericsoftware.kryo.pool.KryoPool
import net.corda.core.serialization.SerializationContext
import net.corda.nodeapi.internal.serialization.CordaSerializationMagic
import net.corda.nodeapi.internal.serialization.kryo.AbstractKryoSerializationScheme
import net.corda.nodeapi.internal.serialization.kryo.kryoMagic

class KryoServerSerializationScheme : AbstractKryoSerializationScheme() {
    override fun canDeserializeVersion(magic: CordaSerializationMagic, target: SerializationContext.UseCase): Boolean {
        return magic == kryoMagic && target == SerializationContext.UseCase.Checkpoint
    }

    override fun rpcClientKryoPool(context: SerializationContext): KryoPool = throw UnsupportedOperationException()

    override fun rpcServerKryoPool(context: SerializationContext): KryoPool = throw UnsupportedOperationException()

}