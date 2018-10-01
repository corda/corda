package net.corda.djvm.rewiring

import net.corda.djvm.analysis.AnalysisConfiguration
import net.corda.djvm.analysis.ClassAndMemberVisitor.Companion.API_VERSION
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.commons.ClassRemapper

class SandboxClassRemapper(cv: ClassVisitor, private val configuration: AnalysisConfiguration)
    : ClassRemapper(cv, SandboxRemapper(configuration.classResolver)
) {
    override fun createMethodRemapper(mv: MethodVisitor): MethodVisitor {
        return MethodRemapperWithPinning(mv, super.createMethodRemapper(mv))
    }

    /**
     * Do not attempt to remap references to methods and fields on pinned classes.
     * For example, the methods on [RuntimeCostAccounter] really DO use [java.lang.String]
     * rather than [sandbox.java.lang.String].
     */
    private inner class MethodRemapperWithPinning(private val nonmapper: MethodVisitor, remapper: MethodVisitor)
        : MethodVisitor(API_VERSION, remapper) {

        private fun mapperFor(element: Element): MethodVisitor {
            return if (configuration.isPinnedClass(element.owner) || configuration.isTemplateClass(element.owner) || element in UNMAPPED) {
                nonmapper
            } else {
                mv
            }
        }

        override fun visitMethodInsn(opcode: Int, owner: String, name: String, descriptor: String, isInterface: Boolean) {
            val method = Element(owner, name, descriptor)
            return mapperFor(method).visitMethodInsn(opcode, owner, name, descriptor, isInterface)
        }

        override fun visitFieldInsn(opcode: Int, owner: String, name: String, descriptor: String) {
            val field = Element(owner, name, descriptor)
            return mapperFor(field).visitFieldInsn(opcode, owner, name, descriptor)
        }
    }

    private data class Element(val owner: String, val name: String, val descriptor: String)

    private companion object {
        private val UNMAPPED = setOf(
            Element("java/lang/System", "arraycopy", "(Ljava/lang/Object;ILjava/lang/Object;II)V"),
            Element("java/lang/Object", "toString", "()Ljava/lang/String;")
        )
    }
}