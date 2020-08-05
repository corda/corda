package net.corda.serialization.internal.amqp

import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.Contract
import net.corda.core.contracts.ContractState
import net.corda.core.identity.AbstractParty
import net.corda.core.serialization.DeprecatedConstructorForDeserialization
import net.corda.core.serialization.SerializedBytes
import net.corda.core.transactions.LedgerTransaction
import net.corda.serialization.internal.amqp.testutils.deserialize
import net.corda.serialization.internal.amqp.testutils.serialize
import net.corda.serialization.internal.amqp.testutils.testDefaultFactory
import net.corda.serialization.internal.amqp.testutils.writeTestResource
import org.assertj.core.api.Assertions
import org.junit.Test

class Corda3824
{
    private val cordappVersionTestValue = 38854445
    private val dataTestValue = "d7af8af0-c10e-45bc-a5f7-92de432be0ef"
    private val xTestValue = 7568055
    private val yTestValue = 4113687

    class TemplateContract : Contract {
        override fun verify(tx: LedgerTransaction) { }
    }

    /**
     * Step 1
     */
//    @BelongsToContract(TemplateContract::class)
//    data class TemplateState(val cordappVersion: Int, val data: String, val x : Int?, override val participants: List<AbstractParty> = listOf()) : ContractState

    /**
     * Step 2
     */
//    @BelongsToContract(TemplateContract::class)
//    data class TemplateState(val cordappVersion: Int, val data: String, val x : Int?, val y : String?, override val participants: List<AbstractParty> = listOf()) : ContractState {
//        @DeprecatedConstructorForDeserialization(1)
//        constructor(cordappVersion: Int, data : String, x : Int?, participants: List<AbstractParty>)
//                : this(cordappVersion, data, null, x?.toString(), participants)
//    }

    /**
     * Step 3
     */
    @BelongsToContract(TemplateContract::class)
    data class TemplateState(val cordappVersion: Int, val data: String, val y : String?, override val participants: List<AbstractParty> = listOf()) : ContractState {
        @DeprecatedConstructorForDeserialization(1)
        constructor(cordappVersion: Int, data : String, x : Int?, participants: List<AbstractParty>) : this(cordappVersion, data, x?.toString(), participants)
    }

    @Test(timeout=300_000)
    fun `Step 1 to Step 3`() {

//        val step1 = TemplateState(cordappVersionTestValue, dataTestValue, xTestValue)
//        saveSerializedObject(step1)

        // serialization/src/test/resources/net/corda/serialization/internal/amqp/Corda3824.Step 1 to Step 3
        val bytes = ThrowableEvolutionTests::class.java.getResource("Corda3824.Step 1 to Step 3").readBytes()

        val serializerFactory: SerializerFactory = testDefaultFactory()
        val deserializedException = DeserializationInput(serializerFactory)
                .deserialize(SerializedBytes<TemplateState>(bytes))

        Assertions.assertThat(deserializedException.cordappVersion).isEqualTo(cordappVersionTestValue)
        Assertions.assertThat(deserializedException.data).isEqualTo(dataTestValue)
//        Assertions.assertThat(deserializedException.x).isEqualTo(xTestValue)
        Assertions.assertThat(deserializedException.y).isEqualTo(xTestValue)
        Assertions.assertThat(deserializedException).isInstanceOf(TemplateState::class.java)
    }

    /**
     * Write serialized object to resources folder
     */
    @Suppress("unused")
    fun <T : Any> saveSerializedObject(obj : T) = writeTestResource(SerializationOutput(testDefaultFactory()).serialize(obj))
}