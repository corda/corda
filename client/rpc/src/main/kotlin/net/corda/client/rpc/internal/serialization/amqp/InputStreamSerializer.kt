package net.corda.client.rpc.internal.serialization.amqp

import net.corda.core.serialization.SerializationContext
import net.corda.nodeapi.internal.serialization.amqp.*
import org.apache.qpid.proton.codec.Data
import java.io.InputStream
import java.lang.reflect.Type

object InputStreamSerializer  : CustomSerializer.Implements<InputStream>(InputStream::class.java){
    override val schemaForDocumentation: Schema
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.

    override fun readObject(obj: Any, schemas: SerializationSchemas, input: DeserializationInput, context: SerializationContext): InputStream {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun writeDescribedObject(obj: InputStream, data: Data, type: Type, output: SerializationOutput, context: SerializationContext) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}