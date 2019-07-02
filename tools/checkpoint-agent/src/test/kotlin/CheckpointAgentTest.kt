import net.corda.tools.CheckpointAgent
import org.junit.Test

class CheckpointAgentTest {

    @Test
    fun argumentParsing() {
        resetDefaults()
        CheckpointAgent.parseArguments("instrumentClassname=net.corda.vega.flows.SimmFlow, minimumSize=1000000, instrumentType=READ,fiberName=c1694658-5df5-4267-abe7-cd8708da682a")
        resetDefaults()
        CheckpointAgent.parseArguments("instrumentClassname=net.corda.vega.flows.SimmFlow, minimumSize=1000000, instrumentType=READ")
        resetDefaults()
        CheckpointAgent.parseArguments("instrumentClassname=net.corda.vega.flows.SimmFlow, minimumSize=1000000")
        resetDefaults()
        CheckpointAgent.parseArguments("instrumentClassname=net.corda.vega.flows.SimmFlow")
        resetDefaults()
        CheckpointAgent.parseArguments("minimumSize=1000000, instrumentType=READ, instrumentClassname=net.corda.vega.flows.SimmFlow")
        resetDefaults()
        CheckpointAgent.parseArguments("minimumSize=1000000,instrumentClassname=net.corda.vega.flows.SimmFlow")
        resetDefaults()
        CheckpointAgent.parseArguments("minimumSize=1000000")
        resetDefaults()
        CheckpointAgent.parseArguments("minimumSize=1000000, instrumentType=READ")
        resetDefaults()
        CheckpointAgent.parseArguments("instrumentType=READ")
        resetDefaults()
        CheckpointAgent.parseArguments("instrumentClassname,minimumSize=1000000")
        resetDefaults()
        CheckpointAgent.parseArguments("minimumSize=abc")
        resetDefaults()
        CheckpointAgent.parseArguments("instrumentType=WRONG")
        resetDefaults()
        CheckpointAgent.parseArguments("instrumentType=read,fiberName=c1694658-5df5-4267-abe7-cd8708da682a")
        resetDefaults()
        CheckpointAgent.parseArguments("blablabla=net.corda.vega.flows.SimmFlow, blablabla=1000000, instrumentType=WRITE")
    }

    private fun resetDefaults() {
        CheckpointAgent.instrumentClassname = CheckpointAgent.DEFAULT_INSTRUMENT_CLASSNAME
        CheckpointAgent.minimumSize = CheckpointAgent.DEFAULT_MINIMUM_SIZE
        CheckpointAgent.maximumSize = CheckpointAgent.DEFAULT_MAXIMUM_SIZE
        CheckpointAgent.instrumentType = CheckpointAgent.DEFAULT_INSTRUMENT_TYPE
        CheckpointAgent.fiberName = null
    }
}