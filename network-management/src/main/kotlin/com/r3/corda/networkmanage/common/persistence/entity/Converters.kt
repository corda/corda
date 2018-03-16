package com.r3.corda.networkmanage.common.persistence.entity

import net.corda.core.identity.CordaX500Name
import javax.persistence.AttributeConverter

class CordaX500NameAttributeConverter : AttributeConverter<CordaX500Name, String> {
    override fun convertToDatabaseColumn(attribute: CordaX500Name?): String? = attribute?.toString()
    override fun convertToEntityAttribute(dbData: String?): CordaX500Name? = dbData?.let { CordaX500Name.parse(it) }
}

// TODO Use SecureHash in entities
//class SecureHashAttributeConverter : AttributeConverter<SecureHash, String> {
//    override fun convertToDatabaseColumn(attribute: SecureHash?): String? = attribute?.toString()
//    override fun convertToEntityAttribute(dbData: String?): SecureHash? = dbData?.let { SecureHash.parse(it) }
//}
