package net.corda.node.shell.standalone;

// A simple forwarder to the "flow start" command, for easier typing.

import net.corda.shell.InteractiveShellCommand;
import net.corda.shell.utlities.ANSIProgressRenderer;
import net.corda.shell.utlities.CRaSHANSIProgressRenderer;
import org.crsh.cli.Argument;
import org.crsh.cli.Command;
import org.crsh.cli.Man;
import org.crsh.cli.Usage;

import java.util.List;

public class StartShellCommand extends InteractiveShellCommand {
    @Command
    @Man("An alias for 'flow start'. Example: \"start Yo target: Some other company\"")
    public void main(@Usage("The class name of the flow to run, or an unambiguous substring") @Argument String name,
                     @Usage("The data to pass as input") @Argument(unquote = false) List<String> input) {
        ANSIProgressRenderer ansiProgressRenderer = ansiProgressRenderer();
        FlowShellCommand.startFlow(name, input, out, ops(), ansiProgressRenderer != null ? ansiProgressRenderer : new CRaSHANSIProgressRenderer(out), objectMapper());
    }
}
