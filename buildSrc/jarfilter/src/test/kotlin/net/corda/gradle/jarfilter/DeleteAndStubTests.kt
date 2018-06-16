package net.corda.gradle.jarfilter

import net.corda.gradle.jarfilter.matcher.*
import net.corda.gradle.unwanted.*
import org.assertj.core.api.Assertions.*
import org.hamcrest.core.IsCollectionContaining.*
import org.hamcrest.core.IsNot.*
import org.junit.Assert.*
import org.junit.ClassRule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TemporaryFolder
import org.junit.rules.TestRule
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.full.declaredMemberProperties
import kotlin.test.assertFailsWith

class DeleteAndStubTests {
    companion object {
        private const val VAR_PROPERTY_CLASS = "net.corda.gradle.HasVarPropertyForDeleteAndStub"
        private const val VAL_PROPERTY_CLASS = "net.corda.gradle.HasValPropertyForDeleteAndStub"
        private const val DELETED_FUN_CLASS = "net.corda.gradle.DeletedFunctionInsideStubbed"
        private const val DELETED_VAR_CLASS = "net.corda.gradle.DeletedVarInsideStubbed"
        private const val DELETED_VAL_CLASS = "net.corda.gradle.DeletedValInsideStubbed"
        private const val DELETED_PKG_CLASS = "net.corda.gradle.DeletePackageWithStubbed"

        private val testProjectDir = TemporaryFolder()
        private val testProject = JarFilterProject(testProjectDir, "delete-and-stub")
        private val stringVal = isProperty("stringVal", String::class)
        private val longVar = isProperty("longVar", Long::class)
        private val getStringVal = isMethod("getStringVal", String::class.java)
        private val getLongVar = isMethod("getLongVar", Long::class.java)
        private val setLongVar = isMethod("setLongVar", Void.TYPE, Long::class.java)
        private val stringData = isFunction("stringData", String::class)
        private val unwantedFun = isFunction("unwantedFun", String::class, String::class)
        private val unwantedVar = isProperty("unwantedVar", String::class)
        private val unwantedVal = isProperty("unwantedVal", String::class)
        private val stringDataJava = isMethod("stringData", String::class.java)
        private val getUnwantedVal = isMethod("getUnwantedVal", String::class.java)
        private val getUnwantedVar = isMethod("getUnwantedVar", String::class.java)
        private val setUnwantedVar = isMethod("setUnwantedVar", Void.TYPE, String::class.java)

        @ClassRule
        @JvmField
        val rules: TestRule = RuleChain
            .outerRule(testProjectDir)
            .around(testProject)
    }

