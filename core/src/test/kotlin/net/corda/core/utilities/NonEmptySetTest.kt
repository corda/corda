package net.corda.core.utilities

import com.google.common.collect.testing.SetTestSuiteBuilder
import com.google.common.collect.testing.TestIntegerSetGenerator
import com.google.common.collect.testing.features.CollectionFeature
import com.google.common.collect.testing.features.CollectionSize
import com.google.common.collect.testing.testers.CollectionAddAllTester
import com.google.common.collect.testing.testers.CollectionClearTester
import com.google.common.collect.testing.testers.CollectionRemoveAllTester
import com.google.common.collect.testing.testers.CollectionRetainAllTester
import junit.framework.TestSuite
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Suite
import kotlin.test.assertEquals

@RunWith(Suite::class)
@Suite.SuiteClasses(
        NonEmptySetTest.Guava::class,
        NonEmptySetTest.Remove::class,
        NonEmptySetTest.Serializer::class
)
class NonEmptySetTest {
    /**
     * Guava test suite generator for NonEmptySet.
     */
    class Guava {
        companion object {
            @JvmStatic
            fun suite(): TestSuite
                    = SetTestSuiteBuilder
                    .using(NonEmptySetGenerator())
                    .named("test NonEmptySet with several values")
                    .withFeatures(
                            CollectionSize.SEVERAL,
                            CollectionFeature.ALLOWS_NULL_VALUES,
                            CollectionFeature.FAILS_FAST_ON_CONCURRENT_MODIFICATION,
                            CollectionFeature.GENERAL_PURPOSE
                    )
                    // Kotlin throws the wrong exception in this cases
                    .suppressing(CollectionAddAllTester::class.java.getMethod("testAddAll_nullCollectionReference"))
                    // Disable tests that try to remove everything:
                    .suppressing(CollectionRemoveAllTester::class.java.getMethod("testRemoveAll_nullCollectionReferenceNonEmptySubject"))
                    .suppressing(CollectionClearTester::class.java.methods.toList())
                    .suppressing(CollectionRetainAllTester::class.java.methods.toList())
                    .createTestSuite()
        }

        /**
         * For some reason IntelliJ really wants to scan this class for tests and fail when
         * it doesn't find any. This stops that error from occurring.
         */
        @Test fun dummy() {
        }
    }

    /**
     * Test removal, which Guava's standard tests can't cover for us.
     */
    class Remove {
        @Test
        fun `construction`() {
            val expected = 17
            val basicSet = nonEmptySetOf(expected)
            val actual = basicSet.first()
            assertEquals(expected, actual)
        }

        @Test(expected = IllegalStateException::class)
        fun `remove sole element`() {
            val basicSet = nonEmptySetOf(-17)
            basicSet.remove(-17)
        }

        @Test
        fun `remove one of two elements`() {
            val basicSet = nonEmptySetOf(-17, 17)
            basicSet.remove(-17)
        }

        @Test
        fun `remove element which does not exist`() {
            val basicSet = nonEmptySetOf(-17)
            basicSet.remove(-5)
            assertEquals(1, basicSet.size)
        }

        @Test(expected = IllegalStateException::class)
        fun `remove via iterator`() {
            val basicSet = nonEmptySetOf(-17, 17)
            val iterator = basicSet.iterator()
            while (iterator.hasNext()) {
                iterator.remove()
            }
        }
    }

    /**
     * Test serialization/deserialization.
     */
    class Serializer {
        @Test
        fun `serialize deserialize`() {
            val expected: NonEmptySet<Int> = nonEmptySetOf(-17, 22, 17)
            val serialized = expected.serialize().bytes
            val actual = serialized.deserialize<NonEmptySet<Int>>()

            assertEquals(expected, actual)
        }
    }
}

/**
 * Generator of non empty set instances needed for testing.
 */
class NonEmptySetGenerator : TestIntegerSetGenerator() {
    override fun create(elements: Array<out Int?>?): NonEmptySet<Int?>? {
        val set = nonEmptySetOf(elements!!.first())
        set.addAll(elements.toList())
        return set
    }
}
