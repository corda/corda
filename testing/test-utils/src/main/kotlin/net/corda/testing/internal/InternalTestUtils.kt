package net.corda.testing.internal

import net.corda.core.context.AuthServiceId
import net.corda.core.contracts.Command
import net.corda.core.contracts.PrivacySalt
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TimeWindow
import net.corda.core.contracts.TransactionState
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.DigestService
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.internal.NamedCacheFactory
import net.corda.core.internal.cordapp.set
import net.corda.core.internal.createComponentGroups
import net.corda.core.node.NodeInfo
import net.corda.core.schemas.MappedSchema
import net.corda.core.serialization.internal.effectiveSerializationEnv
import net.corda.core.transactions.WireTransaction
import net.corda.core.utilities.loggerFor
import net.corda.coretesting.internal.asTestContextEnv
import net.corda.coretesting.internal.createTestSerializationEnv
import net.corda.coretesting.internal.stubs.CertificateStoreStubs
import net.corda.node.internal.checkOrUpdate
import net.corda.node.internal.createCordaPersistence
import net.corda.node.internal.security.RPCSecurityManagerImpl
import net.corda.node.internal.startHikariPool
import net.corda.node.services.api.SchemaService
import net.corda.node.services.config.SecurityConfiguration
import net.corda.node.services.schema.NodeSchemaService
import net.corda.nodeapi.BrokerRpcSslOptions
import net.corda.nodeapi.internal.config.MutualSslConfiguration
import net.corda.nodeapi.internal.config.User
import net.corda.nodeapi.internal.createDevNodeCa
import net.corda.nodeapi.internal.crypto.CertificateAndKeyPair
import net.corda.nodeapi.internal.crypto.CertificateType
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.nodeapi.internal.persistence.SchemaMigration
import net.corda.nodeapi.internal.protonwrapper.netty.CrlSource
import net.corda.nodeapi.internal.registerDevP2pCertificates
import net.corda.serialization.internal.amqp.AMQP_ENABLED
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.core.TestIdentity
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import java.security.KeyPair
import java.security.cert.X509CRL
import java.security.cert.X509Certificate
import java.util.Properties
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import java.util.zip.ZipEntry
import javax.security.auth.x500.X500Principal

@Suppress("unused")
inline fun <reified T : Any> T.kryoSpecific(reason: String, function: () -> Unit) = if (!AMQP_ENABLED) {
    function()
} else {
    loggerFor<T>().info("Ignoring Kryo specific test, reason: $reason")
}

@Suppress("unused")
inline fun <reified T : Any> T.amqpSpecific(reason: String, function: () -> Unit) = if (AMQP_ENABLED) {
    function()
} else {
    loggerFor<T>().info("Ignoring AMQP specific test, reason: $reason")
}

private val defaultRootCaName = X500Principal("CN=Corda Root CA,O=R3 Ltd,L=London,C=GB")
private val defaultIntermediateCaName = X500Principal("CN=Corda Intermediate CA,O=R3 Ltd,L=London,C=GB")

/**
 * Returns a pair of [CertificateAndKeyPair]s, the first being the root CA and the second the intermediate CA.
 * @param rootCaName The subject name for the root CA cert.
 * @param intermediateCaName The subject name for the intermediate CA cert.
 */
fun createDevIntermediateCaCertPath(
        rootCaName: X500Principal = defaultRootCaName,
        intermediateCaName: X500Principal = defaultIntermediateCaName
): Pair<CertificateAndKeyPair, CertificateAndKeyPair> {
    val rootKeyPair = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
    val rootCert = X509Utilities.createSelfSignedCACertificate(rootCaName, rootKeyPair)

    val intermediateCaKeyPair = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
    val intermediateCaCert = X509Utilities.createCertificate(
            CertificateType.INTERMEDIATE_CA,
            rootCert,
            rootKeyPair,
            intermediateCaName,
            intermediateCaKeyPair.public)

    return Pair(
            CertificateAndKeyPair(rootCert, rootKeyPair),
            CertificateAndKeyPair(intermediateCaCert, intermediateCaKeyPair)
    )
}

/**
 * Returns a triple of [CertificateAndKeyPair]s, the first being the root CA, the second the intermediate CA and the third
 * the node CA.
 * @param legalName The subject name for the node CA cert.
 */
fun createDevNodeCaCertPath(
        legalName: CordaX500Name,
        nodeKeyPair: KeyPair = Crypto.generateKeyPair(X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME),
        rootCaName: X500Principal = defaultRootCaName,
        intermediateCaName: X500Principal = defaultIntermediateCaName
): Triple<CertificateAndKeyPair, CertificateAndKeyPair, CertificateAndKeyPair> {
    val (rootCa, intermediateCa) = createDevIntermediateCaCertPath(rootCaName, intermediateCaName)
    val nodeCa = createDevNodeCa(intermediateCa, legalName, nodeKeyPair)
    return Triple(rootCa, intermediateCa, nodeCa)
}

fun BrokerRpcSslOptions.useSslRpcOverrides(): Map<String, String> {
    return mapOf(
            "rpcSettings.useSsl" to "true",
            "rpcSettings.ssl.keyStorePath" to keyStorePath.toAbsolutePath().toString(),
            "rpcSettings.ssl.keyStorePassword" to keyStorePassword
    )
}

/**
 * Until we have proper handling of multiple identities per node, for tests we use the first identity as special one.
 * TODO: Should be removed after multiple identities are introduced.
 */
