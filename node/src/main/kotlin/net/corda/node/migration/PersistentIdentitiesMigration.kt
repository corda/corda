package net.corda.node.migration

import liquibase.database.Database
import liquibase.database.jvm.JdbcConnection
import liquibase.exception.ValidationErrors
import liquibase.resource.ResourceAccessor
import net.corda.core.contracts.PartyAndReference
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SignatureScheme
import net.corda.core.crypto.toStringShort
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.internal.unspecifiedCountry
import net.corda.core.schemas.MappedSchema
import net.corda.core.utilities.contextLogger
import net.corda.node.internal.DBNetworkParametersStorage
import net.corda.node.services.identity.PersistentIdentityService
import net.corda.node.services.keys.BasicHSMKeyManagementService
import net.corda.node.services.persistence.DBTransactionStorage
import net.corda.node.services.persistence.NodeAttachmentService
import net.corda.nodeapi.internal.DEV_INTERMEDIATE_CA
import net.corda.nodeapi.internal.DEV_ROOT_CA
import net.corda.nodeapi.internal.createDevNodeCa
import net.corda.nodeapi.internal.crypto.CertificateAndKeyPair
import net.corda.nodeapi.internal.crypto.CertificateType
import net.corda.nodeapi.internal.crypto.X509CertificateFactory
import net.corda.nodeapi.internal.crypto.X509Utilities
import java.math.BigInteger
import java.security.KeyPair
import java.security.PublicKey
import java.security.cert.X509Certificate
import java.util.*

/**
 * Forgive me father, for I have sinned.
 */
class PersistentIdentitiesMigration : CordaMigration() {
    companion object {
        private val logger = contextLogger()
    }

    override fun execute(database: Database?) {
        logger.info("Migrating persistent identities with certificates table into persistent table with no certificate data.")

        if (database == null) {
            logger.error("Cannot migrate persistent states: Liquibase failed to provide a suitable database connection")
            throw PersistentIdentitiesMigrationException("Cannot migrate persistent states as liquibase failed to provide a suitable database connection")
        }
        initialiseNodeServices(database, setOf(PersistentIdentitiesMigrationSchemaV1))

        val connection = database.connection as JdbcConnection

        /**
         * TODO temporary hack to get around soul destroying mocking needed to test this properly
         */
        val alice = DuplicateTestIdentity(CordaX500Name("Alice Corp", "Madrid", "ES"), 70)
        val pkHash = addTestMapping(connection, alice)

        // Extract data from old table needed to populate the new table
        val keys = extractKeys(connection)
        val parties = extractParties(connection)

        require(keys.size == parties.size)

        val map: Map<String, CordaX500Name> = keys.zip(parties).toMap()

        map.forEach {
            insertEntry(connection, it)
        }

        /**
         * TODO temporary hack to get around soul destroying mocking needed to test this properly
         */
        verifyTestMigration(connection, pkHash, alice.name.toString())
        deleteTestMapping(connection, pkHash)
    }

    private fun insertEntry(connection: JdbcConnection, entry: Map.Entry<String, CordaX500Name>) {
        val pk = entry.key
        val name = entry.value.toString()
        connection.prepareStatement("INSERT INTO node_identities_no_cert (pk_hash, name) VALUES (?,?)").use {
            it.setString(1, pk)
            it.setString(2, name)
            it.executeUpdate()
        }
    }

    private fun extractKeys(connection: JdbcConnection): List<String> {
        val keys = mutableListOf<String>()
        connection.createStatement().use {
            val rs = it.executeQuery("SELECT pk_hash FROM node_identities WHERE pk_hash IS NOT NULL")
            while (rs.next()) {
                val key = rs.getString(1)
                keys.add(key)
            }
            rs.close()
        }
        return keys
    }

    private fun extractParties(connection: JdbcConnection): List<CordaX500Name> {
        val entries = mutableListOf<ByteArray>()
        connection.createStatement().use {
            val rs = it.executeQuery("SELECT identity_value FROM node_identities")
            while (rs.next()) {
                val e = rs.getBytes(1)
                entries.add(e)
            }
        }
        //TODO this will need to be more robust in checking checking the certificate before attempting to map to the party
        return entries.map {
            PartyAndCertificate(X509CertificateFactory().delegate.generateCertPath(it.inputStream())).party.name
        }.toList()
    }

    override fun setUp() {
    }

    override fun setFileOpener(resourceAccessor: ResourceAccessor?) {
    }

    override fun getConfirmationMessage(): String? {
        return null
    }

    override fun validate(database: Database?): ValidationErrors? {
        return null
    }

