package net.corda.djvm.validation

import net.corda.djvm.analysis.AnalysisConfiguration
import net.corda.djvm.analysis.ClassAndMemberVisitor
import net.corda.djvm.code.EmitterModule
import net.corda.djvm.code.Instruction
import net.corda.djvm.references.ClassRepresentation
import net.corda.djvm.references.Member
import net.corda.djvm.rules.ClassRule
import net.corda.djvm.rules.InstructionRule
import net.corda.djvm.rules.MemberRule
import net.corda.djvm.rules.Rule
import net.corda.djvm.utilities.Processor
import org.objectweb.asm.ClassVisitor

/**
 * Helper class for validating a set of rules for a class or set of classes.
 *
 * @property rules A set of rules to validate for provided classes.
 * @param classVisitor Class visitor to use when traversing the structure of classes.
 */
class RuleValidator(
        private val rules: List<Rule> = emptyList(),
        configuration: AnalysisConfiguration = AnalysisConfiguration(),
        classVisitor: ClassVisitor? = null
) : ClassAndMemberVisitor(classVisitor, configuration = configuration) {

    /**
     * Apply the set of rules to the traversed class and record any violations.
     */
    override fun visitClass(clazz: ClassRepresentation): ClassRepresentation {
        if (shouldBeProcessed(clazz.name)) {
            val context = RuleContext(currentAnalysisContext())
            Processor.processEntriesOfType<ClassRule>(rules, analysisContext.messages) {
                it.validate(context, clazz)
            }
        }
        return super.visitClass(clazz)
    }

    /**
     * Apply the set of rules to the traversed method and record any violations.
     */
    override fun visitMethod(clazz: ClassRepresentation, method: Member): Member {
        if (shouldBeProcessed(clazz.name) && shouldBeProcessed(method.reference)) {
            val context = RuleContext(currentAnalysisContext())
            Processor.processEntriesOfType<MemberRule>(rules, analysisContext.messages) {
                it.validate(context, method)
            }
        }
        return super.visitMethod(clazz, method)
    }

    /**
     * Apply the set of rules to the traversed field and record any violations.
     */
    override fun visitField(clazz: ClassRepresentation, field: Member): Member {
        if (shouldBeProcessed(clazz.name) && shouldBeProcessed(field.reference)) {
            val context = RuleContext(currentAnalysisContext())
            Processor.processEntriesOfType<MemberRule>(rules, analysisContext.messages) {
                it.validate(context, field)
            }
        }
        return super.visitField(clazz, field)
    }

    /**
     * Apply the set of rules to the traversed instruction and record any violations.
     */
    override fun visitInstruction(method: Member, emitter: EmitterModule, instruction: Instruction) {
        if (shouldBeProcessed(method.className) && shouldBeProcessed(method.reference)) {
            val context = RuleContext(currentAnalysisContext())
            Processor.processEntriesOfType<InstructionRule>(rules, analysisContext.messages) {
                it.validate(context, instruction)
            }
        }
        super.visitInstruction(method, emitter, instruction)
    }

}
