package net.corda.sandbox.code

import net.corda.sandbox.analysis.AnalysisConfiguration
import net.corda.sandbox.analysis.ClassAndMemberVisitor
import net.corda.sandbox.references.Class
import net.corda.sandbox.references.Member
import net.corda.sandbox.utilities.Processor
import org.objectweb.asm.ClassVisitor

/**
 * Helper class for applying a set of definition providers and emitters to a class or set of classes.
 *
 * @param classVisitor Class visitor to use when traversing the structure of classes.
 * @property definitionProviders A set of providers used to update the name or meta-data of classes and members.
 * @property emitters A set of code emitters used to modify and instrument method bodies.
 */
class ClassMutator(
        classVisitor: ClassVisitor,
        private val configuration: AnalysisConfiguration,
        private val definitionProviders: List<DefinitionProvider> = emptyList(),
        private val emitters: List<Emitter> = emptyList()
) : ClassAndMemberVisitor(classVisitor, configuration = configuration) {

    /**
     * Tracks whether any modifications have been applied to any of the processed class(es) and pertinent members.
     */
    var hasBeenModified: Boolean = false
        private set

    /**
     * Apply definition providers to a class. This can be used to update the name or definition (pertinent meta-data)
     * of the class itself.
     */
    override fun visitClass(clazz: Class): Class {
        var resultingClass = clazz
        Processor.processEntriesOfType<ClassDefinitionProvider>(definitionProviders, analysisContext.messages) {
            resultingClass = it.define(currentAnalysisContext(), resultingClass)
        }
        if (clazz != resultingClass) {
            hasBeenModified = true
        }
        return super.visitClass(resultingClass)
    }

    /**
     * Apply definition providers to a method. This can be used to update the name or definition (pertinent meta-data)
     * of a class member.
     */
    override fun visitMethod(clazz: Class, method: Member): Member {
        var resultingMethod = method
        Processor.processEntriesOfType<MemberDefinitionProvider>(definitionProviders, analysisContext.messages) {
            resultingMethod = it.define(currentAnalysisContext(), resultingMethod)
        }
        if (method != resultingMethod) {
            hasBeenModified = true
        }
        return super.visitMethod(clazz, resultingMethod)
    }

    /**
     * Apply definition providers to a field. This can be used to update the name or definition (pertinent meta-data)
     * of a class member.
     */
    override fun visitField(clazz: Class, field: Member): Member {
        var resultingField = field
        Processor.processEntriesOfType<MemberDefinitionProvider>(definitionProviders, analysisContext.messages) {
            resultingField = it.define(currentAnalysisContext(), resultingField)
        }
        if (field != resultingField) {
            hasBeenModified = true
        }
        return super.visitField(clazz, resultingField)
    }

    /**
     * Apply emitters to an instruction. This can be used to instrument a part of the code block, change behaviour of
     * an existing instruction, or strip it out completely.
     */
    override fun visitInstruction(method: Member, emitter: EmitterModule, instruction: Instruction) {
        val context = EmitterContext(currentAnalysisContext(), configuration, emitter)
        Processor.processEntriesOfType<Emitter>(emitters, analysisContext.messages) {
            it.emit(context, instruction)
        }
        if (!emitter.emitDefaultInstruction || emitter.hasEmittedCustomCode) {
            hasBeenModified = true
        }
        super.visitInstruction(method, emitter, instruction)
    }

}
