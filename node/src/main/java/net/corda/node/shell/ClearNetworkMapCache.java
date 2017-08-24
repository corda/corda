package net.corda.node.shell;

import org.crsh.cli.Command;
import org.crsh.cli.Man;

import static net.corda.node.shell.InteractiveShell.clearNetworkMapCache;

public class ClearNetworkMapCache extends InteractiveShellCommand {
    @Command
    @Man("Clear all network map data from local node cache.")
    public void main() {
        clearNetworkMapCache();
    }
}
