package net.corda.tools.shell;

import org.crsh.cli.Command;
import org.crsh.cli.Man;

import static net.corda.tools.shell.InteractiveShell.*;

public class CheckpointShellCommand extends CordaRpcOpsShellCommand {

    @Command
    @Man("Outputs the contents of all checkpoints as json to be manually reviewed")
    @SuppressWarnings("unused")
    public void dump() {
        runDumpCheckpoints(ops());
    }
}
