package net.corda.core.schemas

import java.util.*
import javax.persistence.*

class BadSchemaNoGetterV1 : MappedSchema(TestJavaSchemaFamily::class.java, 1, Arrays.asList(State::class.java)) {

    @Entity
    class State : PersistentState() {
        @JoinColumns(JoinColumn(name = "itid"), JoinColumn(name = "outid"))
        @OneToOne
        @MapsId
        var other: GoodSchemaV1.State? = null
        @get:Column
        var id: String? = null
    }
}