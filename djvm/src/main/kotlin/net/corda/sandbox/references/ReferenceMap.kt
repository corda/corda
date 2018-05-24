package net.corda.sandbox.references

import net.corda.sandbox.analysis.SourceLocation
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Map from member references to all discovered call-sites / field accesses for each reference.
 */
class ReferenceMap(
        private val classModule: ClassModule
) : Iterable<EntityReference> {

    private val entities = ConcurrentLinkedQueue<EntityReference>()

    private val map: MutableMap<EntityReference, MutableSet<SourceLocation>> = hashMapOf()

    private val mapByLocation: MutableMap<String, MutableSet<ReferenceWithLocation>> = hashMapOf()

    /**
     * Add source location association to a target member.
     */
    fun add(target: EntityReference, location: SourceLocation) {
        map.getOrPut(target, {
            entities.add(target)
            hashSetOf()
        }).add(location)
        ReferenceWithLocation(location, target).apply {
            mapByLocation.getOrPut(location.key(), { hashSetOf() }).add(this)
            if (location.memberName.isNotBlank()) {
                mapByLocation.getOrPut(key(location.className), { hashSetOf() }).add(this)
            }
        }
    }

    /**
     * Get call-sites and field access locations associated with a target member.
     */
    fun get(target: EntityReference): Set<SourceLocation> =
            map.getOrElse(target) { emptySet() }

    /**
     * Check if any call-sites or field access locations have been registered for a target member.
     */
    fun exists(target: EntityReference): Boolean =
            map[target]?.isNotEmpty() ?: false

    /**
     * Look up all references made from a class or a class member.
     */
    fun from(className: String, memberName: String = "", signature: String = ""): Set<ReferenceWithLocation> {
        return mapByLocation.getOrElse(key(className, memberName, signature)) { emptySet() }
    }

    /**
     * Look up all references made from a specific class.
     */
    fun from(clazz: java.lang.Class<*>) = from(classModule.getBinaryClassName(clazz.name))

    /**
     * Look up all references made from a class member.
     */
    fun from(member: MemberReference) = from(member.className, member.memberName, member.signature)

    /**
     * The number of member references in the map.
     */
    val size: Int
        get() = map.keys.size

    /**
     * Get iterator for all the references in the map.
     */
    override fun iterator(): Iterator<EntityReference> = entities.iterator()

    /**
     * Iterate over the dynamic collection of references.
     */
    fun process(action: (EntityReference) -> Unit) {
        while (entities.isNotEmpty()) {
            val reference = entities.remove()
            action(reference)
        }
    }

    companion object {

        private fun SourceLocation.key() = key(this.className, this.memberName, this.signature)

        private fun key(className: String, memberName: String = "", signature: String = "") =
                "$className.$memberName:$signature"

    }

}
