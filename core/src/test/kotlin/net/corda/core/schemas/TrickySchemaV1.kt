package net.corda.core.schemas

import java.util.*
import javax.persistence.Column
import javax.persistence.Entity

class TrickySchemaV1 : MappedSchema(TestJavaSchemaFamily::class.java, 1, Arrays.asList(TrickySchemaV1.State::class.java)) {

    @Entity
    class State : PersistentState() {
        @get:Column
        var id: String? = null
        //the field is a cross-reference to other MappedSchema however the field is not persistent (no JPA annotation)
        var other: GoodSchemaV1.State? = null
    }
}