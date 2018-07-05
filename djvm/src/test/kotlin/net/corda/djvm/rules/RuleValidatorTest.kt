package net.corda.djvm.rules

import foo.bar.sandbox.Callable
import net.corda.djvm.TestBase
import net.corda.djvm.assertions.AssertionExtensions.assertThat
import net.corda.djvm.messages.Severity
import org.junit.Test
import org.objectweb.asm.Opcodes
import sandbox.greymalkin.StringReturner

class RuleValidatorTest : TestBase() {

    @Test
    fun `can validate empty class`() = validate<Empty>(Severity.TRACE) { context ->
        assertThat(context.classes)
                .hasCount(1)
                .hasClass(nameOf<Empty>())
                .withMemberCount(1)
                .withMember("<init>", "()V")
        assertThat(context.messages)
                .hasErrorCount(0)
                .hasWarningCount(0)
                .hasInfoCount(0)
                .hasTraceCount(2)
                .withMessage("Synchronization specifier will be ignored")
                .withMessage("Strict floating-point arithmetic will be applied")
    }

    class Empty

    @Test
    fun `can validate class`() = validate<A>(Severity.TRACE) { context ->
        assertThat(context.classes)
                .hasCount(1)
                .hasClass(nameOf<A>())
                .withInterfaceCount(1)
                .withInterface(nameOf<Callable>())
                .withMemberCount(2)
                .withMember("<init>", "()V")
                .withMember("call", "()V")
                .withNoAccessFlag(Opcodes.ACC_STRICT)
        assertThat(context.messages)
                .hasErrorCount(0)
                .hasWarningCount(0)
                .hasInfoCount(1)
                .withMessage("Stripped monitoring instruction")
                .hasTraceCount(4)
                .withMessage("Synchronization specifier will be ignored")
                .withMessage("Strict floating-point arithmetic will be applied")
    }

    class A : Callable {
        override fun call() {
            synchronized(this) { }
        }
    }

    @Test
    fun `can reject invalid class from sandbox package`() = validate<StringReturner> { context ->
        assertThat(context.classes)
                .hasCount(1)
        assertThat(context.messages)
                .hasErrorCount(1)
                .withMessage("Cannot load class explicitly defined in the 'sandbox' root package; " +
                        "sandbox/greymalkin/StringReturner")
    }

}
