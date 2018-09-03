package net.corda.sample.businessnetwork.membership.internal

import com.opencsv.CSVReaderBuilder
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.node.services.NetworkMapCache
import net.corda.sample.businessnetwork.membership.flow.MembershipList
import java.io.InputStream

/**
 * Implementation of a MembershipList that reads the content from CSV file.
 */
class CsvMembershipList(private val inputStream: InputStream, private val networkMapCache: NetworkMapCache) : MembershipList {

    private val allParties by lazy {
        fun lookUpParty(name: CordaX500Name): AbstractParty? = networkMapCache.getPeerByLegalName(name)

        inputStream.use {
            val reader = CSVReaderBuilder(it.reader()).withSkipLines(1).build()
            reader.use {
                val linesRead = reader.readAll()
                val commentsRemoved = linesRead.filterNot { line -> line.isEmpty() || line[0].startsWith("#") }
                val partiesList = commentsRemoved.mapNotNull { line -> lookUpParty(CordaX500Name.parse(line[0])) }
                partiesList.toSet()
            }
        }
    }

    override fun content(): Set<AbstractParty>  = allParties
}