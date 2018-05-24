package net.corda.sandbox.validation

import net.corda.sandbox.analysis.AnalysisConfiguration
import net.corda.sandbox.analysis.AnalysisContext
import net.corda.sandbox.analysis.ClassAndMemberVisitor
import net.corda.sandbox.execution.SandboxedRunnable
import net.corda.sandbox.formatting.MemberFormatter
import net.corda.sandbox.messages.Message
import net.corda.sandbox.messages.Severity
import net.corda.sandbox.references.*
import net.corda.sandbox.rewiring.SandboxClassLoadingException

/**
 * Module used to validate all traversable references before instantiating and executing a [SandboxedRunnable].
 *
 * @param configuration The analysis configuration to use for the validation.
 * @property memberFormatter Module with functionality for formatting class members.
 */
class ReferenceValidator(
        private val configuration: AnalysisConfiguration,
        private val memberFormatter: MemberFormatter = MemberFormatter()
) {

    /**
     * Container holding the current state of the validation.
     *
     * @property context The context in which references are to be validated.
     * @property analyzer Underlying analyzer used for processing classes.
     */
    private class State(
            val context: AnalysisContext,
            val analyzer: ClassAndMemberVisitor
    )

    /**
     * Validate whether or not the classes in a class hierarchy can be safely instantiated and run in a sandbox by
     * checking that all references are rooted in deterministic code.
     *
     * @param context The context in which the check should be made.
     * @param analyzer Underlying analyzer used for processing classes.
     */
    fun validate(context: AnalysisContext, analyzer: ClassAndMemberVisitor): ReferenceValidationSummary =
            State(context, analyzer).let { state ->
                context.references.process { validateReference(state, it) }
                ReferenceValidationSummary(state.context.classes, state.context.messages)
            }

    /**
     * Construct a message from an invalid reference and its source location.
     */
    private fun referenceToMessage(referenceWithLocation: ReferenceWithLocation): Message {
        val (location, reference, description) = referenceWithLocation
        val referenceMessage = when {
            reference is ClassReference ->
                "Invalid reference to class ${configuration.classModule.getFormattedClassName(reference.className)}"
            reference is MemberReference && configuration.memberModule.isConstructor(reference) ->
                "Invalid reference to constructor ${memberFormatter.format(reference)}"
            reference is MemberReference && configuration.memberModule.isField(reference) ->
                "Invalid reference to field ${memberFormatter.format(reference)}"
            reference is MemberReference && configuration.memberModule.isMethod(reference) ->
                "Invalid reference to method ${memberFormatter.format(reference)}"
            else ->
                "Invalid reference to $reference"
        }
        val message = if (description.isNotBlank()) {
            "$referenceMessage, $description"
        } else {
            referenceMessage
        }
        return Message(message, Severity.ERROR, location)
    }

    /**
     * Validate a reference made from a class or class member.
     */
    private fun validateReference(state: State, reference: EntityReference) {
        when (reference) {
            is ClassReference -> {
                val clazz = getClass(state, reference.className)
                val reason = when (clazz) {
                    null -> Reason(Reason.Code.NON_EXISTENT_CLASS)
                    else -> clazz.let { getReasonFromEntity(it) }
                }
                if (reason != null) {
                    state.context.messages.addAll(state.context.references.get(reference).map {
                        referenceToMessage(ReferenceWithLocation(it, reference, reason.description))
                    })
                }
            }
            is MemberReference -> {
                // Ensure that the dependent class is loaded and analyzed
                val clazz = getClass(state, reference.className)
                val member = state.context.classes.getMember(
                        reference.className, reference.memberName, reference.signature
                )
                val reason = when {
                    clazz == null -> Reason(Reason.Code.NON_EXISTENT_CLASS)
                    member == null -> Reason(Reason.Code.NON_EXISTENT_MEMBER)
                    else -> getReasonFromEntity(state, member)
                }
                if (reason != null) {
                    state.context.messages.addAll(state.context.references.get(reference).map {
                        referenceToMessage(ReferenceWithLocation(it, reference, reason.description))
                    })
                }
            }
        }
    }

    /**
     * Get a class from the class hierarchy by its binary name.
     */
    private fun getClass(state: State, className: String): Class? {
        val arrayTypeExtractor = Regex("^\\[*L([^;]+);$")
        val name = if (configuration.classModule.isArray(className)) {
            val arrayType = arrayTypeExtractor.find(className)?.groupValues?.get(1)
            when (arrayType) {
                null -> "java/lang/Object"
                else -> arrayType
            }
        } else {
            className
        }
        var clazz = state.context.classes[name]
        if (clazz == null) {
            val origin = state.context.references.get(ClassReference(name)).firstOrNull()
            state.analyzer.analyze(name, state.context, origin?.className)
            clazz = state.context.classes[name]
        }
        if (clazz == null) {
            state.context.messages.add(Message("Referenced class not found; $name", Severity.ERROR))
        }
        clazz?.apply {
            val ancestors = listOf(superClass) + interfaces
            for (ancestor in ancestors.filter(String::isNotBlank)) {
                getClass(state, ancestor)
            }
        }
        return clazz
    }

    /**
     * Check if a top-level class definition is considered safe or not.
     */
    private fun isNonDeterministic(state: State, className: String): Boolean = when {
        configuration.whitelist.matches(className) -> false
        else -> {
            try {
                getClass(state, className)?.let {
                    isNonDeterministic(it)
                } ?: true
            } catch (exception: SandboxClassLoadingException) {
                true // Failed to load the class, which means the class is non-deterministic.
            }
        }
    }

    /**
     * Check if a top-level class definition is considered safe or not.
     */
    private fun isNonDeterministic(clazz: Class) =
            getReasonFromEntity(clazz) != null

    /**
     * Derive what reason to give to the end-user for an invalid class.
     */
    private fun getReasonFromEntity(clazz: Class): Reason? = when {
        configuration.whitelist.matches(clazz.name) -> null
        configuration.whitelist.inNamespace(clazz.name) -> Reason(Reason.Code.NOT_WHITELISTED)
        configuration.classModule.isNonDeterministic(clazz) -> Reason(Reason.Code.ANNOTATED)
        else -> null
    }

    /**
     * Derive what reason to give to the end-user for an invalid member.
     */
    private fun getReasonFromEntity(state: State, member: Member): Reason? = when {
        configuration.whitelist.matches(member.reference) -> null
        configuration.whitelist.inNamespace(member.reference) -> Reason(Reason.Code.NOT_WHITELISTED)
        configuration.memberModule.isNonDeterministic(member) -> Reason(Reason.Code.ANNOTATED)
        else -> {
            val invalidClasses = configuration.memberModule.findReferencedClasses(member)
                    .filter { isNonDeterministic(state, it) }
            if (invalidClasses.isNotEmpty()) {
                Reason(Reason.Code.INVALID_CLASS, invalidClasses.map {
                    configuration.classModule.getFormattedClassName(it)
                })
            } else {
                null
            }
        }
    }

}
