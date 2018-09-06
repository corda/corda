package net.corda.djvm.assertions

import net.corda.djvm.analysis.SourceLocation
import net.corda.djvm.references.EntityReference
import net.corda.djvm.references.ReferenceMap
import org.assertj.core.api.Assertions

class AssertiveReferenceMapWithEntity(
        references: ReferenceMap,
        private val entity: EntityReference,
        private val locations: Set<SourceLocation>
) : AssertiveReferenceMap(references) {

    fun withLocationCount(count: Int): AssertiveReferenceMapWithEntity {
        Assertions.assertThat(locations.size)
                .`as`("LocationCount($entity)")
                .isEqualTo(count)
        return this
    }

}
