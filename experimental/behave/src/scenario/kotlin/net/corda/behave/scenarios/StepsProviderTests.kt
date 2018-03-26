package net.corda.behave.scenarios

import org.junit.Test
import org.reflections.Reflections
import kotlin.test.assertEquals

class StepsProviderTests {

    @Test
    fun `module can discover steps providers`() {
        val reflections = Reflections("net.corda.behave")

        val foundProviders = mutableListOf<String>()
        reflections.getSubTypesOf(StepsProvider::class.java).forEach {
            foundProviders.add(it.simpleName)
            val instance = it.newInstance()
            val name = instance.name
            val stepsDefinition = instance.stepsDefinition
            assert(it.simpleName.contains(name))
            stepsDefinition {
                // Blah
            }
        }

        assertEquals(2, foundProviders.size)
        assert(foundProviders.contains("FooStepsProvider"))
        assert(foundProviders.contains("BarStepsProvider"))
    }

    class FooStepsProvider : StepsProvider {

        override val name: String
            get() = "Foo"

        override val stepsDefinition: (StepsBlock) -> Unit
            get() = {}

    }

}