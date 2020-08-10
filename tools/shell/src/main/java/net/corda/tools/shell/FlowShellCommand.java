package net.corda.tools.shell;

// See the comments at the top of run.java

import com.fasterxml.jackson.databind.ObjectMapper;
import net.corda.core.messaging.CordaRPCOps;
import net.corda.tools.shell.utlities.ANSIProgressRenderer;
import net.corda.tools.shell.utlities.CRaSHANSIProgressRenderer;
import org.crsh.cli.*;
import org.crsh.command.*;
import org.crsh.text.*;
import org.crsh.text.ui.TableElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static net.corda.tools.shell.InteractiveShell.killFlowById;
import static net.corda.tools.shell.InteractiveShell.runFlowByNameFragment;
import static net.corda.tools.shell.InteractiveShell.runStateMachinesView;

@Man(
        "Allows you to start and kill flows, list the ones available and to watch flows currently running on the node.\n\n" +
                "Starting flow is the primary way in which you command the node to change the ledger.\n\n" +
                "This command is generic, so the right way to use it depends on the flow you wish to start. You can use the 'flow start'\n" +
                "command with either a full class name, or a substring of the class name that's unambiguous. The parameters to the \n" +
                "flow constructors (the right one is picked automatically) are then specified using the same syntax as for the run command."
)
@Named("flow")
public class FlowShellCommand extends CordaRpcOpsShellCommand {

    private static final Logger logger = LoggerFactory.getLogger(FlowShellCommand.class);

    @Command
    @Usage("Start a (work)flow on the node. This is how you can change the ledger.\n\n" +
            "\t\t    Starting flow is the primary way in which you command the node to change the ledger.\n" +
            "\t\t    This command is generic, so the right way to use it depends on the flow you wish to start. You can use the 'flow start'\n" +
            "\t\t    command with either a full class name, or a substring of the class name that's unambiguous. The parameters to the\n" +
            "\t\t    flow constructors (the right one is picked automatically) are then specified using the same syntax as for the run command.\n")
    public void start(
            @Usage("The class name of the flow to run, or an unambiguous substring") @Argument String name,
            @Usage("The data to pass as input") @Argument(unquote = false) List<String> input
    ) {
        logger.info("Executing command \"flow start {} {}\",", name, (input != null) ? String.join(" ", input) : "<no arguments>");
        startFlow(name, input, out, ops(), ansiProgressRenderer(), objectMapper(null));
    }

    // TODO Limit number of flows shown option?
    @Command
    @Usage("Watch information about state machines running on the node with result information.")
    public void watch(InvocationContext<TableElement> context) throws Exception {
        logger.info("Executing command \"flow watch\".");
        runStateMachinesView(out, ops());
    }

    static void startFlow(@Usage("The class name of the flow to run, or an unambiguous substring") @Argument String name,
                          @Usage("The data to pass as input") @Argument(unquote = false) List<String> input,
                          RenderPrintWriter out,
                          CordaRPCOps rpcOps,
                          ANSIProgressRenderer ansiProgressRenderer,
                          ObjectMapper om) {
        if (name == null) {
            out.println("You must pass a name for the flow. Example: \"start Yo target: Some other company\"", Decoration.bold, Color.red);
            return;
        }
        String inp = input == null ? "" : String.join(" ", input).trim();
        runFlowByNameFragment(name, inp, out, rpcOps, ansiProgressRenderer != null ? ansiProgressRenderer : new CRaSHANSIProgressRenderer(out), om);
    }

    @Command
    @Usage("List flows that user can start.")
    public void list(InvocationContext<String> context) throws Exception {
        logger.info("Executing command \"flow list\".");
        for (String name : ops().registeredFlows()) {
            context.provide(name + System.lineSeparator());
        }
    }

    @Command
    @Usage("Kill a flow that is running on this node.")
    public void kill(
            @Usage("The UUID for the flow that we wish to kill") @Argument String id
    ) {
        logger.info("Executing command \"flow kill {}\".", id);
        killFlowById(id, out, ops(), objectMapper(null));
    }
}