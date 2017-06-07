package net.corda.node.shell;

// See the comments at the top of run.java

import org.crsh.cli.*;
import org.crsh.command.*;
import org.crsh.text.*;

import java.util.*;

import static net.corda.node.shell.InteractiveShell.*;

@Man(
    "Allows you to list and start flows. This is the primary way in which you command the node to change the ledger.\n\n" +
    "This command is generic, so the right way to use it depends on the flow you wish to start. You can use the 'flow start'\n" +
    "command with either a full class name, or a substring of the class name that's unambiguous. The parameters to the \n" +
    "flow constructors (the right one is picked automatically) are then specified using the same syntax as for the run command."
)
@Usage("Start a (work)flow on the node. This is how you can change the ledger.")
public class FlowShellCommand extends InteractiveShellCommand {
    @Command
    public void start(
            @Usage("The class name of the flow to run, or an unambiguous substring") @Argument String name,
            @Usage("The data to pass as input") @Argument(unquote = false) List<String> input
    ) {
        startFlow(name, input, out);
    }

    static void startFlow(@Usage("The class name of the flow to run, or an unambiguous substring") @Argument String name, @Usage("The data to pass as input") @Argument(unquote = false) List<String> input, RenderPrintWriter out) {
        if (name == null) {
            out.println("You must pass a name for the flow, see 'man flow'", Color.red);
            return;
        }
        String inp = input == null ? "" : String.join(" ", input).trim();
        runFlowByNameFragment(name, inp, out);
    }

    @Command
    public void list(InvocationContext<String> context) throws Exception {
        for (String name : ops().registeredFlows()) {
            context.provide(name + System.lineSeparator());
        }
    }
}