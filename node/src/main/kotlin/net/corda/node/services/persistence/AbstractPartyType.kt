package net.corda.node.services.persistence

import net.corda.core.identity.AbstractParty
import net.corda.core.node.services.IdentityService
import org.hibernate.type.AbstractSingleColumnStandardBasicType
import org.hibernate.type.descriptor.sql.VarcharTypeDescriptor

class AbstractPartyType(identitySvc: () -> IdentityService)
    : AbstractSingleColumnStandardBasicType<AbstractParty>(VarcharTypeDescriptor.INSTANCE, AbstractPartyDescriptor(identitySvc)) {
    override fun getName(): String = "party"
}