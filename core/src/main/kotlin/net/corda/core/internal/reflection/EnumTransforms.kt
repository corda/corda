package net.corda.core.internal.reflection

import net.corda.core.KeepForDJVM
import net.corda.core.serialization.CordaSerializationTransformEnumDefault
import net.corda.core.serialization.CordaSerializationTransformRename
import java.io.NotSerializableException
import java.util.*

@KeepForDJVM
class InvalidEnumTransformsException(message: String): Exception(message)

@KeepForDJVM
enum class LocalTransformTypes {
    Unknown {
        override fun build(annotation: Annotation) = LocalTransform.UnknownTransform
    },

    EnumDefault {
        override fun build(annotation: Annotation) =
                LocalTransform.EnumDefaultSchemaTransform(
                        (annotation as CordaSerializationTransformEnumDefault).old,
                        annotation.new)
    },

    Rename {
        override fun build(annotation: Annotation) =
                LocalTransform.RenameSchemaTransform(
                        (annotation as CordaSerializationTransformRename).from,
                        annotation.to)
    };

    abstract fun build(annotation: Annotation): LocalTransform
}

@KeepForDJVM
sealed class LocalTransform {
    abstract val name: String

    object UnknownTransform : LocalTransform() {
        override val name: String get() = "Unknown"
    }

    data class EnumDefaultSchemaTransform(val old: String, val new: String): LocalTransform() {
        override val name: String get() = "Default"
    }

    class RenameSchemaTransform(val from: String, val to: String): LocalTransform() {
        override val name: String get() = "Rename"
    }
}

typealias LocalTransformsMap = EnumMap<LocalTransformTypes, MutableList<LocalTransform>>

/**
 * Processes the annotations applied to classes intended for serialisation, to get the transforms that can be applied to them.
 */
@KeepForDJVM
object TransformsAnnotationProcessor {

    /**
     * Obtain all of the transforms applied for the given [Class].
     */
    fun getTransformsSchema(type: Class<*>): LocalTransformsMap {
        val result = LocalTransformsMap(LocalTransformTypes::class.java)
        // We only have transforms for enums at present.
        if (!type.isEnum) return result

        supportedTransforms.forEach { supportedTransform ->
            val annotationContainer = type.getAnnotation(supportedTransform.type) ?: return@forEach
            result.processAnnotations(
                    type,
                    supportedTransform.enum,
                    supportedTransform.getAnnotations(annotationContainer))
        }

        return result
    }

    private fun LocalTransformsMap.processAnnotations(type: Class<*>, transformType: LocalTransformTypes, annotations: List<Annotation>) {
        annotations.forEach { annotation ->
            addTransform(transformType, transformType.build(annotation))
        }
    }

    private fun LocalTransformsMap.addTransform(transformType: LocalTransformTypes, transform: LocalTransform) {
        // we're explicitly rejecting repeated annotations, whilst it's fine and we'd just
        // ignore them it feels like a good thing to alert the user to since this is
        // more than likely a typo in their code so best make it an actual error
        compute(transformType) { _, transforms ->
            when {
                transforms == null -> mutableListOf(transform)
                transform in transforms -> throw NotSerializableException(
                        "Repeated unique transformation annotation of type ${transform.name}")
                else -> transforms.apply { this += transform }
            }
        }
    }

}
/**
 * Contains all of the transforms that have been defined against an enum.
 *
 * @param defaults A [Map] of "new" to "old" for enum constant defaults
 * @param renames A [Map] of "to" to "from" for enum constant renames.
 * @param source The [TransformsMap] from which this data was derived.
 */
