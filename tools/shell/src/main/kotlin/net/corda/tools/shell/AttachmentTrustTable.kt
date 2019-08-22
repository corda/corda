package net.corda.tools.shell

import net.corda.core.internal.AttachmentTrustRoot
import net.corda.core.internal.P2P_UPLOADER
import org.crsh.text.Color
import org.crsh.text.Decoration
import org.crsh.text.RenderPrintWriter
import org.crsh.text.ui.LabelElement
import org.crsh.text.ui.Overflow
import org.crsh.text.ui.RowElement
import org.crsh.text.ui.TableElement

class AttachmentTrustTable(
    writer: RenderPrintWriter,
    private val attachmentTrustRoots: List<AttachmentTrustRoot>
) {

    private val content: TableElement

    init {
        content = createTable()
        createRows()
        writer.print(content)
    }

    private fun createTable(): TableElement {
        val table = TableElement(2, 3, 1, 1, 3).overflow(Overflow.WRAP).rightCellPadding(3)
        val header =
            RowElement(true).add("Name", "Hash/Id", "Installed", "Trusted", "Trust Root").style(
                Decoration.bold.fg(
                    Color.black
                ).bg(Color.white)
            )
        table.add(header)
        return table
    }

    private fun createRows() {
        for (attachmentTrustRoot in attachmentTrustRoots) {
            attachmentTrustRoot.run {
                val name = when {
                    fileName != null -> fileName!!
                    uploader?.startsWith(P2P_UPLOADER) ?: false -> {
                        "Received from: ${uploader!!.removePrefix("$P2P_UPLOADER:")}"
                    }
                    else -> ""
                }
                content.add(
                    RowElement().add(
                        LabelElement(name),
                        LabelElement(attachmentId),
                        LabelElement(isTrustRoot),
                        LabelElement(isTrusted),
                        LabelElement(trustRootFileName ?: trustRootId ?: "")
                    )
                )
            }
        }
    }
}