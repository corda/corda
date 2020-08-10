package net.corda.tools.shell;

import net.corda.core.internal.messaging.AttachmentTrustInfoRPCOps;
import org.crsh.cli.Command;
import org.crsh.cli.Man;
import org.crsh.cli.Named;
import org.crsh.cli.Usage;
import org.jetbrains.annotations.NotNull;

import static net.corda.tools.shell.InteractiveShell.runAttachmentTrustInfoView;

@Named("attachments")
public class AttachmentShellCommand extends InteractiveShellCommand<AttachmentTrustInfoRPCOps> {

    @NotNull
    @Override
    public Class<AttachmentTrustInfoRPCOps> getRpcOpsClass()  {
        return AttachmentTrustInfoRPCOps.class;
    }

    @Command
    @Man("Displays the trusted CorDapp attachments that have been manually installed or received over the network")
    @Usage("Displays the trusted CorDapp attachments that have been manually installed or received over the network")
    public void trustInfo() {
        runAttachmentTrustInfoView(out, ops());
    }
}
