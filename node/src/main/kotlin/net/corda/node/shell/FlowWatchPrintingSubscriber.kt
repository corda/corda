package net.corda.node.shell

import com.google.common.util.concurrent.SettableFuture
import net.corda.core.ErrorOr
import net.corda.core.crypto.commonName
import net.corda.core.flows.FlowInitiator
import net.corda.core.flows.StateMachineRunId
import net.corda.core.messaging.StateMachineUpdate
import net.corda.core.then
import net.corda.core.transactions.SignedTransaction
import org.crsh.text.Color
import org.crsh.text.Decoration
import org.crsh.text.RenderPrintWriter
import org.crsh.text.ui.LabelElement
import org.crsh.text.ui.TableElement
import org.crsh.text.ui.Overflow
import org.crsh.text.ui.RowElement
import rx.Subscriber

class FlowWatchPrintingSubscriber(private val toStream: RenderPrintWriter) : Subscriber<Any>() {
    private val indexMap = HashMap<StateMachineRunId, Int>()
    private val table = createStateMachinesTable()
    val future: SettableFuture<Unit> = SettableFuture.create()

    init {
        // The future is public and can be completed by something else to indicate we don't wish to follow
        // anymore (e.g. the user pressing Ctrl-C).
        future.then { unsubscribe() }
    }

    @Synchronized
    override fun onCompleted() {
        // The observable of state machines will never complete.
        future.set(Unit)
    }

    @Synchronized
    override fun onNext(t: Any?) {
        if (t is StateMachineUpdate) {
            toStream.cls()
            createStateMachinesRow(t)
            toStream.print(table)
            toStream.println("Waiting for completion or Ctrl-C ... ")
            toStream.flush()
        }
    }

    @Synchronized
    override fun onError(e: Throwable) {
        toStream.println("Observable completed with an error")
        future.setException(e)
    }

    private fun stateColor(smmUpdate: StateMachineUpdate): Color {
        return when(smmUpdate){
            is StateMachineUpdate.Added -> Color.blue
            is StateMachineUpdate.Removed -> smmUpdate.result.match({ Color.green } , { Color.red })
        }
    }

    private fun createStateMachinesTable(): TableElement {
        val table = TableElement(1,2,1,2).overflow(Overflow.HIDDEN).rightCellPadding(1)
        val header = RowElement(true).add("Id", "Flow name", "Initiator", "Status").style(Decoration.bold.fg(Color.black).bg(Color.white))
        table.add(header)
        return table
    }

    // TODO Add progress tracker?
    private fun createStateMachinesRow(smmUpdate: StateMachineUpdate) {
        when (smmUpdate) {
            is StateMachineUpdate.Added -> {
                table.add(RowElement().add(
                        LabelElement(formatFlowId(smmUpdate.id)),
                        LabelElement(formatFlowName(smmUpdate.stateMachineInfo.flowLogicClassName)),
                        LabelElement(formatFlowInitiator(smmUpdate.stateMachineInfo.initiator)),
                        LabelElement("In progress")
                ).style(stateColor(smmUpdate).fg()))
                indexMap[smmUpdate.id] = table.rows.size - 1
            }
            is StateMachineUpdate.Removed -> {
                val idx = indexMap[smmUpdate.id]
                if (idx != null) {
                    val oldRow = table.rows[idx]
                    val flowNameLabel = oldRow.getCol(1) as LabelElement
                    val flowInitiatorLabel = oldRow.getCol(2) as LabelElement
                    table.rows[idx] = RowElement().add(
                            LabelElement(formatFlowId(smmUpdate.id)),
                            LabelElement(flowNameLabel.value),
                            LabelElement(flowInitiatorLabel.value),
                            LabelElement(formatFlowResult(smmUpdate.result))
                    ).style(stateColor(smmUpdate).fg())
                }
            }
        }
    }

    private fun formatFlowName(flowName: String): String {
        val camelCaseRegex = Regex("(?<=[a-z])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])")
        val name = flowName.split('.', '$').last()
        // Split CamelCase and get rid of "flow" at the end if present.
        return camelCaseRegex.split(name).filter { it.compareTo("Flow", true) != 0 }.joinToString(" ")
    }

    private fun formatFlowId(flowId: StateMachineRunId): String {
        return flowId.toString().removeSurrounding("[","]")
    }

    private fun formatFlowInitiator(flowInitiator: FlowInitiator): String {
        return when (flowInitiator) {
            is FlowInitiator.Scheduled ->  flowInitiator.scheduledState.ref.toString()
            is FlowInitiator.Shell -> "Shell" // TODO Change when we will have more information on shell user.
            is FlowInitiator.Peer -> flowInitiator.party.name.commonName
            is FlowInitiator.RPC -> "RPC: " + flowInitiator.username
        }
    }

    private fun formatFlowResult(flowResult: ErrorOr<*>): String {
        fun successFormat(value: Any?): String {
            return when(value) {
                is SignedTransaction -> "Tx ID: " + value.id.toString()
                is kotlin.Unit -> "No return value"
                null -> "No return value"
                else -> value.toString()
            }
        }
        return flowResult.match({ successFormat(it) }, { it.message ?: it.toString() })
    }
}
