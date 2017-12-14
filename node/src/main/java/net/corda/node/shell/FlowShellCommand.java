package net.corda.node.shell;

// See the comments at the top of run.java

import net.corda.core.messaging.CordaRPCOps;
import net.corda.node.utilities.ANSIProgressRenderer;
import net.corda.node.utilities.CRaSHANSIProgressRenderer;
import org.crsh.cli.*;
import org.crsh.command.*;
import org.crsh.text.*;
import org.crsh.text.ui.TableElement;

import java.util.*;

import static net.corda.node.shell.InteractiveShell.*;

@Man(
        "Allows you to start flows, list the ones available and to watch flows currently running on the node.\n\n" +
                "Starting flow is the primary way in which you command the node to change the ledger.\n\n" +
                "This command is generic, so the right way to use it depends on the flow you wish to start. You can use the 'flow start'\n" +
                "command with either a full class name, or a substring of the class name that's unambiguous. The parameters to the \n" +
                "flow constructors (the right one is picked automatically) are then specified using the same syntax as for the run command."
)
public class FlowShellCommand extends InteractiveShellCommand {
    @Command
    @Usage("Start a (work)flow on the node. This is how you can change the ledger.")
    public void start(
            @Usage("The class name of the flow to run, or an unambiguous substring") @Argument String name,
            @Usage("The data to pass as input") @Argument(unquote = false) List<String> input
    ) {
        startFlow(name, input, out, ops(), ansiProgressRenderer());
    }

    // TODO Limit number of flows shown option?
    @Command
    @Usage("watch information about state machines running on the node with result information")
    public void watch(InvocationContext<TableElement> context) throws Exception {
        runStateMachinesView(out, ops());
    }

    static void startFlow(@Usage("The class name of the flow to run, or an unambiguous substring") @Argument String name,
                          @Usage("The data to pass as input") @Argument(unquote = false) List<String> input,
                          RenderPrintWriter out,
                          CordaRPCOps rpcOps,
                          ANSIProgressRenderer ansiProgressRenderer) {
        if (name == null) {
            out.println("You must pass a name for the flow, see 'man flow'", Color.red);
            return;
        }
        String inp = input == null ? "" : String.join(" ", input).trim();
        runFlowByNameFragment(name, inp, out, rpcOps, ansiProgressRenderer != null ? ansiProgressRenderer : new CRaSHANSIProgressRenderer(out) );
    }

    @Command
    @Usage("list flows that user can start")
    public void list(InvocationContext<String> context) throws Exception {
        for (String name : ops().registeredFlows()) {
            context.provide(name + System.lineSeparator());
        }
    }
}