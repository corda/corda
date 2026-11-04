package net.corda.nodeapi.internal.persistence.factory

import jakarta.persistence.Entity
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class JavaxPersistenceDetectionTest {
    @Entity
    class JakartaEntity

    @Test(timeout = 300_000)
    fun `a class built against jakarta persistence is not flagged`() {
        val bytes = JakartaEntity::class.java.classLoader
                .getResourceAsStream(JakartaEntity::class.java.name.replace('.', '/') + ".class")!!.use { it.readBytes() }
        assertThat(referencesJavaxPersistence(bytes)).isFalse()
    }

    @Test(timeout = 300_000)
    fun `a class file that refers to javax persistence is flagged`() {
        // The constant pool of a class annotated with javax.persistence.Entity contains this descriptor.
        val bytes = "Êþº¾...Ljavax/persistence/Entity;...".toByteArray(Charsets.ISO_8859_1)
        assertThat(referencesJavaxPersistence(bytes)).isTrue()
    }

    @Test(timeout = 300_000)
    fun `the message says the class uses javax persistence and that jakarta persistence is required`() {
        assertThat(builtForJavaxPersistenceMessage("com.example.MyState", "com.example.MySchemaV1"))
                .contains("The class com.example.MyState of the schema com.example.MySchemaV1 was built for Corda 4.14 or earlier")
                .contains("uses the javax.persistence annotations")
                .contains("requires jakarta.persistence")
    }
}
