package net.corda.signing.persistence

import net.corda.core.contracts.ContractState
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.node.services.api.SchemaService

class SigningServerSchemaService: SchemaService {
    // Entities for compulsory services
    object SigningServerServices

    object SigningServerServicesV1 : MappedSchema(schemaFamily = SigningServerServices.javaClass, version = 1,
            mappedTypes = listOf(DBCertificateRequestStorage.CertificateSigningRequest::class.java))

    override val schemaOptions: Map<MappedSchema, SchemaService.SchemaOptions> = mapOf(Pair(SigningServerServicesV1, SchemaService.SchemaOptions()))

    override fun selectSchemas(state: ContractState): Iterable<MappedSchema> = setOf(SigningServerServicesV1)

    override fun generateMappedObject(state: ContractState, schema: MappedSchema): PersistentState = throw UnsupportedOperationException()
}