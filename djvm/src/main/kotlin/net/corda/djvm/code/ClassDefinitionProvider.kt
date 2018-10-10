package net.corda.djvm.code

import net.corda.djvm.analysis.AnalysisRuntimeContext
import net.corda.djvm.references.ClassRepresentation

/**
 * A class definition provider is a hook for [ClassMutator], from where one can modify the name and meta-data of
 * processed classes.
 */
interface ClassDefinitionProvider : DefinitionProvider {

    /**
     * Hook for providing modifications to a class definition.
     *
     * @param context The context in which the hook is called.
     * @param clazz The original class definition.
     *
     * @return The updated class definition, or [clazz] if no changes are desired.
     */
    fun define(context: AnalysisRuntimeContext, clazz: ClassRepresentation): ClassRepresentation

}
