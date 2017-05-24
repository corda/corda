package net.corda.node.shell;

import org.crsh.cli.Command;
import org.crsh.cli.Man;
import org.crsh.cli.Usage;
import org.crsh.command.InvocationContext;
import org.crsh.text.ui.TableElement;

@Man(
        "Allows you to see all state machines activity run on the node.\n\n" +
                "Notice that flow information isn't currently persisted, so every rerun of the command will start from fresh."
)
@Usage("show information about state machines running on the node with result information")
public class StateMachinesCommand extends InteractiveShellCommand{

    //TODO Limit number of flows shown option?
    @Command
    public Object main(InvocationContext<TableElement> context) {
        return InteractiveShell.runStateMachinesView(out, context);
    }
}
