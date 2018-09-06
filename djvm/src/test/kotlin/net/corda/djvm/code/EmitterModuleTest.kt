package net.corda.djvm.code

import net.corda.djvm.TestBase
import net.corda.djvm.analysis.ClassAndMemberVisitor
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes.NEW

class EmitterModuleTest : TestBase() {

    @Test
    fun `can emit code to method body`() {
        var hasEmittedTypeInstruction = false
        val methodVisitor = object : MethodVisitor(ClassAndMemberVisitor.API_VERSION) {
            override fun visitTypeInsn(opcode: Int, type: String?) {
                if (opcode == NEW && type == java.lang.String::class.java.name) {
                    hasEmittedTypeInstruction = true
                }
            }
        }
        val visitor = object : ClassVisitor(ClassAndMemberVisitor.API_VERSION) {
            override fun visitMethod(
                    access: Int, name: String?, descriptor: String?, signature: String?, exceptions: Array<out String>?
            ): MethodVisitor {
                return methodVisitor
            }
        }
        val emitter = object : Emitter {
            override fun emit(context: EmitterContext, instruction: Instruction) = context.emit {
                @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
                new<java.lang.String>()
            }

        }
        val context = context
        val mutator = ClassMutator(visitor, configuration, emitters = listOf(emitter))
        mutator.analyze<TestClass>(context)
        assertThat(hasEmittedTypeInstruction).isTrue()
    }

    class TestClass {
        fun foo() {}
    }

}
