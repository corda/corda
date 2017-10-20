package com.r3.corda.networkmanage.common.persistence

import net.corda.core.contracts.ContractState
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.node.services.api.SchemaService

class SchemaService : SchemaService {
    // Entities for compulsory services
    object SchemaServices

    object DoormanServicesV1 : MappedSchema(schemaFamily = SchemaServices.javaClass, version = 1,
            mappedTypes = listOf(CertificateSigningRequest::class.java, NodeInfoEntity::class.java, PublicKeyNodeInfoLink::class.java))

    override var schemaOptions: Map<MappedSchema, SchemaService.SchemaOptions> = mapOf(Pair(DoormanServicesV1, SchemaService.SchemaOptions()))

    override fun selectSchemas(state: ContractState): Iterable<MappedSchema> = setOf(DoormanServicesV1)

    override fun generateMappedObject(state: ContractState, schema: MappedSchema): PersistentState = throw UnsupportedOperationException()

}