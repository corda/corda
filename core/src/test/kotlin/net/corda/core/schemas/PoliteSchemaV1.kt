package net.corda.core.schemas

import java.util.*
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Transient

class PoliteSchemaV1 : MappedSchema(TestJavaSchemaFamily::class.java, 1, Arrays.asList(State::class.java)) {

    @Entity
    class State : PersistentState() {
        @get:Column
        var id: String? = null
        @get:Transient
        var other: GoodSchemaV1.State? = null
    }
}