fun NodeInfo.chooseIdentityAndCert(): PartyAndCertificate = legalIdentitiesAndCerts.first()

/**
 * Returns the party identity of the first identity on the node. Until we have proper handling of multiple identities per node,
 * for tests we use the first identity as special one.
 * TODO: Should be removed after multiple identities are introduced.
 */
fun NodeInfo.chooseIdentity(): Party = chooseIdentityAndCert().party

fun p2pSslOptions(path: Path, name: CordaX500Name = CordaX500Name("MegaCorp", "London", "GB")): MutualSslConfiguration {
    val sslConfig = CertificateStoreStubs.P2P.withCertificatesDirectory(path, keyStorePassword = "serverstorepass")
    val (rootCa, intermediateCa) = createDevIntermediateCaCertPath()
    sslConfig.keyStore.get(true).registerDevP2pCertificates(name, rootCa.certificate, intermediateCa)
    val trustStore = sslConfig.trustStore.get(true)
    trustStore[X509Utilities.CORDA_ROOT_CA] = rootCa.certificate
    return sslConfig
}

fun fixedCrlSource(crls: Set<X509CRL>): CrlSource {
    return object : CrlSource {
        override fun fetch(certificate: X509Certificate): Set<X509CRL> = crls
    }
}

@SuppressWarnings("LongParameterList")
fun createWireTransaction(inputs: List<StateRef>,
                          attachments: List<SecureHash>,
                          outputs: List<TransactionState<*>>,
                          commands: List<Command<*>>,
                          notary: Party?,
                          timeWindow: TimeWindow?,
                          legacyAttachments: List<SecureHash> = emptyList(),
                          privacySalt: PrivacySalt = PrivacySalt(),
                          digestService: DigestService = DigestService.default): WireTransaction {
    val componentGroups = createComponentGroups(inputs, outputs, commands, attachments, notary, timeWindow, emptyList(), null, legacyAttachments)
    return WireTransaction(componentGroups, privacySalt, digestService)
}

/**
 * Instantiate RPCSecurityManager initialised with users data from a list of [User]
 */
fun RPCSecurityManagerImpl.Companion.fromUserList(id: AuthServiceId, users: List<User>) =
        RPCSecurityManagerImpl(SecurityConfiguration.AuthService.fromUsers(users).copy(id = id), TestingNamedCacheFactory())

/**
 * Convenience method for configuring a database for some tests.
 */
@Suppress("LongParameterList")
fun configureDatabase(hikariProperties: Properties,
                      databaseConfig: DatabaseConfig,
                      wellKnownPartyFromX500Name: (CordaX500Name) -> Party?,
                      wellKnownPartyFromAnonymous: (AbstractParty) -> Party?,
                      schemaService: SchemaService = NodeSchemaService(),
                      internalSchemas: Set<MappedSchema> = NodeSchemaService().internalSchemas,
                      cacheFactory: NamedCacheFactory = TestingNamedCacheFactory(),
                      ourName: CordaX500Name = TestIdentity(ALICE_NAME, 70).name,
                      runMigrationScripts: Boolean = true,
                      allowHibernateToManageAppSchema: Boolean = true): CordaPersistence {
    val persistence = createCordaPersistence(
            databaseConfig,
            wellKnownPartyFromX500Name,
            wellKnownPartyFromAnonymous,
            schemaService,
            hikariProperties,
            cacheFactory,
            null,
            allowHibernateToManageAppSchema)
    persistence.startHikariPool(hikariProperties) { dataSource, haveCheckpoints ->
        SchemaMigration(dataSource, null, null, ourName)
                .checkOrUpdate(internalSchemas, runMigrationScripts, haveCheckpoints, false)
    }
    return persistence
}

/**
 * Convenience method for creating a fake attachment containing a file with some content.
 */
fun fakeAttachment(filePath: String, content: String, manifestAttributes: Map<String, String> = emptyMap()): ByteArray {
    val bs = ByteArrayOutputStream()
    val manifest = Manifest()
    manifestAttributes.forEach { manifest[it.key] = it.value } //adding manually instead of putAll, as it requires typed keys, not strings
    JarOutputStream(bs, manifest).use { js ->
        js.putNextEntry(ZipEntry(filePath))
        js.writer().apply { append(content); flush() }
        js.closeEntry()
    }
    return bs.toByteArray()
}

fun fakeAttachment(filePath1: String, content1: String, filePath2: String, content2: String, manifestAttributes: Map<String, String> = emptyMap()): ByteArray {
    val bs = ByteArrayOutputStream()
    val manifest = Manifest()
    manifestAttributes.forEach { manifest[it.key] = it.value } //adding manually instead of putAll, as it requires typed keys, not strings
    JarOutputStream(bs, manifest).use { js ->
        js.putNextEntry(ZipEntry(filePath1))
        js.writer().apply { append(content1); flush() }
        js.closeEntry()
        js.putNextEntry(ZipEntry(filePath2))
        js.writer().apply { append(content2); flush() }
        js.closeEntry()
    }
    return bs.toByteArray()
}

/** If [effectiveSerializationEnv] is not set, runs the block with a new [SerializationEnvironmentRule]. */
fun <R> withTestSerializationEnvIfNotSet(block: () -> R): R {
    val serializationExists = try {
        effectiveSerializationEnv
        true
    } catch (e: IllegalStateException) {
        false
    }
    return if (serializationExists) {
        block()
    } else {
        createTestSerializationEnv().asTestContextEnv { block() }
    }
}

@JvmField
val IS_S390X = System.getProperty("os.arch") == "s390x"
