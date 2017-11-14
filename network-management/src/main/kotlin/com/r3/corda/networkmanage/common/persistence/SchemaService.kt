package com.r3.corda.networkmanage.common.persistence

import com.r3.corda.networkmanage.common.persistence.entity.*
import net.corda.core.contracts.ContractState
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.node.services.api.SchemaService

class SchemaService : SchemaService {
    // Entities for compulsory services
    object SchemaServices

    object NetworkServicesV1 : MappedSchema(schemaFamily = SchemaServices.javaClass, version = 1,
            mappedTypes = listOf(
                    CertificateSigningRequestEntity::class.java,
                    CertificateDataEntity::class.java,
                    NodeInfoEntity::class.java,
                    NetworkParametersEntity::class.java,
                    NetworkMapEntity::class.java))

    override var schemaOptions: Map<MappedSchema, SchemaService.SchemaOptions> = mapOf(Pair(NetworkServicesV1, SchemaService.SchemaOptions()))

    override fun selectSchemas(state: ContractState): Iterable<MappedSchema> = setOf(NetworkServicesV1)

    override fun generateMappedObject(state: ContractState, schema: MappedSchema): PersistentState = throw UnsupportedOperationException()

}