    private fun addTestMapping(connection: JdbcConnection, testIdentity: DuplicateTestIdentity): String {
        val pkHash = UUID.randomUUID().toString()
        val cert = testIdentity.identity.certPath.encoded

        connection.prepareStatement("INSERT INTO node_identities (pk_hash, identity_value) VALUES (?,?)").use {
            it.setString(1, pkHash)
            it.setBytes(2, cert)
            it.executeUpdate()
        }
        return pkHash
    }

    private fun deleteTestMapping(connection: JdbcConnection, pkHash: String) {
        connection.prepareStatement("DELETE FROM node_identities WHERE pk_hash = ?").use {
            it.setString(1, pkHash)
            it.executeUpdate()
        }
    }

    private fun verifyTestMigration(connection: JdbcConnection, pk: String, name: String) {
        connection.createStatement().use {
            val rs = it.executeQuery("SELECT (pk_hash, name) FROM node_identities_no_cert")
            while (rs.next()) {
                val result = rs.getString(1)
                require(result.contains(pk))
                require(result.contains(name))
            }
            rs.close()
        }
    }
}

/**
 * A minimal set of schema for retrieving data from the database.
 *
 * Note that adding an extra schema here may cause migrations to fail if it ends up creating a table before the same table
 * is created in a migration script. As such, this migration must be run after the tables for the following have been created (and,
 * if they are removed in the future, before they are deleted).
 */
object PersistentIdentitiesMigrationSchema

object PersistentIdentitiesMigrationSchemaV1 : MappedSchema(schemaFamily = PersistentIdentitiesMigrationSchema.javaClass, version = 1,
        mappedTypes = listOf(
                DBTransactionStorage.DBTransaction::class.java,
                PersistentIdentityService.PersistentIdentityCert::class.java,
                PersistentIdentityService.PersistentIdentity::class.java,
                BasicHSMKeyManagementService.PersistentKey::class.java,
                NodeAttachmentService.DBAttachment::class.java,
                DBNetworkParametersStorage.PersistentNetworkParameters::class.java
        )
)

class PersistentIdentitiesMigrationException(msg: String, cause: Exception? = null) : Exception(msg, cause)

/**
 * TODO - Ergh. Circular dependencies if you try and expose test-utils here.
 */
class DuplicateTestIdentity(val name: CordaX500Name, val keyPair: KeyPair) {
    companion object {
        /**
         * Creates an identity that won't equal any other. This is mostly useful as a throwaway for test helpers.
         * @param organisation the organisation part of the new identity's name.
         */
        @JvmStatic
        @JvmOverloads
        fun fresh(organisation: String, signatureScheme: SignatureScheme = Crypto.DEFAULT_SIGNATURE_SCHEME): DuplicateTestIdentity {
            val keyPair = Crypto.generateKeyPair(signatureScheme)
            val name = CordaX500Name(organisation, keyPair.public.toStringShort(), CordaX500Name.unspecifiedCountry)
            return DuplicateTestIdentity(name, keyPair)
        }
    }

    /** Creates an identity with a deterministic [keyPair] i.e. same [entropy] same keyPair. */
    @JvmOverloads
    constructor(name: CordaX500Name, entropy: Long, signatureScheme: SignatureScheme = Crypto.DEFAULT_SIGNATURE_SCHEME)
            : this(name, Crypto.deriveKeyPairFromEntropy(signatureScheme, BigInteger.valueOf(entropy)))

    /** Creates an identity with the given name and a fresh keyPair. */
    @JvmOverloads
    constructor(name: CordaX500Name, signatureScheme: SignatureScheme = Crypto.DEFAULT_SIGNATURE_SCHEME)
            : this(name, Crypto.generateKeyPair(signatureScheme))

    val publicKey: PublicKey get() = keyPair.public
    val party: Party = Party(name, publicKey)
    val identity: PartyAndCertificate by lazy { getTestPartyAndCertificate(party) } // Often not needed.

    /** Returns a [PartyAndReference] for this identity and the given reference. */
    fun ref(vararg bytes: Byte): PartyAndReference = party.ref(*bytes)

    fun getTestPartyAndCertificate(party: Party): PartyAndCertificate {
        val trustRoot: X509Certificate = DEV_ROOT_CA.certificate
        val intermediate: CertificateAndKeyPair = DEV_INTERMEDIATE_CA

        val (nodeCaCert, nodeCaKeyPair) = createDevNodeCa(intermediate, party.name)

        val identityCert = X509Utilities.createCertificate(
                CertificateType.LEGAL_IDENTITY,
                nodeCaCert,
                nodeCaKeyPair,
                party.name.x500Principal,
                party.owningKey)

        val certPath = X509Utilities.buildCertPath(identityCert, nodeCaCert, intermediate.certificate, trustRoot)
        return PartyAndCertificate(certPath)
    }
}