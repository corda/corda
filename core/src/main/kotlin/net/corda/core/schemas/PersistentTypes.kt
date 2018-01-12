package net.corda.core.schemas

import com.google.common.base.CaseFormat
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateRef
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.toHexString
import java.io.Serializable
import javax.persistence.Column
import javax.persistence.Embeddable
import javax.persistence.EmbeddedId
import javax.persistence.MappedSuperclass

//DOCSTART QueryableState
/**
 * A contract state that may be mapped to database schemas configured for this node to support querying for,
 * or filtering of, states.
 */
interface QueryableState : ContractState {
    /**
     * Enumerate the schemas this state can export representations of itself as.
     */
    fun supportedSchemas(): Iterable<MappedSchema>

    /**
     * Export a representation for the given schema.
     */
    fun generateMappedObject(schema: MappedSchema): PersistentState
}
//DOCEND QueryableState

//DOCSTART MappedSchema
/**
 * A database schema that might be configured for this node.  As well as a name and version for identifying the schema,
 * also list the classes that may be used in the generated object graph in order to configure the ORM tool.
 *
 * @param schemaFamily A class to fully qualify the name of a schema family (i.e. excludes version)
 * @param version The version number of this instance within the family.
 * @param mappedTypes The JPA entity classes that the ORM layer needs to be configure with for this schema.
 */
open class MappedSchema(schemaFamily: Class<*>,
                        val version: Int,
                        val mappedTypes: Iterable<Class<*>>) {
    val name: String = schemaFamily.name

    /**
     * Points to a classpath resource containing the database changes for the [mappedTypes]
     */
    protected open val migrationResource: String? = null

    internal fun getMigrationResource(): String? = migrationResource

    override fun toString(): String = "${this.javaClass.simpleName}(name=$name, version=$version)"
}
//DOCEND MappedSchema

/**
 * A super class for all mapped states exported to a schema that ensures the [StateRef] appears on the database row.  The
 * [StateRef] will be set to the correct value by the framework (there's no need to set during mapping generation by the state itself).
 */
@MappedSuperclass
@CordaSerializable open class PersistentState(@EmbeddedId var stateRef: PersistentStateRef? = null) : StatePersistable

/**
 * Embedded [StateRef] representation used in state mapping.
 */
@Embeddable
data class PersistentStateRef(
        @Column(name = "transaction_id", length = 64)
        var txId: String? = null,

        @Column(name = "output_index")
        var index: Int? = null
) : Serializable {
    constructor(stateRef: StateRef) : this(stateRef.txhash.bytes.toHexString(), stateRef.index)
}

/**
 * Marker interface to denote a persistable Corda state entity that will always have a transaction id and index
 */
interface StatePersistable

private const val MIGRATION_PREFIX = "migration"
private const val DEFAULT_MIGRATION_EXTENSION = "xml"
private const val CHANGELOG_NAME = "changelog-master"
private val possibleMigrationExtensions = listOf(".xml", ".sql", ".yml", ".json")

fun getMigrationResource(schema: MappedSchema): String? {
    val declaredMigration = schema.getMigrationResource()

    if (declaredMigration == null) {
        // try to apply the naming convention and find the migration file in the classpath
        val resource = migrationResourceNameForSchema(schema)
        return possibleMigrationExtensions.map { "${resource}${it}" }.firstOrNull {
            Thread.currentThread().contextClassLoader.getResource(it) != null
        }
    }

    return "${MIGRATION_PREFIX}/${declaredMigration}.${DEFAULT_MIGRATION_EXTENSION}"
}

// SchemaName will be transformed from camel case to lower_hyphen
// then add ".changelog-master"
fun migrationResourceNameForSchema(schema: MappedSchema): String {
    val name: String = schema::class.simpleName!!
    val fileName = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_HYPHEN, name)
    return "${MIGRATION_PREFIX}/${fileName}.${CHANGELOG_NAME}"
}