package net.corda.tools.shell;

import net.corda.client.rpc.proxy.NodeHealthCheckRpcOps;
import org.crsh.cli.Command;
import org.crsh.cli.Man;
import org.jetbrains.annotations.NotNull;

public class HealthCheckShellCommand extends InteractiveShellCommand<NodeHealthCheckRpcOps> {

    @NotNull
    @Override
    public Class<NodeHealthCheckRpcOps> getRpcOpsClass()  {
        return NodeHealthCheckRpcOps.class;
    }

    @Command
    @Man("Outputs runtime info of the running node")
    @SuppressWarnings("unused")
    public String runtimeInfo() {
        return ops().runtimeInfo();
    }
}
