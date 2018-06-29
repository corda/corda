package net.corda.serialization.internal.amqp

import net.corda.core.KeepForDJVM
import net.corda.core.internal.uncheckedCast
import net.corda.core.serialization.CordaSerializationTransformEnumDefault
import net.corda.core.serialization.CordaSerializationTransformEnumDefaults
import net.corda.core.serialization.CordaSerializationTransformRename
import net.corda.serialization.internal.NotSerializableWithReasonException
import org.apache.qpid.proton.amqp.DescribedType
import org.apache.qpid.proton.codec.DescribedTypeConstructor

/**
 * Enumerated type that represents each transform that can be applied to a class. Used as the key type in
 * the [TransformsSchema] map for each class.
 *
 * @property build should be a function that takes a transform [Annotation] (currently one of
 * [CordaSerializationTransformRename] or [CordaSerializationTransformEnumDefaults])
 * and constructs an instance of the corresponding [Transform] type
 *
 * DO NOT REORDER THE CONSTANTS!!! Please append any new entries to the end
 */
// TODO:  it would be awesome to auto build this list by scanning for transform annotations themselves
// TODO: annotated with some annotation
@KeepForDJVM
enum class TransformTypes(val build: (Annotation) -> Transform) : DescribedType {
    /**
     * Placeholder entry for future transforms where a node receives a transform we've subsequently
     * added and thus the de-serialising node doesn't know about that transform.
     */
    Unknown({ UnknownTransform() }) {
        override fun getDescriptor(): Any = DESCRIPTOR
        override fun getDescribed(): Any = ordinal
        override fun validate(list: List<Transform>, constants: Map<String, Int>) {}
    },
    EnumDefault({ a -> EnumDefaultSchemaTransform((a as CordaSerializationTransformEnumDefault).old, a.new) }) {
        override fun getDescriptor(): Any = DESCRIPTOR
        override fun getDescribed(): Any = ordinal

        /**
         * Validates a list of constant additions to an enumerated type. To be valid a default (the value
         * that should be used when we cannot use the new value) must refer to a constant that exists in the
         * enum class as it exists now and it cannot refer to itself.
         *
         * @param list The list of transforms representing new constants and the mapping from that constant to an
         * existing value
         * @param constants The list of enum constants on the type the transforms are being applied to
         */
        override fun validate(list: List<Transform>, constants: Map<String, Int>) {
            uncheckedCast<List<Transform>, List<EnumDefaultSchemaTransform>>(list).forEach {
                requireThat(constants.contains(it.new)) {"Unknown enum constant ${it.new}"}
                requireThat(constants.contains(it.old)) { "Enum extension defaults must be to a valid constant: ${it.new} -> ${it.old}. ${it.old} " +
                        "doesn't exist in constant set $constants" }
                requireThat(it.old != it.new) { "Enum extension ${it.new} cannot default to itself" }
                requireThat(constants[it.old]!! < constants[it.new]!!) { "Enum extensions must default to older constants. ${it.new}[${constants[it.new]}] " +
                        "defaults to ${it.old}[${constants[it.old]}] which is greater" }
            }
        }
    },
    Rename({ a -> RenameSchemaTransform((a as CordaSerializationTransformRename).from, a.to) }) {
        override fun getDescriptor(): Any = DESCRIPTOR
        override fun getDescribed(): Any = ordinal

        /**
         * Validates a list of rename transforms is valid. Such a list isn't valid if we detect a cyclic chain,
         * that is a constant is renamed to something that used to exist in the enum. We do this for both
         * the same constant (i.e. C -> D -> C) and multiple constants (C->D, B->C)
         *
         * @param list The list of transforms representing the renamed constants and the mapping between their new
         * and old values
         * @param constants The list of enum constants on the type the transforms are being applied to
         */
        override fun validate(list: List<Transform>, constants: Map<String, Int>) {
            data class Node(val transform: RenameSchemaTransform, var next: Node?, var prev: Node?, var visitedBy: Node? = null) {
                fun visit(visitedBy: Node) {
                    this.visitedBy = visitedBy
                }
                val visited get() = visitedBy != null
            }

            val graph = mutableListOf<Node>()
            // Keep two maps of forward links and back links in order to build the graph in one pass
            val forwardLinks = hashMapOf<String, Node>()
            val reverseLinks = hashMapOf<String, Node>()

            // build a dependency graph
            val transforms: List<RenameSchemaTransform> = uncheckedCast(list)
            transforms.forEach { rename ->
                requireThat(!forwardLinks.contains(rename.from)) { "There are multiple transformations from ${rename.from}, which is not allowed" }
                requireThat(!reverseLinks.contains(rename.to)) { "There are multiple transformations to ${rename.to}, which is not allowed" }
                val node = Node(rename, forwardLinks[rename.to], reverseLinks[rename.from])
                graph.add(node)
                node.next?.prev = node
                node.prev?.next = node
                forwardLinks[rename.from] = node
                reverseLinks[rename.to] = node
            }

            // Check that every property in the current type is at the end of a renaming chain, if it is in one
            constants.keys.forEach {
                requireThat(reverseLinks[it]?.next == null) { "$it is specified as a previously evolved type, but it also exists in the current type" }
            }

            // Check for cyclic dependencies
            graph.forEach {
                if (it.visited) return@forEach
                // Find an unvisited node
                var currentNode = it
                currentNode.visit(it)
                while (currentNode.next != null) {
                    currentNode = currentNode.next!!
                    if (currentNode.visited) {
                        requireThat(currentNode.visitedBy != it) { "Cyclic renames are not allowed (${currentNode.transform.from})" }
                        // we have found the start of another non-cyclic chain of dependencies
                        // if they were cyclic we would have gone round in a loop and already thrown
                        break
                    }
                    currentNode.visit(it)
                }
            }
        }
    }

    // Transform used to test the unknown handler, leave this at as the final constant, uncomment
    // when regenerating test cases - if Java had a pre-processor this would be much neater
    //
    //,UnknownTest({ a -> UnknownTestTransform((a as UnknownTransformAnnotation).a, a.b, a.c)}) {
    //    override fun getDescriptor(): Any = DESCRIPTOR
    //    override fun getDescribed(): Any = ordinal
    //    override fun validate(list: List<Transform>, constants: Map<String, Int>) = Unit
    //}
    ;

    abstract fun validate(list: List<Transform>, constants: Map<String, Int>)

    companion object : DescribedTypeConstructor<TransformTypes> {
        val DESCRIPTOR = AMQPDescriptorRegistry.TRANSFORM_ELEMENT_KEY.amqpDescriptor

        /**
         * Used to construct an instance of the object from the serialised bytes
         *
         * @param obj the serialised byte object from the AMQP serialised stream
         */
        override fun newInstance(obj: Any?): TransformTypes {
            val describedType = obj as DescribedType

            requireThat(describedType.descriptor == DESCRIPTOR) { "Unexpected descriptor ${describedType.descriptor}." }

            return try {
                values()[describedType.described as Int]
            } catch (e: IndexOutOfBoundsException) {
                values()[0]
            }
        }

        override fun getTypeClass(): Class<*> = TransformTypes::class.java

        protected inline fun requireThat(expr: Boolean, errorMessage: () -> String) {
            if (!expr) {
                throw NotSerializableWithReasonException(errorMessage())
            }
        }
    }
}
