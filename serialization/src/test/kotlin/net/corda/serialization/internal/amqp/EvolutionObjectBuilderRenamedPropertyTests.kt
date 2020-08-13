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

class EvolutionObjectBuilderRenamedPropertyTests
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
     *
     * This is the original class definition in object evolution.
     */
//    @BelongsToContract(TemplateContract::class)
//    data class TemplateState(val cordappVersion: Int, val data: String, val x : Int?, override val participants: List<AbstractParty> = listOf()) : ContractState

    /**
     * Step 2
     *
     * This is an intermediate class definition in object evolution.
     * The y property has been added and a constructor copies the value of x into y. x is now set to null by the constructor.
     */
//    @BelongsToContract(TemplateContract::class)
//    data class TemplateState(val cordappVersion: Int, val data: String, val x : Int?, val y : String?, override val participants: List<AbstractParty> = listOf()) : ContractState {
//        @DeprecatedConstructorForDeserialization(1)
//        constructor(cordappVersion: Int, data : String, x : Int?, participants: List<AbstractParty>)
//                : this(cordappVersion, data, null, x?.toString(), participants)
//    }

    /**
     * Step 3
     *
     * This is the final class definition in object evolution.
     * The x property has been removed but the constructor that copies values of x into y still exists. We expect previous versions of this
     * object to pass the value of x to the constructor when deserialized.
     */
    @BelongsToContract(TemplateContract::class)
    data class TemplateState(val cordappVersion: Int, val data: String, val y : String?, override val participants: List<AbstractParty> = listOf()) : ContractState {
        @DeprecatedConstructorForDeserialization(1)
        constructor(cordappVersion: Int, data : String, x : Int?, participants: List<AbstractParty>) : this(cordappVersion, data, x?.toString(), participants)
    }

    @Test(timeout=300_000)
    fun `Step 1 to Step 3`() {

        // The next two commented lines are how the serialized data is generated. To regenerate the data, uncomment these along
        // with the correct version of the class and rerun the test. This will generate a new file in the project resources.

//        val step1 = TemplateState(cordappVersionTestValue, dataTestValue, xTestValue)
//        saveSerializedObject(step1)

        // serialization/src/test/resources/net/corda/serialization/internal/amqp/EvolutionObjectBuilderRenamedPropertyTests.Step1
        val bytes = this::class.java.getResource("EvolutionObjectBuilderRenamedPropertyTests.Step1").readBytes()

        val serializerFactory: SerializerFactory = testDefaultFactory()
        val deserializedObject = DeserializationInput(serializerFactory)
                .deserialize(SerializedBytes<TemplateState>(bytes))

        Assertions.assertThat(deserializedObject.cordappVersion).isEqualTo(cordappVersionTestValue)
        Assertions.assertThat(deserializedObject.data).isEqualTo(dataTestValue)
//        Assertions.assertThat(deserializedObject.x).isEqualTo(xTestValue)
        Assertions.assertThat(deserializedObject.y).isEqualTo(xTestValue.toString())
        Assertions.assertThat(deserializedObject).isInstanceOf(TemplateState::class.java)
    }

    /**
     * Write serialized object to resources folder
     */
    @Suppress("unused")
    fun <T : Any> saveSerializedObject(obj : T) = writeTestResource(SerializationOutput(testDefaultFactory()).serialize(obj))
}