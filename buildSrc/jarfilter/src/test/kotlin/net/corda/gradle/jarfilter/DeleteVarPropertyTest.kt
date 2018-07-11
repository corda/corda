package net.corda.gradle.jarfilter

import net.corda.gradle.jarfilter.matcher.*
import net.corda.gradle.unwanted.HasUnwantedVar
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

class DeleteVarPropertyTest {
    companion object {
        private const val PROPERTY_CLASS = "net.corda.gradle.HasUnwantedVarPropertyForDelete"
        private const val GETTER_CLASS = "net.corda.gradle.HasUnwantedGetForDelete"
        private const val SETTER_CLASS = "net.corda.gradle.HasUnwantedSetForDelete"
        private const val JVM_FIELD_CLASS = "net.corda.gradle.HasVarJvmFieldForDelete"

        private val testProjectDir = TemporaryFolder()
        private val testProject = JarFilterProject(testProjectDir, "delete-var-property")
        private val unwantedVar = isProperty("unwantedVar", String::class)
        private val getUnwantedVar = isMethod("getUnwantedVar", String::class.java)
        private val setUnwantedVar = isMethod("setUnwantedVar", Void.TYPE, String::class.java)

        @ClassRule
        @JvmField
        val rules: TestRule = RuleChain
            .outerRule(testProjectDir)
            .around(testProject)
    }

    @Test
    fun deleteProperty() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<HasUnwantedVar>(PROPERTY_CLASS).apply {
                getDeclaredConstructor(String::class.java).newInstance(DEFAULT_MESSAGE).also { obj ->
                    assertEquals(DEFAULT_MESSAGE, obj.unwantedVar)
                    obj.unwantedVar = MESSAGE
                    assertEquals(MESSAGE, obj.unwantedVar)
                }
                assertFalse(getDeclaredField("unwantedVar").isAccessible)
                assertThat("unwantedVar not found", kotlin.declaredMemberProperties, hasItem(unwantedVar))
                assertThat("getUnwantedVar not found", kotlin.javaDeclaredMethods, hasItem(getUnwantedVar))
                assertThat("setUnwantedVar not found", kotlin.javaDeclaredMethods, hasItem(setUnwantedVar))
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<HasUnwantedVar>(PROPERTY_CLASS).apply {
                getDeclaredConstructor(String::class.java).newInstance(DEFAULT_MESSAGE).also { obj ->
                    assertFailsWith<AbstractMethodError> { obj.unwantedVar }
                    assertFailsWith<AbstractMethodError> { obj.unwantedVar = MESSAGE }
                }
                assertFailsWith<NoSuchFieldException> { getDeclaredField("unwantedVar") }
                assertThat("unwantedVar still exists", kotlin.declaredMemberProperties, not(hasItem(unwantedVar)))
                assertThat("getUnwantedVar still exists", kotlin.javaDeclaredMethods, not(hasItem(getUnwantedVar)))
                assertThat("setUnwantedVar still exists", kotlin.javaDeclaredMethods, not(hasItem(setUnwantedVar)))
            }
        }
    }

    @Test
    fun deleteGetter() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<HasUnwantedVar>(GETTER_CLASS).apply {
                getDeclaredConstructor(String::class.java).newInstance(MESSAGE).also { obj ->
                    assertEquals(MESSAGE, obj.unwantedVar)
                }
                assertFalse(getDeclaredField("unwantedVar").isAccessible)
                assertThat("getUnwantedVar not found", kotlin.javaDeclaredMethods, hasItem(getUnwantedVar))
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<HasUnwantedVar>(GETTER_CLASS).apply {
                getDeclaredConstructor(String::class.java).newInstance(MESSAGE).also { obj ->
                    assertFailsWith<AbstractMethodError> { obj.unwantedVar }
                }
                assertFalse(getDeclaredField("unwantedVar").isAccessible)
                assertThat("getUnwantedVar still exists", kotlin.javaDeclaredMethods, not(hasItem(getUnwantedVar)))
            }
        }
    }

    @Test
    fun deleteSetter() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<HasUnwantedVar>(SETTER_CLASS).apply {
                getConstructor(String::class.java).newInstance(DEFAULT_MESSAGE).also { obj ->
                    assertEquals(DEFAULT_MESSAGE, obj.unwantedVar)
                    obj.unwantedVar = MESSAGE
                    assertEquals(MESSAGE, obj.unwantedVar)
                }
                getDeclaredField("unwantedVar").also { field ->
                    assertFalse(field.isAccessible)
                }
                assertThat("setUnwantedVar not found", kotlin.javaDeclaredMethods, hasItem(setUnwantedVar))
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<HasUnwantedVar>(SETTER_CLASS).apply {
                getConstructor(String::class.java).newInstance(DEFAULT_MESSAGE).also { obj ->
                    assertEquals(DEFAULT_MESSAGE, obj.unwantedVar)
                    assertFailsWith<AbstractMethodError> { obj.unwantedVar = MESSAGE }
                }
                getDeclaredField("unwantedVar").also { field ->
                    assertFalse(field.isAccessible)
                }
                assertThat("setUnwantedVar still exists", kotlin.javaDeclaredMethods, not(hasItem(setUnwantedVar)))
            }
        }
    }

    @Test
    fun deleteJvmField() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<Any>(JVM_FIELD_CLASS).apply {
                val obj: Any = getDeclaredConstructor(String::class.java).newInstance(DEFAULT_MESSAGE)
                getDeclaredField("unwantedVar").also { field ->
                    assertEquals(DEFAULT_MESSAGE, field.get(obj))
                    field.set(obj, MESSAGE)
                    assertEquals(MESSAGE, field.get(obj))
                }
                assertThat("unwantedVar not found", kotlin.declaredMemberProperties, hasItem(unwantedVar))
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<Any>(JVM_FIELD_CLASS).apply {
                assertNotNull(getDeclaredConstructor(String::class.java).newInstance(DEFAULT_MESSAGE))
                assertFailsWith<NoSuchFieldException> { getDeclaredField("unwantedVar") }
                assertThat("unwantedVar still exists", kotlin.declaredMemberProperties, not(hasItem(unwantedVar)))
            }
        }
    }
}