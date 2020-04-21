package net.corda.serialization.internal.amqp

import net.corda.core.CordaRuntimeException
import net.corda.core.serialization.SerializedBytes
import net.corda.serialization.internal.amqp.testutils.deserialize
import net.corda.serialization.internal.amqp.testutils.serialize
import net.corda.serialization.internal.amqp.testutils.testDefaultFactory
import net.corda.serialization.internal.amqp.testutils.writeTestResource
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class ThrowableEvolutionTests
{
    private val toBeRemovedValue = "Remove"
    private val message = "Test Message"

    /**
     * AddConstructorParametersException with an extra parameter called "added"
     */
//    class AddConstructorParametersException(message: String) : CordaRuntimeException(message)
    class AddConstructorParametersException(message: String, val added: String?) : CordaRuntimeException(message)

    /**
     * RemoveConstructorParametersException with the "toBeRemoved" parameter removed
     */
//    class RemoveConstructorParametersException(message: String, val toBeRemoved: String) : CordaRuntimeException(message)
    class RemoveConstructorParametersException(message: String) : CordaRuntimeException(message)

    /**
     * AddAndRemoveConstructorParametersException with the "toBeRemoved" parameter removed and "added" added
     */
//    class AddAndRemoveConstructorParametersException(message: String, val toBeRemoved: String) : CordaRuntimeException(message)
    class AddAndRemoveConstructorParametersException(message: String, val added: String?) : CordaRuntimeException(message)

    @Test(timeout=300_000)
    fun `We can evolve exceptions by adding constructor parameters`() {

//        val exception = AddConstructorParametersException(message)
//        saveSerializedObject(exception)

        val bytes = ThrowableEvolutionTests::class.java.getResource("ThrowableEvolutionTests.AddConstructorParametersException").readBytes()

        val sf = testDefaultFactory()
        val deserializedException = DeserializationInput(sf).deserialize(SerializedBytes<AddConstructorParametersException>(bytes))

        assertThat(deserializedException.message).isEqualTo(message)
        assertThat(deserializedException).isInstanceOf(AddConstructorParametersException::class.java)
    }

    @Test(timeout=300_000)
    fun `We can evolve exceptions by removing constructor parameters`() {

//        val exception = RemoveConstructorParametersException(message, toBeRemovedValue)
//        saveSerializedObject(exception)

        val bytes = ThrowableEvolutionTests::class.java.getResource("ThrowableEvolutionTests.RemoveConstructorParametersException").readBytes()
        val sf = testDefaultFactory()
        val deserializedException = DeserializationInput(sf).deserialize(SerializedBytes<RemoveConstructorParametersException>(bytes))

        assertThat(deserializedException.message).isEqualTo(message)
        assertThat(deserializedException).isInstanceOf(RemoveConstructorParametersException::class.java)
    }

    @Test(timeout=300_000)
    fun `We can evolve exceptions by adding and removing constructor parameters`() {

//        val exception = AddAndRemoveConstructorParametersException(message, toBeRemovedValue)
//        saveSerializedObject(exception)

        val bytes = ThrowableEvolutionTests::class.java.getResource("ThrowableEvolutionTests.AddAndRemoveConstructorParametersException").readBytes()

        val sf = testDefaultFactory()
        val deserializedException = DeserializationInput(sf).deserialize(SerializedBytes<AddAndRemoveConstructorParametersException>(bytes))

        assertThat(deserializedException.message).isEqualTo(message)
        assertThat(deserializedException).isInstanceOf(AddAndRemoveConstructorParametersException::class.java)
    }

    /**
     * Write serialized object to resources folder
     */
    @Suppress("unused")
    fun <T : Any> saveSerializedObject(obj : T){

        val sf = testDefaultFactory()
        val serializedBytes = SerializationOutput(sf).serialize(obj)
        writeTestResource(serializedBytes)
    }
}