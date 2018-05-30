package net.corda.gradle.jarfilter

import net.corda.gradle.jarfilter.matcher.*
import net.corda.gradle.unwanted.HasUnwantedVal
import org.hamcrest.core.IsCollectionContaining.*
import org.hamcrest.core.IsNot.*
import org.junit.Assert.*
import org.junit.ClassRule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TemporaryFolder
import org.junit.rules.TestRule
import kotlin.reflect.full.declaredMemberProperties
import kotlin.test.assertFailsWith

class DeleteValPropertyTest {
    companion object {
        private const val PROPERTY_CLASS = "net.corda.gradle.HasValPropertyForDelete"
        private const val GETTER_CLASS = "net.corda.gradle.HasValGetterForDelete"
        private const val JVM_FIELD_CLASS = "net.corda.gradle.HasValJvmFieldForDelete"

        private val testProjectDir = TemporaryFolder()
        private val testProject = JarFilterProject(testProjectDir, "delete-val-property")
        private val unwantedVal = isProperty("unwantedVal", String::class)
        private val getUnwantedVal = isMethod("getUnwantedVal", String::class.java)

        @ClassRule
        @JvmField
        val rules: TestRule = RuleChain
            .outerRule(testProjectDir)
            .around(testProject)
    }

    @Test
    fun deleteProperty() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<HasUnwantedVal>(PROPERTY_CLASS).apply {
                getDeclaredConstructor(String::class.java).newInstance(MESSAGE).also { obj ->
                    assertEquals(MESSAGE, obj.unwantedVal)
                }
                assertFalse(getDeclaredField("unwantedVal").isAccessible)
                assertThat("unwantedVal not found", kotlin.declaredMemberProperties, hasItem(unwantedVal))
                assertThat("getUnwantedVal not found", kotlin.javaDeclaredMethods, hasItem(getUnwantedVal))
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<HasUnwantedVal>(PROPERTY_CLASS).apply {
                getDeclaredConstructor(String::class.java).newInstance(MESSAGE).also { obj ->
                    assertFailsWith<AbstractMethodError> { obj.unwantedVal }
                }
                assertFailsWith<NoSuchFieldException> { getDeclaredField("unwantedVal") }
                assertThat("unwantedVal still exists", kotlin.declaredMemberProperties, not(hasItem(unwantedVal)))
                assertThat("getUnwantedVal still exists", kotlin.javaDeclaredMethods, not(hasItem(getUnwantedVal)))
            }
        }
    }

    @Test
    fun deleteGetter() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<HasUnwantedVal>(GETTER_CLASS).apply {
                getDeclaredConstructor(String::class.java).newInstance(MESSAGE).also { obj ->
                    assertEquals(MESSAGE, obj.unwantedVal)
                }
                assertFalse(getDeclaredField("unwantedVal").isAccessible)
                assertThat("getUnwantedVal not found", kotlin.javaDeclaredMethods, hasItem(getUnwantedVal))
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<HasUnwantedVal>(GETTER_CLASS).apply {
                getDeclaredConstructor(String::class.java).newInstance(MESSAGE).also { obj ->
                    assertFailsWith<AbstractMethodError> { obj.unwantedVal }
                }
                assertFalse(getDeclaredField("unwantedVal").isAccessible)
                assertThat("getUnwantedVal still exists", kotlin.javaDeclaredMethods, not(hasItem(getUnwantedVal)))
            }
        }
    }

    @Test
    fun deleteJvmField() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<Any>(JVM_FIELD_CLASS).apply {
                val obj = getDeclaredConstructor(String::class.java).newInstance(MESSAGE)
                getDeclaredField("unwantedVal").also { field ->
                    assertEquals(MESSAGE, field.get(obj))
                }
                assertThat("unwantedVal not found", kotlin.declaredMemberProperties, hasItem(unwantedVal))
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<Any>(JVM_FIELD_CLASS).apply {
                assertNotNull(getDeclaredConstructor(String::class.java).newInstance(MESSAGE))
                assertFailsWith<NoSuchFieldException> { getDeclaredField("unwantedVal") }
                assertThat("unwantedVal still exists", kotlin.declaredMemberProperties, not(hasItem(unwantedVal)))
            }
        }
    }
}
