package net.corda.serialization.internal.amqp

import net.corda.core.serialization.SerializationContext
import org.apache.qpid.proton.codec.Data
import org.hamcrest.CoreMatchers.`is`
import org.junit.Assert
import org.junit.Test
import java.io.InputStream
import java.lang.reflect.Type
import java.util.zip.ZipInputStream

class CustomSerializerTest {


    @Test
    fun `should count hops to super class`() {
        val lifeSerializer = object : CustomSerializer.CustomSerializerImp<Life>(Life::class.java, true) {
            override val schemaForDocumentation: Schema
                get() = TODO("not implemented")

            override fun writeDescribedObject(obj: Life, data: Data, type: Type, output: SerializationOutput, context: SerializationContext) {
                TODO("not implemented")
            }

            override fun readObject(obj: Any, schemas: SerializationSchemas, input: DeserializationInput, context: SerializationContext): Life {
                TODO("not implemented")
            }
        }

        val fungusSerializer = object : CustomSerializer.CustomSerializerImp<Fungus>(Fungus::class.java, true) {
            override val schemaForDocumentation: Schema
                get() = TODO("not implemented")

            override fun writeDescribedObject(obj: Fungus, data: Data, type: Type, output: SerializationOutput, context: SerializationContext) {
                TODO("not implemented")
            }

            override fun readObject(obj: Any, schemas: SerializationSchemas, input: DeserializationInput, context: SerializationContext): Fungus {
                TODO("not implemented")
            }
        }

        val magicSerializer = object : CustomSerializer.CustomSerializerImp<AmanitaMuscaria>(AmanitaMuscaria::class.java, true) {
            override val schemaForDocumentation: Schema
                get() = TODO("not implemented")

            override fun writeDescribedObject(obj: AmanitaMuscaria, data: Data, type: Type, output: SerializationOutput, context: SerializationContext) {
                TODO("not implemented")
            }

            override fun readObject(obj: Any, schemas: SerializationSchemas, input: DeserializationInput, context: SerializationContext): AmanitaMuscaria {
                TODO("not implemented")
            }
        }

        val zipSerializer = object : CustomSerializer.CustomSerializerImp<InputStream>(InputStream::class.java, true) {
            override val schemaForDocumentation: Schema
                get() = TODO("not implemented")

            override fun writeDescribedObject(obj: InputStream, data: Data, type: Type, output: SerializationOutput, context: SerializationContext) {
                TODO("not implemented")
            }

            override fun readObject(obj: Any, schemas: SerializationSchemas, input: DeserializationInput, context: SerializationContext): InputStream {
                TODO("not implemented")
            }
        }

        Assert.assertThat(lifeSerializer.isSerializerFor(Life::class.java), `is`(true))
        Assert.assertThat(lifeSerializer.serializationMatch(Life::class.java), `is`(0))

        Assert.assertThat(fungusSerializer.isSerializerFor(Life::class.java), `is`(false))
        Assert.assertThat(fungusSerializer.serializationMatch(Life::class.java), `is`(Int.MAX_VALUE))

        Assert.assertThat(lifeSerializer.isSerializerFor(Life::class.java), `is`(true))
        Assert.assertThat(lifeSerializer.serializationMatch(Life::class.java), `is`(0))

        Assert.assertThat(lifeSerializer.isSerializerFor(Animalia::class.java), `is`(true))
        Assert.assertThat(lifeSerializer.serializationMatch(Animalia::class.java), `is`(1))

        Assert.assertThat(lifeSerializer.isSerializerFor(Dog::class.java), `is`(true))
        Assert.assertThat(lifeSerializer.serializationMatch(Dog::class.java), `is`(2))

        Assert.assertThat(lifeSerializer.isSerializerFor(Fungus::class.java), `is`(true))
        Assert.assertThat(lifeSerializer.serializationMatch(Fungus::class.java), `is`(1))

        Assert.assertThat(lifeSerializer.isSerializerFor(AmanitaMuscaria::class.java), `is`(true))
        Assert.assertThat(lifeSerializer.serializationMatch(AmanitaMuscaria::class.java), `is`(2))

        Assert.assertThat(fungusSerializer.isSerializerFor(AmanitaMuscaria::class.java), `is`(true))
        Assert.assertThat(fungusSerializer.serializationMatch(AmanitaMuscaria::class.java), `is`(1))

        Assert.assertThat(magicSerializer.isSerializerFor(AmanitaMuscaria::class.java), `is`(true))
        Assert.assertThat(magicSerializer.serializationMatch(AmanitaMuscaria::class.java), `is`(0))

        println(zipSerializer.serializationMatch(ZipInputStream::class.java))

    }
}


open class Life
open class Animalia : Life()
open class Dog : Animalia()
open class Fungus : Life()
open class AmanitaMuscaria : Fungus()