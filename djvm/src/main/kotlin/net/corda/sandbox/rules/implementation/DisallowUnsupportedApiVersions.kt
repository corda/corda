package net.corda.sandbox.rules.implementation

import net.corda.sandbox.references.ClassRepresentation
import net.corda.sandbox.rules.ClassRule
import net.corda.sandbox.validation.RuleContext
import org.objectweb.asm.Opcodes.*

/**
 * Rule that checks for classes compiled for unsupported API versions.
 */
@Suppress("unused")
class DisallowUnsupportedApiVersions : ClassRule() {

    override fun validate(context: RuleContext, clazz: ClassRepresentation) = context.validate {
        fail("Unsupported API version '${versionString(clazz.apiVersion)}'") given
                (clazz.apiVersion !in supportedVersions)
    }

    companion object {

        private val supportedVersions = setOf(V1_5, V1_6, V1_7, V1_8)

        private val versionMap = mapOf(
                V1_1 to "1.1", V1_2 to "1.2", V1_3 to "1.3", V1_4 to "1.4",
                V1_5 to "1.5", V1_6 to "1.6", V1_7 to "1.7", V1_8 to "1.8",
                V9 to "9", V10 to "10"
        )

        private fun versionString(version: Int) = versionMap.getOrDefault(version, "unknown")

    }
}
