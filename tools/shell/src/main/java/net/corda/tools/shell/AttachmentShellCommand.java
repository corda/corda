package net.corda.tools.shell;

import org.crsh.cli.Command;
import org.crsh.cli.Man;
import org.crsh.cli.Named;
import org.crsh.cli.Usage;

import static net.corda.tools.shell.InteractiveShell.runAttachmentTrustInfoView;

@Named("attachments")
public class AttachmentShellCommand extends InteractiveShellCommand {

    @Command
    @Man("Displays the trusted CorDapp attachments that have been manually installed or received over the network")
    @Usage("Displays the trusted CorDapp attachments that have been manually installed or received over the network")
    public void trustInfo() {
        runAttachmentTrustInfoView(out, ops());
    }
}
