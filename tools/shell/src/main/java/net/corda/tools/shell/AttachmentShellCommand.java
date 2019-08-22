package net.corda.tools.shell;

import org.crsh.cli.Command;
import org.crsh.cli.Man;

import static net.corda.tools.shell.InteractiveShell.runAttachmentTrustRootsView;

public class AttachmentShellCommand extends InteractiveShellCommand {

    @Command
    @Man("Displays the trusted CorDapp attachments that have been manually installed or received over the network")
    public void trustRoots() {
        runAttachmentTrustRootsView(out, ops());
    }
}
