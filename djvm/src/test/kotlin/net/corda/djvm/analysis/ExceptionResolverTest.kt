package net.corda.djvm.analysis

import net.corda.djvm.analysis.AnalysisConfiguration.Companion.JVM_EXCEPTIONS
import net.corda.djvm.analysis.AnalysisConfiguration.Companion.SANDBOX_PREFIX
import net.corda.djvm.code.ruleViolationError
import net.corda.djvm.code.thresholdViolationError
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.objectweb.asm.Type

class ExceptionResolverTest {
    private companion object {
        private val Class<*>.sandboxed: String get() = SANDBOX_PREFIX + unsandboxed
        private val Class<*>.unsandboxed: String get() = Type.getInternalName(this)

        inline fun <reified T> sandboxed(): String = T::class.java.sandboxed
        inline fun <reified T> unsandboxed(): String = T::class.java.unsandboxed
    }

    private val exceptionResolver = ExceptionResolver(
        jvmExceptionClasses = JVM_EXCEPTIONS,
        pinnedClasses = setOf(ruleViolationError, thresholdViolationError),
        sandboxPrefix = SANDBOX_PREFIX
    )

    open class ExampleException : Exception()
}