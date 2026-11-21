package net.corda.coretests.transactions.javaxfixture

import jakarta.persistence.AttributeConverter
import jakarta.persistence.CascadeType
import jakarta.persistence.PersistenceException
import net.corda.core.contracts.Contract
import net.corda.core.transactions.LedgerTransaction
import java.util.concurrent.Callable

// Classes that AttachmentsClassLoaderJavaxPersistenceTests puts into attachments, moved to another package (so that only the attachment
// has them) and, apart from BothApisFixture, with jakarta.persistence renamed to javax.persistence: what a contract jar built for
// Corda 4.14 or earlier has when its classes use the persistence API in code and not in annotations alone.

/** Has a `javax.persistence` interface as a supertype. */
class ConverterFixture : AttributeConverter<String, String> {
    override fun convertToDatabaseColumn(attribute: String?): String? = attribute?.uppercase()
    override fun convertToEntityAttribute(dbData: String?): String? = dbData?.lowercase()
}

/** Names `javax.persistence` types in its code and in the signature of a member. */
class CodeFixture : Callable<String> {
    fun cascade(): CascadeType = CascadeType.ALL

    override fun call(): String {
        return try {
            ConverterFixture().convertToDatabaseColumn("converted") + cascade().name
        } catch (e: PersistenceException) {
            "failed"
        }
    }
}

/** Does not use the persistence API: defined by the class loader as before, in the same package as the converted classes. */
class PlainFixture : Callable<String> {
    override fun call(): String = "plain"
}

/** Refers to both APIs (the other one in a string), as a class annotated for both does: left alone. */
class BothApisFixture : Callable<String> {
    fun cascade(): CascadeType = CascadeType.ALL

    override fun call(): String = "javax/persistence/Entity"
}

/** A contract whose verification reaches code that uses the persistence API. */
class ContractFixture : Contract {
    override fun verify(tx: LedgerTransaction) {
        require(CodeFixture().call() == "CONVERTEDALL") { "The persistence classes are usable in verification" }
    }
}
