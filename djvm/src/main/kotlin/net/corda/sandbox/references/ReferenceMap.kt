package net.corda.sandbox.references

import net.corda.sandbox.analysis.SourceLocation
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Map from member references to all discovered call-sites / field accesses for each reference.
 */
class ReferenceMap(
        private val classModule: ClassModule
) : Iterable<EntityReference> {

    private val queueOfReferences = ConcurrentLinkedQueue<EntityReference>()

    private val locationsPerReference: MutableMap<EntityReference, MutableSet<SourceLocation>> = hashMapOf()

    private val referencesPerLocation: MutableMap<String, MutableSet<ReferenceWithLocation>> = hashMapOf()

    private var numberOfReferences = 0

    /**
     * Add source location association to a target member.
     */
    fun add(target: EntityReference, location: SourceLocation) {
        locationsPerReference.getOrPut(target) {
            queueOfReferences.add(target)
            numberOfReferences += 1
            hashSetOf()
        }.add(location)
        ReferenceWithLocation(location, target).apply {
            referencesPerLocation.getOrPut(location.key()) { hashSetOf() }.add(this)
            if (location.memberName.isNotBlank()) {
                referencesPerLocation.getOrPut(key(location.className)) { hashSetOf() }.add(this)
            }
        }
    }

    /**
     * Get call-sites and field access locations associated with a target member.
     */
    fun locationsFromReference(target: EntityReference): Set<SourceLocation> =
            locationsPerReference.getOrElse(target) { emptySet() }

    /**
     * Look up all references made from a class or a class member.
     */
    fun referencesFromLocation(
            className: String, memberName: String = "", signature: String = ""
    ): Set<ReferenceWithLocation> {
        return referencesPerLocation.getOrElse(key(className, memberName, signature)) { emptySet() }
    }

    /**
     * The number of member references in the map.
     */
    val size: Int
        get() = numberOfReferences

    /**
     * Get iterator for all the references in the map.
     */
    override fun iterator(): Iterator<EntityReference> = queueOfReferences.iterator()

    /**
     * Iterate over the dynamic collection of references.
     */
    fun process(action: (EntityReference) -> Unit) {
        while (queueOfReferences.isNotEmpty()) {
            queueOfReferences.remove().apply(action)
        }
    }

    companion object {

        private fun SourceLocation.key() = key(this.className, this.memberName, this.signature)

        private fun key(className: String, memberName: String = "", signature: String = "") =
                "$className.$memberName:$signature"

    }

}
