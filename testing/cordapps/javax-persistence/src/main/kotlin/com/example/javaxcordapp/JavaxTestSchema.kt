package com.example.javaxcordapp

import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import org.hibernate.annotations.Type
import java.util.UUID
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Table

/**
 * The schema of a CorDapp built against `javax.persistence` (and Hibernate 5's `@Type`) for Corda 4.12, the way a CorDapp for Corda 4.14
 * or earlier is built. Neither the `javax.persistence` API nor Hibernate 5 is on the runtime classpath of the tests that use it, exactly
 * as on a Corda 4.15 node, so the classes are only usable once converted to `jakarta.persistence`.
 */
object JavaxTestSchema

object JavaxTestSchemaV1 : MappedSchema(JavaxTestSchema::class.java, 1, listOf(PersistentJavaxTestState::class.java))

@Entity
@Table(name = "javax_test_states")
class PersistentJavaxTestState(
        @Column(name = "label", nullable = false)
        var label: String,

        /** Mapped with the Hibernate 5 type Corda documented for UUIDs, which the conversion substitutes by a JDBC type code. */
        @Column(name = "ref", nullable = false)
        @Type(type = "uuid-char")
        var ref: UUID
) : PersistentState() {
    constructor() : this("", UUID(0, 0))
}