@KeepForDJVM
data class EnumTransforms(
        val defaults: Map<String, String>,
        val renames: Map<String, String>,
        val source: LocalTransformsMap) {

    val size: Int get() = defaults.size + renames.size

    @KeepForDJVM
    companion object {
        /**
         * Build a set of [EnumTransforms] from a [TransformsMap], and validate it against the supplied constants.
         */
        fun build(source: LocalTransformsMap, constants: Map<String, Int>): EnumTransforms {
            val defaultTransforms = source[LocalTransformTypes.EnumDefault]?.asSequence()
                    ?.filterIsInstance<LocalTransform.EnumDefaultSchemaTransform>()
                    ?.toList() ?: emptyList()

            val renameTransforms = source[LocalTransformTypes.Rename]?.asSequence()
                    ?.filterIsInstance<LocalTransform.RenameSchemaTransform>()
                    ?.toList() ?: emptyList()

            // We have to do this validation here, because duplicate keys are discarded in EnumTransforms.
            renameTransforms.groupingBy { it.from }.eachCount().forEach { from, count ->
                if (count > 1) throw InvalidEnumTransformsException(
                        "There are multiple transformations from $from, which is not allowed")
            }
            renameTransforms.groupingBy { it.to }.eachCount().forEach { to, count ->
                if (count > 1) throw InvalidEnumTransformsException(
                        "There are multiple transformations to $to, which is not allowed")
            }

            val defaults = defaultTransforms.associate { transform -> transform.new to transform.old }
            val renames = renameTransforms.associate { transform -> transform.to to transform.from }
            return EnumTransforms(defaults, renames, source).validate(constants)
        }

        val empty = EnumTransforms(emptyMap(), emptyMap(), LocalTransformsMap(LocalTransformTypes::class.java))
    }

    private fun validate(constants: Map<String, Int>): EnumTransforms {
        validateNoCycles(constants)

        // For any name in the enum's constants, get all its previous names
        fun renameChain(newName: String): Sequence<String> = generateSequence(newName) { renames[it] }

        // Map all previous names to the current name's index.
        val constantsBeforeRenaming = constants.asSequence().flatMap { (name, index) ->
            renameChain(name).map { it to index }
        }.toMap()

        validateDefaults(constantsBeforeRenaming + constants)

        return this
    }

    /**
     * Verify that there are no rename cycles, i.e. C -> D -> C, or A -> B -> C -> A.
     *
     * This algorithm depends on the precondition (which is validated during construction of [EnumTransforms]) that there is at
     * most one edge (a rename "from" one constant "to" another) between any two nodes (the constants themselves) in the rename
     * graph. It makes a single pass over the set of edges, attempting to add each new edge to any existing chain of edges, or
     * starting a new chain if there is no existing chain.
     *
     * For each new edge, one of the following must true:
     *
     * 1) There is no existing chain to which the edge can be connected, in which case it starts a new chain.
     * 2) The edge can be added to one existing chain, either at the start or the end of the chain.
     * 3) The edge is the "missing link" between two unconnected chains.
     * 4) The edge is the "missing link" between the start of a chain and the end of that same chain, in which case we have a cycle.
     *
     * By detecting each condition, and updating the chains accordingly, we can perform cycle-detection in O(n) time.
     */
    private fun validateNoCycles(constants: Map<String, Int>) {
        // We keep track of chains in both directions
        val chainStartsToEnds = mutableMapOf<String, String>()
        val chainEndsToStarts = mutableMapOf<String, String>()

        for ((to, from) in renames) {
            if (from in constants) {
                throw InvalidEnumTransformsException("Rename from $from to $to would rename existing constant in $constants.keys")
            }

            // If there is an existing chain, starting at the "to" node of this edge, then there is a chain from this edge's
            // "from" to that chain's end.
            val newEnd = chainStartsToEnds[to] ?: to

            // If there is an existing chain, ending at the "from" node of this edge, then there is a chain from that chain's start
            // to this edge's "to".
            val newStart = chainEndsToStarts[from] ?: from

            // If either chain ends where it begins, we have closed a loop, and detected a cycle.
            if (newEnd == from || newStart == to) {
                throw InvalidEnumTransformsException("Rename cycle detected in rename map starting from $newStart")
            }

            // Either update, or create, the chains in both directions.
            chainStartsToEnds[newStart] = newEnd
            chainEndsToStarts[newEnd] = newStart
        }

        // Make sure that every rename chain ends with a known constant.
        for ((chainStart, chainEnd) in chainStartsToEnds) {
            if (chainEnd !in constants) {
                throw InvalidEnumTransformsException(
                        "Rename chain from $chainStart to $chainEnd does not end with a known constant in ${constants.keys}")
            }
        }
    }

    /**
     * Verify that defaults match up to existing constants (prior to their renaming).
     */
    private fun validateDefaults(constantsBeforeRenaming: Map<String, Int>) {
        defaults.forEach { (new, old) ->
            requireThat(constantsBeforeRenaming.contains(new)) { "Unknown enum constant $new" }
            requireThat(constantsBeforeRenaming.contains(old)) {
                "Enum extension defaults must be to a valid constant: $new -> $old. $old " +
                        "doesn't exist in constant set $constantsBeforeRenaming"
            }
            requireThat(old != new) { "Enum extension $new cannot default to itself" }
            requireThat(constantsBeforeRenaming[old]!! < constantsBeforeRenaming[new]!!) {
                "Enum extensions must default to older constants. $new[${constantsBeforeRenaming[new]}] " +
                        "defaults to $old[${constantsBeforeRenaming[old]}] which is greater"
            }
        }
    }

    private inline fun requireThat(expr: Boolean, errorMessage: () -> String) {
        if (!expr) {
            throw InvalidEnumTransformsException(errorMessage())
        }
    }
}