    @Test
    fun deleteValProperty() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<HasStringVal>(VAL_PROPERTY_CLASS).apply {
                getDeclaredConstructor(String::class.java).newInstance(MESSAGE).also { obj ->
                    assertEquals(MESSAGE, obj.stringVal)
                }
                assertThat("stringVal not found", kotlin.declaredMemberProperties, hasItem(stringVal))
                assertThat("getStringVal() not found", kotlin.javaDeclaredMethods, hasItem(getStringVal))
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<HasStringVal>(VAL_PROPERTY_CLASS).apply {
                assertNotNull(getDeclaredConstructor(String::class.java).newInstance(MESSAGE))
                assertThat("stringVal still exists", kotlin.declaredMemberProperties, not(hasItem(stringVal)))
                assertThat("getStringVal() still exists", kotlin.javaDeclaredMethods, not(hasItem(getStringVal)))
            }
        }
    }

    @Test
    fun deleteVarProperty() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<HasLongVar>(VAR_PROPERTY_CLASS).apply {
                getDeclaredConstructor(Long::class.java).newInstance(BIG_NUMBER).also { obj ->
                    assertEquals(BIG_NUMBER, obj.longVar)
                }
                assertThat("longVar not found", kotlin.declaredMemberProperties, hasItem(longVar))
                assertThat("getLongVar() not found", kotlin.javaDeclaredMethods, hasItem(getLongVar))
                assertThat("setLongVar() not found", kotlin.javaDeclaredMethods, hasItem(setLongVar))
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<HasLongVar>(VAR_PROPERTY_CLASS).apply {
                assertNotNull(getDeclaredConstructor(Long::class.java).newInstance(BIG_NUMBER))
                assertThat("longVar still exists", kotlin.declaredMemberProperties, not(hasItem(longVar)))
                assertThat("getLongVar() still exists", kotlin.javaDeclaredMethods, not(hasItem(getLongVar)))
                assertThat("setLongVar() still exists", kotlin.javaDeclaredMethods, not(hasItem(setLongVar)))
            }
        }
    }

    @Test
    fun deletedFunctionInsideStubbed() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<HasString>(DELETED_FUN_CLASS).apply {
                getDeclaredConstructor(String::class.java).newInstance(DEFAULT_MESSAGE).also { obj ->
                    assertEquals(DEFAULT_MESSAGE, obj.stringData())
                    assertEquals(MESSAGE, (obj as HasUnwantedFun).unwantedFun(MESSAGE))
                }
                assertThat("unwantedFun not found", kotlin.declaredMemberFunctions, hasItem(unwantedFun))
                assertThat("stringData() not found", kotlin.declaredMemberFunctions, hasItem(stringData))
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<HasString>(DELETED_FUN_CLASS).apply {
                getDeclaredConstructor(String::class.java).newInstance(DEFAULT_MESSAGE).also { obj ->
                    assertFailsWith<UnsupportedOperationException> { obj.stringData() }.also { ex ->
                        assertThat(ex).hasMessage("Method has been deleted")
                    }
                    assertFailsWith<AbstractMethodError> { (obj as HasUnwantedFun).unwantedFun(MESSAGE) }
                }
                assertThat("unwantedFun still exists", kotlin.declaredMemberFunctions, not(hasItem(unwantedFun)))
                assertThat("stringData() not found", kotlin.declaredMemberFunctions, hasItem(stringData))
            }
        }
    }

    @Test
    fun deletedVarInsideStubbed() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<HasString>(DELETED_VAR_CLASS).apply {
                getDeclaredConstructor(String::class.java).newInstance(DEFAULT_MESSAGE).also { obj ->
                    assertEquals(DEFAULT_MESSAGE, obj.stringData())
                    (obj as HasUnwantedVar).also {
                        assertEquals(DEFAULT_MESSAGE, it.unwantedVar)
                        it.unwantedVar = MESSAGE
                        assertEquals(MESSAGE, it.unwantedVar)
                    }
                }
                assertThat("unwantedVar not found", kotlin.declaredMemberProperties, hasItem(unwantedVar))
                assertThat("stringData() not found", kotlin.declaredMemberFunctions, hasItem(stringData))
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<HasString>(DELETED_VAR_CLASS).apply {
                assertNotNull(getDeclaredConstructor(String::class.java).newInstance(MESSAGE))
                assertThat("unwantedVar still exists", kotlin.declaredMemberProperties, not(hasItem(unwantedVar)))
                assertThat("getUnwantedVar() still exists", kotlin.javaDeclaredMethods, not(hasItem(getUnwantedVar)))
                assertThat("setUnwantedVar() still exists", kotlin.javaDeclaredMethods, not(hasItem(setUnwantedVar)))
                assertThat("stringData() not found", kotlin.declaredMemberFunctions, hasItem(stringData))
                assertThat("stringData() not found", kotlin.javaDeclaredMethods, hasItem(stringDataJava))
            }
        }
    }

    @Test
    fun deletedValInsideStubbed() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<HasString>(DELETED_VAL_CLASS).apply {
                getDeclaredConstructor(String::class.java).newInstance(MESSAGE).also { obj ->
                    assertEquals(MESSAGE, obj.stringData())
                    assertEquals(MESSAGE, (obj as HasUnwantedVal).unwantedVal)
                }
                assertThat("unwantedVal not found", kotlin.declaredMemberProperties, hasItem(unwantedVal))
                assertThat("stringData() not found", kotlin.declaredMemberFunctions, hasItem(stringData))
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            cl.load<HasString>(DELETED_VAL_CLASS).apply {
                assertNotNull(getDeclaredConstructor(String::class.java).newInstance(MESSAGE))
                assertThat("unwantedVal still exists", kotlin.declaredMemberProperties, not(hasItem(unwantedVal)))
                assertThat("getUnwantedVal() still exists", kotlin.javaDeclaredMethods, not(hasItem(getUnwantedVal)))
                assertThat("stringData() not found", kotlin.declaredMemberFunctions, hasItem(stringData))
                assertThat("stringData() not found", kotlin.javaDeclaredMethods, hasItem(stringDataJava))
            }
        }
    }

    @Test
    fun deletePackageWithStubbed() {
        classLoaderFor(testProject.sourceJar).use { cl ->
            cl.load<Any>(DELETED_PKG_CLASS).apply {
                getDeclaredMethod("stubbed", String::class.java).also { method ->
                    assertEquals("[$MESSAGE]", method.invoke(null, MESSAGE))
                }
            }
        }

        classLoaderFor(testProject.filteredJar).use { cl ->
            assertFailsWith<ClassNotFoundException> { cl.load<Any>(DELETED_PKG_CLASS) }
        }
    }
}