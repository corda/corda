package net.corda.errorUtilities.docsTable

import net.corda.common.logging.errorReporting.ErrorResource
import net.corda.errorUtilities.ErrorResourceUtilities
import java.lang.IllegalArgumentException
import java.nio.file.Path
import java.util.*

/**
 * Generate the documentation table given a resource file location set.
 */
class DocsTableGenerator(private val resourceLocation: Path,
                         private val locale: Locale) {

    companion object {
        private const val ERROR_CODE_HEADING = "codeHeading"
        private const val ALIASES_HEADING = "aliasesHeading"
        private const val DESCRIPTION_HEADING = "descriptionHeading"
        private const val TO_FIX_HEADING = "toFixHeading"
        private const val ERROR_HEADINGS_BUNDLE = "ErrorPageHeadings"
    }

    private fun getHeading(heading: String) : String {
        val resource = ResourceBundle.getBundle(ERROR_HEADINGS_BUNDLE, locale)
        return resource.getString(heading)
    }

    private fun generateTable() : List<List<String>> {
        val table = mutableListOf<List<String>>()
        val loader = ErrorResourceUtilities.loaderFromDirectory(resourceLocation)
        for (resource in ErrorResourceUtilities.listResourceNames(resourceLocation)) {
            val errorResource = ErrorResource.fromLoader(resource, loader, locale)
            table.add(listOf(resource, errorResource.aliases, errorResource.shortDescription, errorResource.actionsToFix))
        }
        return table
    }

    private fun formatTable(tableData: List<List<String>>) : String {
        val headings = listOf(
                getHeading(ERROR_CODE_HEADING),
                getHeading(ALIASES_HEADING),
                getHeading(DESCRIPTION_HEADING),
                getHeading(TO_FIX_HEADING)
        )
        val underlines = headings.map { "-".repeat(it.length) }
        val fullTable = listOf(headings, underlines) + tableData
        return fullTable.joinToString(System.lineSeparator()) { it.joinToString(prefix = "| ", postfix = " |", separator = " | ") }
    }

    fun generateMarkdown() : String {
        if (!resourceLocation.toFile().exists()) throw IllegalArgumentException("Directory $resourceLocation does not exist.")
        val tableData = generateTable()
        return formatTable(tableData)
    }
}