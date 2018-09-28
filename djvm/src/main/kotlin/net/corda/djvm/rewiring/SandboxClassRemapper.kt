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

        private fun mapperFor(owner: String): MethodVisitor {
            return if (owner in configuration.pinnedClasses) nonmapper else mv
        }

        override fun visitMethodInsn(opcode: Int, owner: String, name: String, descriptor: String, isInterface: Boolean) {
            return mapperFor(owner).visitMethodInsn(opcode, owner, name, descriptor, isInterface)
        }

        override fun visitFieldInsn(opcode: Int, owner: String, name: String, descriptor: String) {
            return mapperFor(owner).visitFieldInsn(opcode, owner, name, descriptor)
        }
    }
}