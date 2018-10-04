package net.corda.djvm.rewiring

import net.corda.djvm.SandboxConfiguration
import net.corda.djvm.analysis.AnalysisConfiguration
import net.corda.djvm.analysis.AnalysisContext
import net.corda.djvm.analysis.ClassAndMemberVisitor.Companion.API_VERSION
import net.corda.djvm.code.ClassMutator
import net.corda.djvm.code.EmitterModule
import net.corda.djvm.code.emptyAsNull
import net.corda.djvm.references.Member
import net.corda.djvm.utilities.loggerFor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor

/**
 * Functionality for rewriting parts of a class as it is being loaded.
 *
 * @property configuration The configuration of the sandbox.
 * @property classLoader The class loader used to load the classes that are to be rewritten.
 */
open class ClassRewriter(
        private val configuration: SandboxConfiguration,
        private val classLoader: ClassLoader
) {

    /**
     * Process class and allow user to rewrite parts/all of its content through provided hooks.
     *
     * @param reader The reader providing the byte code for the desired class.
     * @param context The context in which the class is being analyzed and processed.
     */
    fun rewrite(reader: ClassReader, context: AnalysisContext): ByteCode {
        logger.debug("Rewriting class {}...", reader.className)
        val writer = SandboxClassWriter(reader, classLoader)
        val analysisConfiguration = configuration.analysisConfiguration
        val classRemapper = SandboxClassRemapper(InterfaceStitcher(writer, analysisConfiguration), analysisConfiguration)
        val visitor = ClassMutator(
                classRemapper,
                analysisConfiguration,
                configuration.definitionProviders,
                configuration.emitters
        )
        visitor.analyze(reader, context, options = ClassReader.EXPAND_FRAMES)
        return ByteCode(writer.toByteArray(), visitor.hasBeenModified)
    }

    private companion object {
        private val logger = loggerFor<ClassRewriter>()
    }

    /**
     * Extra visitor that is applied after [SandboxRemapper]. This "stitches" the original
     * unmapped interface as a super-interface of the mapped version.
     */
    private class InterfaceStitcher(parent: ClassVisitor, private val configuration: AnalysisConfiguration)
        : ClassVisitor(API_VERSION, parent)
    {
        private val extraMethods = mutableListOf<Member>()

        override fun visit(version: Int, access: Int, className: String, signature: String?, superName: String?, interfaces: Array<String>?) {
            val stitchedInterfaces = configuration.stitchedInterfaces[className]?.let { methods ->
                extraMethods += methods
                arrayOf(*(interfaces ?: emptyArray()), configuration.classResolver.reverse(className))
            } ?: interfaces

            super.visit(version, access, className, signature, superName, stitchedInterfaces)
        }

        override fun visitEnd() {
            for (method in extraMethods) {
                method.apply {
                    visitMethod(access, memberName, signature, genericsDetails.emptyAsNull, exceptions.toTypedArray())?.also { mv ->
                        mv.visitCode()
                        EmitterModule(mv).writeByteCode(body)
                        mv.visitMaxs(-1, -1)
                        mv.visitEnd()
                    }
                }
            }
            extraMethods.clear()
            super.visitEnd()
        }
    }
}
