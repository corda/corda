package net.corda.node.migration

import net.corda.core.schemas.MappedSchema
import net.corda.core.utilities.MAX_HASH_HEX_SIZE
import org.apache.commons.lang3.ArrayUtils
import net.corda.nodeapi.internal.persistence.factory.CordaSqlTypes
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Lob
import jakarta.persistence.Table

object MigrationTestSchema

/**
 * Schema definition for testing PersistentIdentityService custom migration scripts at the moment when scripts were written.
 * Used to break dependency on the latest PersistentIdentityService which schema version may be different.
 *
 * This will allow:
 * - to fix the position of relevant scripts in the node-core.changelog-master.xml (instead of placing them at the end)
 * - to perform further modifications of PersistentIdentityService schema without impacting existing migration scripts and their tests
 */
object IdentityTestSchemaV1 : MappedSchema(
        schemaFamily = MigrationTestSchema::class.java,
        version = 1,
        mappedTypes = listOf(
                NodeIdentities::class.java,
                NodeNamedIdentities::class.java,
                NodeIdentitiesNoCert::class.java,
                NodeHashToKey::class.java
        )
) {
    @Entity
    @Table(name = "node_identities")
    class NodeIdentities(
            @Id
            @Column(name = "pk_hash", length = MAX_HASH_HEX_SIZE, nullable = false)
            var publicKeyHash: String = "",

            @JdbcTypeCode(CordaSqlTypes.CORDA_BLOB)
            @Column(name = "identity_value", nullable = false)
            var identity: ByteArray = ArrayUtils.EMPTY_BYTE_ARRAY
    )

    @Entity
    @Table(name = "node_named_identities")
    class NodeNamedIdentities(
            @Id
            @Suppress("MagicNumber") // database column width
            @Column(name = "name", length = 128, nullable = false)
            var name: String = "",

            @Column(name = "pk_hash", length = MAX_HASH_HEX_SIZE, nullable = true)
            var publicKeyHash: String = ""
    )

    @Entity
    @Table(name = "node_identities_no_cert")
    class NodeIdentitiesNoCert(
            @Id
            @Column(name = "pk_hash", length = MAX_HASH_HEX_SIZE, nullable = false)
            var publicKeyHash: String = "",

            @Column(name = "name", length = 128, nullable = false)
            var name: String = ""
    )

    @Entity
    @Table(name = "node_hash_to_key")
    class NodeHashToKey(
            @Id
            @Column(name = "pk_hash", length = MAX_HASH_HEX_SIZE, nullable = false)
            var publicKeyHash: String = "",

            @JdbcTypeCode(CordaSqlTypes.CORDA_BLOB)
            @Column(name = "public_key", nullable = false)
            var publicKey: ByteArray = ArrayUtils.EMPTY_BYTE_ARRAY
    )
}

object KMSTestSchemaV1 : MappedSchema(
        schemaFamily = MigrationTestSchema::class.java,
        version = 1,
        mappedTypes = listOf(
                PersistentKey::class.java
        )
) {
    @Entity
    @Table(name = "node_our_key_pairs")
    class PersistentKey(
            @Id
            @Column(name = "public_key_hash", length = MAX_HASH_HEX_SIZE, nullable = false)
            var publicKeyHash: String,

            @Lob
            @Column(name = "public_key", nullable = false)
            var publicKey: ByteArray = ArrayUtils.EMPTY_BYTE_ARRAY,

            @Lob
            @Column(name = "private_key", nullable = false)
            var privateKey: ByteArray = ArrayUtils.EMPTY_BYTE_ARRAY
    )
}