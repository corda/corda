package net.corda.nodeapi.internal.peristence

import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.finance.schemas.CashSchema
import net.corda.nodeapi.internal.persistence.fieldFromOtherMappedSchema
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import javax.persistence.*

class HibernateConfigurationTests {

    object GoodSchema : MappedSchema(schemaFamily = CashSchema.javaClass, version = 1, mappedTypes = listOf(GoodEntity::class.java)) {
        @Entity
        class GoodEntity(
                @Column
                var id: String
        ) : PersistentState()
    }

    object BadSchema : MappedSchema(schemaFamily = CashSchema.javaClass, version = 1, mappedTypes = listOf(EntityB::class.java)) {
        @Entity
        class EntityB(
                @Column
                var id: String,

                @JoinColumns(JoinColumn(name = "itid"), JoinColumn(name = "outid"))
                @OneToOne
                @MapsId
                var other: GoodSchema.GoodEntity
        ) : PersistentState()
    }

    object TrickySchema : MappedSchema(schemaFamily = CashSchema.javaClass, version = 1, mappedTypes = listOf(TrickyEntity::class.java)) {
        @Entity
        class TrickyEntity(
                @Column
                var id: String,

                //the field is from other schema bu it's not persisted one (no JPA annotation)
                var other: GoodSchema.GoodEntity
        ) : PersistentState()
    }

    object PoliteSchema : MappedSchema(schemaFamily = CashSchema.javaClass, version = 1, mappedTypes = listOf(PoliteEntity::class.java)) {
        @Entity
        class PoliteEntity(
                @Column
                var id: String,

                @Transient
                var other: GoodSchema.GoodEntity
        ) : PersistentState()
    }

    @Test
    fun `no cross reference to other schema`() {
        assertThat(GoodSchema.fieldFromOtherMappedSchema()).isEmpty()
    }

    @Test
    fun `cross reference to other schema is detected`() {
        assertThat(BadSchema.fieldFromOtherMappedSchema()).isNotEmpty
    }

    @Test
    fun `corss reference via non JPA field is allowed`() {
        assertThat(TrickySchema.fieldFromOtherMappedSchema()).isEmpty()
    }

    @Test
    fun `cross reference via transient field is allowed`() {
        assertThat(PoliteSchema.fieldFromOtherMappedSchema()).isEmpty()
    }
}