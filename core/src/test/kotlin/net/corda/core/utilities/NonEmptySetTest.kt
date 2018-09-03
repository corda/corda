package net.corda.core.utilities

import com.google.common.collect.testing.SetTestSuiteBuilder
import com.google.common.collect.testing.TestIntegerSetGenerator
import com.google.common.collect.testing.features.CollectionFeature
import com.google.common.collect.testing.features.CollectionSize
import junit.framework.TestSuite
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.testing.core.SerializationEnvironmentRule
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(Suite::class)
@Suite.SuiteClasses(
        NonEmptySetTest.Guava::class,
        NonEmptySetTest.General::class
)
class NonEmptySetTest {
    object Guava {
        @JvmStatic
        fun suite(): TestSuite {
            return SetTestSuiteBuilder
                    .using(NonEmptySetGenerator)
                    .named("Guava test suite")
                    .withFeatures(
                            CollectionSize.SEVERAL,
                            CollectionFeature.ALLOWS_NULL_VALUES,
                            CollectionFeature.KNOWN_ORDER
                    )
                    .createTestSuite()
        }
    }

    class General {
        @Rule
        @JvmField
        val testSerialization = SerializationEnvironmentRule()

        @Test
        fun `copyOf - empty source`() {
            assertThatThrownBy { NonEmptySet.copyOf(HashSet<Int>()) }.isInstanceOf(IllegalArgumentException::class.java)
        }

        @Test
        fun head() {
            assertThat(NonEmptySet.of(1, 2).head()).isEqualTo(1)
        }

        @Test
        fun `serialize deserialize`() {
            val original = NonEmptySet.of(-17, 22, 17)
            val copy = original.serialize().deserialize()
            assertThat(copy).isEqualTo(original).isNotSameAs(original)
        }
    }

    private object NonEmptySetGenerator : TestIntegerSetGenerator() {
        override fun create(elements: Array<out Int?>): NonEmptySet<Int?> = NonEmptySet.copyOf(elements.asList())
    }
}
