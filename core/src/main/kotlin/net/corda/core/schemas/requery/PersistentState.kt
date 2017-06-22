package net.corda.core.schemas.requery

import io.requery.Key
import io.requery.Persistable
import io.requery.Superclass
import net.corda.core.contracts.StateRef
import net.corda.core.schemas.StatePersistable

import javax.persistence.Column

object Requery {
    /**
     * A super class for all mapped states exported to a schema that ensures the [StateRef] appears on the database row.  The
     * [StateRef] will be set to the correct value by the framework (there's no need to set during mapping generation by the state itself).
     */
    // TODO: this interface will supercede the existing [PersistentState] interface defined in PersistentTypes.kt
    //       once we cut-over all existing Hibernate ContractState persistence to Requery
    @Superclass interface PersistentState : StatePersistable {
        @get:Key
        @get:Column(name = "transaction_id", length = 64)
        var txId: String

        @get:Key
        @get:Column(name = "output_index")
        var index: Int
    }
}
