package net.corda.core.schemas

import java.util.*
import javax.persistence.*

class BadSchemaV1 : MappedSchema(TestJavaSchemaFamily::class.java, 1, Arrays.asList(State::class.java)) {

    @Entity
    class State : PersistentState() {
        @get:Column
        var id: String? = null
        @get:JoinColumns(JoinColumn(name = "itid"), JoinColumn(name = "outid"))
        @get:OneToOne
        @get:MapsId
        var other: GoodSchemaV1.State? = null
    }
}